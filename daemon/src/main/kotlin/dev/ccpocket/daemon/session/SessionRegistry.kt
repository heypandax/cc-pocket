package dev.ccpocket.daemon.session

import dev.ccpocket.daemon.agent.AgentBackendFactory
import dev.ccpocket.daemon.conversation.Conversation
import dev.ccpocket.daemon.conversation.ObserveSession
import dev.ccpocket.daemon.conversation.OutboundSink
import dev.ccpocket.daemon.conversation.PushHook
import dev.ccpocket.daemon.disk.LiveProcesses
import dev.ccpocket.daemon.disk.ProjectPaths
import dev.ccpocket.daemon.disk.TranscriptScanner
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.AgentModel
import dev.ccpocket.protocol.AuthBlockReason
import dev.ccpocket.protocol.AuthBlocker
import dev.ccpocket.protocol.CancelTurn
import dev.ccpocket.protocol.ClearAllowRule
import dev.ccpocket.protocol.OpenSession
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.PermissionVerdict
import dev.ccpocket.protocol.PocketError
import dev.ccpocket.protocol.SendPrompt
import dev.ccpocket.protocol.SessionSummary
import dev.ccpocket.protocol.StopBackgroundJob
import dev.ccpocket.protocol.SwitchDirectory
import dev.ccpocket.protocol.SwitchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

/** convoId -> live [Conversation]. Create on open (picking the backend by [OpenSession.agent]),
 *  relaunch on switch-dir, reap on close. */
class SessionRegistry(
    private val scope: CoroutineScope,
    private val backends: Map<AgentKind, AgentBackendFactory>,
    // "is a claude OUTSIDE the daemon alive on this workdir/transcript?" — injectable so the fork/observe
    // decision matrix is unit-testable; the real probe shells out to lsof (LiveProcesses.externalClaudeAt)
    private val processProbe: (workdir: String, transcript: Path) -> LiveProcesses.ExternalClaude =
        LiveProcesses::externalClaudeAt,
    // Claude transcript root — injectable ONLY so [renameSession]'s disk write is unit-testable against
    // a temp dir instead of the user's real ~/.claude/projects (every other path resolves via the
    // backends / ProjectPaths directly, same default)
    private val projectsRoot: Path = ProjectPaths.projectsRoot(),
    // Re-resolve a kind ABSENT from [backends] (its CLI wasn't installed when the daemon started) — a
    // hit is cached in [lateBackends], so installing a CLI works without a daemon restart. Default
    // null-resolver keeps the old fixed-map behavior (tests).
    private val reprobe: (AgentKind) -> AgentBackendFactory? = { null },
) {
    // kinds whose CLI appeared after startup, resolved on first use via [reprobe]
    private val lateBackends = java.util.concurrent.ConcurrentHashMap<AgentKind, AgentBackendFactory>()

    private fun backendFor(kind: AgentKind): AgentBackendFactory? =
        backends[kind] ?: lateBackends[kind] ?: reprobe(kind)?.also {
            lateBackends[kind] = it
            log.info("late-registered $kind backend (CLI installed after daemon startup)")
        }

    private fun allBackends(): List<AgentBackendFactory> = AgentKind.entries.mapNotNull { backendFor(it) }

    fun cursorModels(): List<AgentModel> = backendFor(AgentKind.CURSOR)?.create()?.availableModels().orEmpty()
    private val mutex = Mutex()
    private val log = dev.ccpocket.daemon.util.logger("SessionRegistry")
    private val convos = mutableMapOf<String, Conversation>()
    private val observes = mutableMapOf<String, ObserveSession>()
    private val pendingClose = mutableMapOf<String, Job>() // convoId -> delayed-close job during a LAN disconnect grace

    // live LAN sockets — the reaper must treat "a phone is attached over LAN" like relay peerOnline,
    // else a LAN session idle past the reap window is killed under the user's thumbs
    private val lanConnections = java.util.concurrent.atomic.AtomicInteger(0)

    fun onLanConnect() { lanConnections.incrementAndGet() }
    fun onLanDisconnect() { lanConnections.decrementAndGet() }
    fun lanConnected(): Boolean = lanConnections.get() > 0

    // sessions the DAEMON itself closed recently: sessionId -> closedAt (bounded LRU, guarded by [mutex]).
    // A close right after an assistant reply leaves a genuinely fresh transcript mtime; without this record,
    // re-entering within the 20s liveness window misreads our own last write as "an external claude is
    // writing" — bogus observe banner, and a take-over would fork a duplicate (issue #33 residual).
    private val selfClosed = object : LinkedHashMap<String, Long>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 64
    }

    /** When THIS daemon process booted — see the restart-amnesia guard in [externallyActive]. */
    private val startedAt = System.currentTimeMillis()

    /** True if [file]'s recent mtime is explained by an EXTERNAL writer — i.e. recently written AND not
     *  merely the tail of a session we closed ourselves (writes after our close mean a real foreign claude).
     *  mtime freshness is only the cheap GATE: a hit is confirmed by a real process probe (is a claude
     *  outside the daemon actually alive on [workdir]?), because a terminal claude the user just quit
     *  leaves a fresh mtime for up to 20s — trusting it blindly forked take-overs and demoted plain opens
     *  to read-only observe against a writer that no longer exists (the main "mystery fork" source).
     *  Internal (not private) so the decision matrix is unit-testable with a stubbed [processProbe]. */
    internal suspend fun externallyActive(sessionId: String, workdir: String, file: Path): Boolean {
        // one stat serves both the freshness gate and the ownership checks below
        val mtime = runCatching { if (file.exists()) file.getLastModifiedTime().toMillis() else null }.getOrNull() ?: return false
        if (System.currentTimeMillis() - mtime >= TranscriptScanner.LIVE_WINDOW_MS) return false
        // Restart amnesia: a write that predates this daemon's boot came from our PREVIOUS instance's own
        // claude (children die with the daemon, and the restart wiped [selfClosed], which would otherwise
        // prove ownership). Never read it as a foreign writer — the app auto-reopens its session seconds
        // after a daemon update, and landed in read-only observe with a spurious "Continue here" banner.
        // A real terminal claude keeps writing, so its mtime moves past our boot within one turn.
        if (mtime < startedAt) return false
        val closedAt = mutex.withLock { selfClosed[sessionId] }
        val now = System.currentTimeMillis()
        if (closedAt != null && mtime <= closedAt + SELF_CLOSE_SLACK_MS) {
            log.info("externallyActive(${sessionId.take(8)}…): mtime ${now - mtime}ms ago is our own close tail (selfClosed ${now - closedAt}ms ago) → false")
            return false
        }
        // mtime alone can't tell "terminal claude still running" from "user quit it seconds ago" — ask the
        // OS. Only reached on a fresh foreign-looking mtime, so the lsof cost stays off every ordinary open.
        // UNKNOWN (Windows / lsof failure / timeout) keeps the old mtime verdict: a wrongly forked session
        // is recoverable, two writers clobbering one transcript is not.
        val probe = runCatching { withContext(Dispatchers.IO) { processProbe(workdir, file) } }
            .getOrDefault(LiveProcesses.ExternalClaude.UNKNOWN)
        val verdict = probe != LiveProcesses.ExternalClaude.ABSENT
        log.info(
            "externallyActive(${sessionId.take(8)}…): mtime ${now - mtime}ms ago, " +
                "selfClosed ${closedAt?.let { "${now - it}ms ago" } ?: "absent"}, probe=$probe → $verdict",
        )
        return verdict
    }

    /** Installed by the relay client; null in local-server mode. Read per turn so a conversation opened
     *  before the relay attached still sees it. */
    @Volatile
    var pushHook: PushHook? = null

    /** Returns the opened convoId, or "" if the requested backend is unavailable (a PocketError is emitted). */
    suspend fun open(open: OpenSession, sink: OutboundSink): String {
        val resume = open.resumeId
        if (resume != null) {
            // re-attach to a session the daemon is already running (a cc-pocket background session).
            // Pre-first-turn the agent hasn't reported a sessionId yet — match the resume anchor too,
            // else a reconnect re-open spawns a second Conversation onto the same transcript.
            val live = mutex.withLock {
                convos.values.firstOrNull { it.sessionId == resume || (it.sessionId == null && it.resumeAnchor == resume) }
            }
            if (live != null) {
                log.info("open ${resume.take(8)}… → reattach ${live.convoId.take(8)}…")
                cancelPendingClose(live.convoId); live.reattach(sink); return live.convoId
            }
            // observe a Claude session running OUTSIDE the daemon (e.g. a terminal) — read-only, no spawn.
            // Claude-transcript specific; Codex resume falls through to a controlled thread/resume below.
            // externallyActive requires a LIVE external process, not just a fresh mtime — a terminal claude
            // the user quit seconds ago falls through to an ordinary in-place resume, not read-only observe.
            if (open.agent == AgentKind.CLAUDE && !open.takeOver) {
                val file = ProjectPaths.dirFor(open.workdir).resolve("$resume.jsonl")
                val recent = externallyActive(resume, open.workdir, file)
                if (recent) {
                    // a reconnecting client re-opens its observe with a FRESH sink (same key, new
                    // instance). Reap this client's previous observer(s) of the same transcript first —
                    // they survive the reconnect (the device's sink key revives with it) and would keep
                    // tailing under a stale convoId, ping-ponging the phone between two SessionLive/
                    // ConvoHistory streams forever (issue #107).
                    val stale = mutex.withLock {
                        val dead = observes.filterValues { it.sessionId == resume && it.isAttachedTo(sink) }
                        dead.keys.forEach(observes::remove)
                        dead.values.toList()
                    }
                    stale.forEach { o ->
                        log.info("open ${resume.take(8)}… → reap stale observer ${o.convoId.take(8)}… (same client re-open)")
                        runCatching { o.close() }
                    }
                    val convoId = UUID.randomUUID().toString()
                    log.info("open ${resume.take(8)}… → OBSERVE ${convoId.take(8)}… (live foreign writer)")
                    val obs = ObserveSession(convoId, open.workdir, resume, file, sink, scope)
                    mutex.withLock { observes[convoId] = obs }
                    obs.start()
                    return convoId
                }
            }
        }
        // resume + control: an idle session, or an explicit "Continue here" take-over
        val factory = backendFor(open.agent)
        if (factory == null) {
            sink.emit(PocketError("agent_unavailable", "no backend registered for ${open.agent}"))
            return ""
        }
        // create() is cheap + never throws (the binary resolves lazily on first launch); the real "CLI not
        // installed" failure surfaces synchronously from c.open() below, so one guard there covers it.
        val convoId = UUID.randomUUID().toString()
        val c = Conversation(convoId, Path.of(open.workdir), open.mode, sink, scope, factory.create(), pushHookProvider = { pushHook })
        mutex.withLock { convos[convoId] = c }
        // For an explicit take-over we bypassed the ObserveSession guard above, so a desktop `claude --resume`
        // MIGHT still be writing this transcript. Fork (branch to a fresh id, dodging a two-writer clobber) ONLY
        // when [externallyActive] confirms it — fresh mtime AND a claude process alive outside the daemon (the
        // mtime window alone mistook "user quit the terminal seconds ago" for an active writer and minted bogus
        // forks). Otherwise resume IN PLACE on the same sessionId: the phone truly takes over (issue #18 — no
        // duplicate session) and the desktop picks up the phone's turns on its next --resume (issue #22 — sync).
        // Ordinary cold/idle resume already appends in place. Same detector as the ObserveSession guard above.
        val forkForTakeOver = open.takeOver && resume != null &&
            externallyActive(resume, open.workdir, ProjectPaths.dirFor(open.workdir).resolve("$resume.jsonl"))
        log.info(
            "open ${resume?.take(8) ?: "new"}${if (open.takeOver) " (take-over)" else ""} → " +
                "convo ${convoId.take(8)}… agent=${open.agent}${if (forkForTakeOver) " FORK" else ""}",
        )
        // takeOver → Conversation.open spawns EAGERLY (seize the session now); a plain open starts lazily on the
        // first prompt (issue #61) so merely previewing a session never holds/occupies it for the desktop.
        val started = runCatching { c.open(open.resumeId, open.model, open.effort, fork = forkForTakeOver, takeOver = open.takeOver) }
        if (started.isFailure) {
            mutex.withLock { convos.remove(convoId) }
            runCatching { c.close() }
            sink.emit(PocketError("agent_unavailable", "${open.agent} CLI not found — is it installed? (${started.exceptionOrNull()?.message})"))
            return ""
        }
        return convoId
    }

    /** Test hook: is [convoId] still a live observe view? (the issue-107 stale-observer reap) */
    internal suspend fun observing(convoId: String): Boolean = mutex.withLock { observes.containsKey(convoId) }

    /** Resumable sessions for [workdir] across every agent backend (each tags its summaries with its kind), newest-first. */
    fun listSessions(workdir: String): List<SessionSummary> =
        allBackends().flatMap { runCatching { it.create().listSessions(workdir) }.getOrDefault(emptyList()) }
            .sortedByDescending { it.lastModified }

    /** Archived threads are an official Codex concept; other backends are intentionally excluded. */
    fun listArchivedSessions(workdir: String): List<SessionSummary> =
        runCatching { dev.ccpocket.daemon.codex.CodexThreadArchiveClient.list(workdir) }.getOrDefault(emptyList())

    suspend fun setSessionArchived(workdir: String, sessionId: String, archived: Boolean): String? {
        val live = mutex.withLock {
            convos.values.any { it.sessionId == sessionId || it.resumeAnchor == sessionId } ||
                observes.values.any { it.isFor(sessionId) }
        }
        if (live) return "session_live"
        val belongsToProject = if (archived) {
            listSessions(workdir).any { it.agent == AgentKind.CODEX && it.sessionId == sessionId }
        } else {
            listArchivedSessions(workdir).any { it.sessionId == sessionId }
        }
        if (!belongsToProject) return "not_found"
        return dev.ccpocket.daemon.codex.CodexThreadArchiveClient.setArchived(sessionId, archived)
            ?.let { "archive_failed:$it" }
    }

    /** Delete [sessionId]'s on-disk history via its backend. Refused ("session_live") while THIS daemon
     *  is driving that session — closing it first is the client's job. Returns null on success, else a
     *  short error code the router turns into a PocketError. */
    suspend fun deleteSession(agent: AgentKind, workdir: String, sessionId: String): String? {
        val live = mutex.withLock {
            convos.values.any { it.sessionId == sessionId || it.resumeAnchor == sessionId } ||
                observes.values.any { it.isFor(sessionId) }
        }
        if (live) return "session_live"
        val backend = backendFor(agent) ?: return "agent_unavailable"
        val deleted = runCatching { backend.create().deleteSession(workdir, sessionId) }.getOrDefault(false)
        return if (deleted) null else "not_found"
    }

    /**
     * Close conversations with no agent activity for longer than [idleMs]. Returns the reap count.
     * A conversation with running background work is NEVER reaped — killing it would take its still-running
     * background shells / sub-agents with it (the "I left it running" case this is meant to preserve). Nor is
     * one with an unanswered permission ask / question (issue #55): it's blocked on the user, not idle, and
     * reaping it would silently discard a card the phone is expected to answer (plan mode surfaces the question
     * long after a premature `result`, past this idle window, while the phone is backgrounded).
     */
    suspend fun reapIdle(idleMs: Long): Int {
        // first settle background jobs whose completion event never arrived — otherwise their forever-RUNNING
        // status keeps hasBackgroundWork() true and the session can never be reaped (and the phone's "N running"
        // count never clears). Snapshot outside the lock so the per-conversation emit doesn't hold the mutex.
        mutex.withLock { convos.values.toList() }.forEach { runCatching { it.reapStaleJobs(STALE_JOB_MS) } }
        val now = System.currentTimeMillis()
        val stale = mutex.withLock {
            val s = convos.filterValues { now - it.lastActivityMs > idleMs && !it.hasBackgroundWork() && !it.hasPendingAsk() }
            convos.keys.removeAll(s.keys)
            s.values.toList()
        }
        stale.forEach { it.close(); noteSelfClosed(it) }
        return stale.size
    }

    /** Conversations mid-work (turn in flight or background jobs) — these BLOCK an account switch:
     *  swapping credentials under an agent that is actively talking to the API breaks it mid-turn.
     *  Merely-open idle conversations don't count — [closeIdleForAuth] reaps those instead (otherwise
     *  the desktop could never switch: its own open chat would always hold the guard). Observe
     *  sessions never count (they spawn no agent and hold no token).
     *
     *  Settles stale jobs FIRST: a bg shell killed outside the daemon leaves its job RUNNING forever
     *  (completion only arrives via the agent stream), and without this reap a ghost job blocks every
     *  switch until [reapIdle] happens to run. Returns one [AuthBlocker] per offender so the client
     *  can show WHAT is busy and offer to stop it, not just a count. */
    suspend fun busyForAuth(): List<AuthBlocker> {
        mutex.withLock { convos.values.toList() }.forEach { runCatching { it.reapStaleJobs(STALE_JOB_MS) } }
        return mutex.withLock {
            convos.values.mapNotNull { c ->
                val executing = c.isExecuting()
                if (!executing && !c.hasBackgroundWork()) return@mapNotNull null
                AuthBlocker(
                    convoId = c.convoId,
                    sessionId = c.sessionId,
                    cwd = c.workdir.toString(),
                    reason = if (executing) AuthBlockReason.EXECUTING else AuthBlockReason.BACKGROUND_JOBS,
                    jobLabels = if (executing) emptyList() else c.runningJobLabels(),
                )
            }
        }
    }

    /** Close every mid-work conversation ahead of a FORCED credential swap — the user saw the blocker
     *  list and chose "stop them & switch". Same lifecycle as [closeIdleForAuth] (transcripts persist,
     *  resumable); the killed process trees take their background shells with them. */
    suspend fun closeBusyForAuth(): Int {
        val busy = mutex.withLock {
            val s = convos.filterValues { it.isExecuting() || it.hasBackgroundWork() }
            convos.keys.removeAll(s.keys)
            s.values.toList()
        }
        busy.forEach { it.close(); noteSelfClosed(it) }
        return busy.size
    }

    /** Close every idle conversation ahead of a credential swap. Transcripts persist on disk, so the
     *  client resumes them like any cold session afterwards — new turns just bill the new account. */
    suspend fun closeIdleForAuth(): Int {
        val idle = mutex.withLock {
            val s = convos.filterValues { !it.isExecuting() && !it.hasBackgroundWork() }
            convos.keys.removeAll(s.keys)
            s.values.toList()
        }
        idle.forEach { it.close(); noteSelfClosed(it) }
        return idle.size
    }

    /** cwds of live conversations with running background work — kept "active" in the project list even when idle. */
    suspend fun busyCwds(): Set<String> =
        mutex.withLock { convos.values.filter { it.hasBackgroundWork() }.map { it.workdir.toString() }.toSet() }

    /** sessionIds of live conversations with running background work — keep their session row's "running" badge on. */
    suspend fun busySessionIds(): Set<String> =
        mutex.withLock { convos.values.filter { it.hasBackgroundWork() }.mapNotNull { it.sessionId }.toSet() }

    /** Live daemon conversations grouped by cwd, with their REAL turn state — the project list's
     *  authoritative live info (a dir can host several sessions; the transcript-mtime heuristic can't
     *  see turn boundaries). Pre-first-turn conversations (no sessionId yet) are skipped: there is no
     *  transcript to link a row to. Titles/branches are left null — DirectoryService enriches them. */
    suspend fun liveByCwd(): Map<String, List<dev.ccpocket.protocol.ActiveSession>> =
        mutex.withLock {
            convos.values.mapNotNull { c ->
                val sid = c.sessionId ?: return@mapNotNull null
                c.workdir.toString() to dev.ccpocket.protocol.ActiveSession(
                    sid, executing = c.isExecuting(), busy = c.hasBackgroundWork(),
                    waitingPermission = c.hasPendingAsk(), agent = c.kind,
                )
            }
        }.groupBy({ it.first }, { it.second })

    /** Routes a prompt into its conversation. False = the convo is gone (idle-reaped / daemon restarted):
     *  the router answers [dev.ccpocket.protocol.SessionGone] so the phone can re-open + resend instead of
     *  the prompt vanishing into silence (the root of "sent a message, nothing happened"). */
    suspend fun sendPrompt(p: SendPrompt): Boolean {
        val convo = get(p.convoId) ?: return false
        convo.sendPrompt(p.text, p.images, p.promptId)
        return true
    }
    suspend fun removeQueuedPrompt(convoId: String, promptId: String) = get(convoId)?.removeQueuedPrompt(promptId) ?: Unit
    suspend fun promoteQueuedPrompt(convoId: String, promptId: String) = get(convoId)?.promoteQueuedPrompt(promptId) ?: Unit
    suspend fun compact(convoId: String) = get(convoId)?.compact() ?: Unit
    suspend fun branch(convoId: String) = get(convoId)?.branch() ?: Unit
    suspend fun setGoal(frame: dev.ccpocket.protocol.SetCodexGoal) =
        get(frame.convoId)?.setGoal(frame.objective, frame.status, frame.tokenBudget, frame.clear) ?: Unit
    suspend fun startReview(frame: dev.ccpocket.protocol.StartCodexReview) =
        get(frame.convoId)?.startReview(frame.target, frame.value) ?: Unit
    suspend fun listSkills(frame: dev.ccpocket.protocol.ListCodexSkills) = get(frame.convoId)?.listSkills(frame.forceReload) ?: Unit
    suspend fun setSkillEnabled(frame: dev.ccpocket.protocol.SetCodexSkillEnabled) = get(frame.convoId)?.setSkillEnabled(frame.path, frame.enabled) ?: Unit
    suspend fun listPlugins(frame: dev.ccpocket.protocol.ListCodexPlugins) = get(frame.convoId)?.listPlugins() ?: Unit
    suspend fun setPluginInstalled(frame: dev.ccpocket.protocol.SetCodexPluginInstalled) = get(frame.convoId)?.setPluginInstalled(frame) ?: Unit
    suspend fun listIntegrations(frame: dev.ccpocket.protocol.ListCodexIntegrations) = get(frame.convoId)?.listIntegrations(frame.forceReload) ?: Unit
    suspend fun reloadMcp(frame: dev.ccpocket.protocol.ReloadCodexMcp) = get(frame.convoId)?.reloadMcp() ?: Unit
    suspend fun loginMcp(frame: dev.ccpocket.protocol.LoginCodexMcp) = get(frame.convoId)?.loginMcp(frame.serverName) ?: Unit
    suspend fun verdict(v: PermissionVerdict) = get(v.convoId)?.submitVerdict(v) ?: Unit
    suspend fun switchDir(s: SwitchDirectory) = get(s.convoId)?.switchDirectory(Path.of(s.workdir)) ?: Unit
    suspend fun switchMode(s: SwitchMode) = get(s.convoId)?.switchMode(s.mode) ?: Unit
    suspend fun clearRule(c: ClearAllowRule) = get(c.convoId)?.clearAllowRule(c.rule) ?: Unit
    suspend fun cancelTurn(c: CancelTurn) = get(c.convoId)?.cancelTurn() ?: Unit
    suspend fun stopBackgroundJob(s: StopBackgroundJob) = get(s.convoId)?.stopBackgroundJob(s.jobId) ?: Unit

    /**
     * Rename [sessionId]'s title (issue #158) by landing Claude's own `custom-title` transcript record,
     * picking the writer by who holds the file:
     *  - a conversation THIS daemon is driving with a live process → the CLI renames itself
     *    (`rename_session` control_request; it appends the record and acks) — never a second appender
     *    on a file our child is writing;
     *  - a claude LIVE OUTSIDE the daemon (terminal) → refused: we can't control that process, and
     *    appending under a foreign writer risks splicing into a record it is mid-writing;
     *  - idle transcript (incl. a daemon convo with no live process yet) → [TranscriptRename.append],
     *    the CLI's exact record shape.
     * Returns null on success (the record is on disk — a rescan sees the new title), else a
     * human-readable failure for the client's error surface. Codex sessions have no transcript under
     * the project dir and fail with the not-found message (their rename path is out of #158's scope).
     */
    suspend fun renameSession(workdir: String, sessionId: String, title: String): String? {
        val t = title.trim()
        if (t.isEmpty()) return "title must not be empty"
        val live = mutex.withLock {
            convos.values.firstOrNull {
                it.sessionId == sessionId || (it.sessionId == null && it.resumeAnchor == sessionId)
            }
        }
        if (live != null && live.hasLiveProcess()) {
            log.info("rename ${sessionId.take(8)}… via live convo ${live.convoId.take(8)}…")
            return if (live.renameSession(t)) null
            else "the running agent didn't accept the rename — stop the session and try again"
        }
        val file = ProjectPaths.dirForUnder(projectsRoot, workdir).resolve("$sessionId.jsonl")
        if (!file.exists()) return "no transcript for this session here (Codex sessions can't be renamed yet)"
        if (externallyActive(sessionId, workdir, file)) {
            return "session is live in another client — rename it there (/rename) or stop it first"
        }
        log.info("rename ${sessionId.take(8)}… via idle transcript append")
        return if (dev.ccpocket.daemon.disk.TranscriptRename.append(file, sessionId, t)) null
        else "couldn't write the rename to the transcript"
    }

    /** Workdir of a live conversation — used by voice transcription for term injection. */
    suspend fun workdirOf(convoId: String): Path? = get(convoId)?.workdir

    /** The conversation's current permission mode — the authoritative input to the shell approval gate (issue #3). */
    suspend fun modeOf(convoId: String): PermissionMode? = get(convoId)?.currentMode()

    /** Close [convoId]. With a [requester] (a client closing ITS view) this only detaches that client's
     *  sink — the conversation keeps streaming (to any other clients, else headless) when others are still
     *  attached (fan-out, issue #47) OR it is still busy (a turn in flight / background work / an unanswered
     *  ask). The phone sends CloseSession when it leaves a session its `streaming` flag calls idle, but plan
     *  mode's premature `result` clears that flag long before the turn truly ends (issue #55): a "done"-looking
     *  session the user taps away from is often still researching and about to ask a question. Killing it here
     *  would abort the turn and drop the question; instead it survives (the idle reaper reclaims it once it
     *  really goes idle and the phone stays away — the same spare set as reapIdle/scheduleClose). Returns true
     *  when the conversation was actually closed.
     *
     *  [force] = the user explicitly asked to STOP the session (an account-switch blocker row), not to
     *  leave it: skip the keep-alive shield and kill it busy or not (transcript persists, resumable). */
    suspend fun close(convoId: String, requester: OutboundSink? = null, force: Boolean = false): Boolean {
        if (requester != null && !force) {
            val keepAlive = mutex.withLock {
                val c = convos[convoId] ?: return@withLock false // gone, or an observe session — fall through to close
                val emptied = c.detach(requester) // drop THIS view; true if no sinks remain
                !emptied || c.isBusy()
            }
            if (keepAlive) {
                log.info("detach ${convoId.take(8)}… (other clients or still busy — kept alive)")
                return false
            }
        }
        val (job, convo, obs) = mutex.withLock {
            Triple(pendingClose.remove(convoId), convos.remove(convoId), observes.remove(convoId))
        }
        job?.cancel(); convo?.close(); obs?.close()
        if (convo != null || obs != null) log.info("close ${convoId.take(8)}… (sid=${convo?.sessionId?.take(8) ?: "-"}, observe=${obs != null})")
        convo?.let { noteSelfClosed(it) }
        return convo != null || obs != null
    }

    /** Remember that WE closed this session just now — see [selfClosed]/[externallyActive]. Call AFTER
     *  [Conversation.close] returns: the dying process's final transcript flush must predate closedAt. */
    private suspend fun noteSelfClosed(convo: Conversation) {
        val sid = convo.sessionId ?: return // pre-first-turn: our silent process never wrote the transcript
        mutex.withLock { selfClosed[sid] = System.currentTimeMillis() }
    }

    /**
     * Close [convoId] after [graceMs] UNLESS a reconnect reattaches first (which calls [cancelPendingClose]).
     * The LAN server uses this on socket drop instead of closing immediately: a flaky link / backgrounded phone
     * would otherwise instantly kill the claude process and rewrite the transcript, forcing the reconnect into a
     * cold `--resume` (issue #24's amplifier) and losing warm session state. Relay drops have their own grace
     * (reaperLoop's 90s idle window); this is the LAN equivalent. A second schedule replaces the first.
     *
     * [owner] is the scheduling connection's sink: at expiry the close only fires if the conversation is STILL
     * attached to it. A zombie socket's late `finally` (TCP can take minutes to give up) must not kill a
     * conversation a newer connection has since reattached — and the same check closes the reattach-vs-expiry
     * race (a reattach re-points the sink before cancelPendingClose lands).
     */
    suspend fun scheduleClose(convoId: String, owner: OutboundSink, graceMs: Long = LAN_DISCONNECT_GRACE_MS) {
        val job = scope.launch {
            delay(graceMs)
            // deregister BEFORE closing: otherwise close() below would cancel this very job mid-flight
            mutex.withLock { pendingClose.remove(convoId) }
            // A conversation still WORKING (streaming turn / running background jobs) survives its owner's
            // disconnect: re-arm and check again next window, close only once truly idle. Killing in-flight
            // work because the app quit (update/relaunch/crash) is exactly what the task-complete push
            // promises never happens — the relay path's idle reaper already spares busy convos this way.
            val busy = mutex.withLock {
                convos[convoId]?.takeIf { it.isAttachedTo(owner) }?.isBusy() == true
            }
            if (busy) {
                log.info("grace expiry ${convoId.take(8)}… still working → re-armed ${graceMs}ms")
                scheduleClose(convoId, owner, graceMs); return@launch
            }
            val (convo, obs) = mutex.withLock {
                val c = convos[convoId]
                val o = observes[convoId]
                when {
                    // fan-out: the dead socket only takes ITS view with it — close only if it was the last
                    c != null && c.isAttachedTo(owner) ->
                        if (c.detach(owner)) { convos.remove(convoId); c to null } else null to null
                    o != null && o.isAttachedTo(owner) -> { observes.remove(convoId); null to o }
                    else -> null to null // reattached elsewhere (or already gone) — not ours to kill
                }
            }
            if (convo != null || obs != null) {
                log.info("grace expiry closed ${convoId.take(8)}… (sid=${convo?.sessionId?.take(8) ?: "-"}, observe=${obs != null})")
            }
            convo?.close(); obs?.close()
            convo?.let { noteSelfClosed(it) }
        }
        mutex.withLock { pendingClose.put(convoId, job) }?.cancel()
    }

    private suspend fun cancelPendingClose(convoId: String) {
        mutex.withLock { pendingClose.remove(convoId) }?.cancel()
    }

    suspend fun closeAll() {
        val all = mutex.withLock { convos.values.toList().also { convos.clear() } }
        val obs = mutex.withLock { observes.values.toList().also { observes.clear() } }
        // Close in parallel. The daemon-shutdown hook (Main.kt / DaemonServer.kt) runs this, and each
        // Conversation.close can spend AgentProcess's bounded EOF->SIGTERM->SIGKILL grace on a wedged
        // child (issue #101). Serialised, N wedged sessions would sum past launchd/systemd's stop
        // timeout and get the daemon SIGKILLed mid-flush — the very transcript loss #101 fixes. So the
        // per-session grace stays bounded AND the total stays ~one session's budget, not N.
        // runCatching per close so a single failure (or the scope being cancelled) can't abandon the
        // remaining reaps.
        coroutineScope {
            (all.map { async { runCatching { it.close() } } } +
                obs.map { async { runCatching { it.close() } } }).awaitAll()
        }
    }

    private suspend fun get(id: String): Conversation? = mutex.withLock { convos[id] }

    private companion object {
        // a backgrounded shell silent this long (no started/updated/result event) is treated as dead. Well above
        // any real launch-to-first-update gap, so a genuinely long-running background job is never reaped early.
        const val STALE_JOB_MS = 15 * 60 * 1000L

        // how long a LAN conversation survives a socket drop before being reaped, so a reconnecting phone can
        // reattach the still-warm claude process instead of paying a kill + transcript rewrite + cold resume.
        const val LAN_DISCONNECT_GRACE_MS = 30_000L

        // transcript writes no later than this past our own close are still "our" writes (FS timestamp
        // granularity + the post-exit unhide); anything newer means a real external claude took over.
        const val SELF_CLOSE_SLACK_MS = 1_500L
    }
}

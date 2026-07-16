package dev.ccpocket.daemon.server

import dev.ccpocket.daemon.DaemonPrefs
import dev.ccpocket.daemon.claude.AuthService
import dev.ccpocket.daemon.conversation.OutboundSink
import dev.ccpocket.daemon.disk.DirectoryService
import dev.ccpocket.daemon.disk.SessionFilesService
import dev.ccpocket.daemon.disk.UsageService
import dev.ccpocket.daemon.session.SessionRegistry
import dev.ccpocket.daemon.shell.ShellService
import dev.ccpocket.daemon.transcribe.TranscribeService
import dev.ccpocket.protocol.AudioCancel
import dev.ccpocket.protocol.AudioChunk
import dev.ccpocket.protocol.AuthLogin
import dev.ccpocket.protocol.AuthLoginCancel
import dev.ccpocket.protocol.AuthLoginCode
import dev.ccpocket.protocol.AuthLogout
import dev.ccpocket.protocol.CancelTurn
import dev.ccpocket.protocol.ClearAllowRule
import dev.ccpocket.protocol.CloseSession
import dev.ccpocket.protocol.DeleteSession
import dev.ccpocket.protocol.CompactSession
import dev.ccpocket.protocol.BranchSession
import dev.ccpocket.protocol.SetSessionArchived
import dev.ccpocket.protocol.SetCodexGoal
import dev.ccpocket.protocol.StartCodexReview
import dev.ccpocket.protocol.ListCodexSkills
import dev.ccpocket.protocol.SetCodexSkillEnabled
import dev.ccpocket.protocol.ListCodexPlugins
import dev.ccpocket.protocol.SetCodexPluginInstalled
import dev.ccpocket.protocol.ListCodexIntegrations
import dev.ccpocket.protocol.ReloadCodexMcp
import dev.ccpocket.protocol.LoginCodexMcp
import dev.ccpocket.protocol.Directories
import dev.ccpocket.protocol.FetchAuthStatus
import dev.ccpocket.protocol.FetchUsage
import dev.ccpocket.protocol.ConsumeCodexLimitReset
import dev.ccpocket.protocol.Frame
import dev.ccpocket.protocol.ListDirectories
import dev.ccpocket.protocol.ListCursorModels
import dev.ccpocket.protocol.ListAgencyAgents
import dev.ccpocket.protocol.CursorModels
import dev.ccpocket.protocol.ListPathEntries
import dev.ccpocket.protocol.ListSessionFiles
import dev.ccpocket.protocol.ListSessions
import dev.ccpocket.protocol.PathEntries
import dev.ccpocket.protocol.ReadFile
import dev.ccpocket.protocol.ReadFileDiff
import dev.ccpocket.protocol.SessionFiles
import dev.ccpocket.protocol.OpenSession
import dev.ccpocket.protocol.PermissionVerdict
import dev.ccpocket.protocol.PocketError
import dev.ccpocket.protocol.PushPrefs
import dev.ccpocket.protocol.RunShellCommand
import dev.ccpocket.protocol.SendPrompt
import dev.ccpocket.protocol.SessionGone
import dev.ccpocket.protocol.Sessions
import dev.ccpocket.protocol.SetPushPrefs
import dev.ccpocket.protocol.ShellResult
import dev.ccpocket.protocol.StopBackgroundJob
import dev.ccpocket.protocol.SwitchDirectory
import dev.ccpocket.protocol.SwitchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Maps an inbound [Frame] to the registry/services. Returns fast; turns run on conversation scopes. */
class RequestRouter(
    private val registry: SessionRegistry,
    private val dirs: DirectoryService,
    private val transcribe: TranscribeService,
    private val shell: ShellService,
    private val scope: CoroutineScope,
    private val auth: AuthService,
    private val prefs: DaemonPrefs,
) {
    suspend fun handle(frame: Frame, sink: OutboundSink, onOpened: suspend (String) -> Unit = {}) {
        when (frame) {
            is ListDirectories -> sink.emit(Directories(dirs.listDirectories(frame.root, registry.busyCwds(), registry.liveByCwd())))

            is ListSessions -> {
                if (frame.archived) {
                    sink.emit(Sessions(frame.workdir, registry.listArchivedSessions(frame.workdir), archived = true))
                } else {
                    val busy = registry.busySessionIds()
                    // merge every backend's resumable sessions for this dir (Claude ~/.claude/projects + Codex ~/.codex/sessions)
                    val items = registry.listSessions(frame.workdir)
                        .map { if (it.sessionId in busy) it.copy(busy = true) else it }
                    sink.emit(Sessions(frame.workdir, items, renameSupported = true))
                }
            }

            is RenameSession -> scope.launch {
                val err = registry.renameSession(frame.workdir, frame.sessionId, frame.title)
                if (err != null) {
                    sink.emit(PocketError("rename_failed", err))
                } else {
                    val busy = registry.busySessionIds()
                    val items = registry.listSessions(frame.workdir)
                        .map { if (it.sessionId in busy) it.copy(busy = true) else it }
                    sink.emit(Sessions(frame.workdir, items, renameSupported = true))
                }
            }

            is ListCursorModels -> scope.launch {
                val models = runCatching { registry.cursorModels() }.getOrDefault(emptyList())
                sink.emit(CursorModels(models, if (models.isEmpty()) "cursor-agent model discovery failed" else null))
            }

            is ListAgencyAgents -> scope.launch {
                sink.emit(dev.ccpocket.daemon.agent.AgencyAgentService.list())
            }

            // heavy transcript scan → off the inbound pump so it can't wedge the socket
            is FetchUsage -> scope.launch {
                val codexAccount = dev.ccpocket.daemon.codex.CodexRateLimitsClient.readAccountSnapshot()
                sink.emit(UsageService.aggregate(
                    frame.days,
                    liveCodexLimits = codexAccount.limits,
                    liveClaudeLimits = dev.ccpocket.daemon.claude.ClaudeRateLimitsClient.read(),
                    liveCodexAccountUsage = codexAccount.usage,
                ))
            }

            // Spending a reset credit is an explicit, user-confirmed mutation. The phone supplies a stable
            // idempotency key so a relay retry cannot consume twice; the result includes the refreshed limits.
            is ConsumeCodexLimitReset -> scope.launch {
                sink.emit(dev.ccpocket.daemon.codex.CodexRateLimitsClient.consume(frame.idempotencyKey))
            }

            // both re-scan the transcript from disk (issue #36) → same off-pump rule as FetchUsage
            is ListSessionFiles -> scope.launch {
                sink.emit(SessionFiles(frame.workdir, frame.sessionId, SessionFilesService.changedFiles(frame.agent, frame.workdir, frame.sessionId)))
            }
            is ReadFile -> scope.launch {
                sink.emit(SessionFilesService.readFile(frame.agent, frame.workdir, frame.sessionId, frame.path))
            }
            is ReadFileDiff -> scope.launch {
                sink.emit(SessionFilesService.fileDiff(frame.agent, frame.workdir, frame.sessionId, frame.path))
            }
            // composer @-file completion (issue #75): a directory scan → off the inbound pump like the others
            is ListPathEntries -> scope.launch {
                val res = dirs.listPathEntries(frame.workdir, frame.subPath, frame.limit)
                sink.emit(
                    PathEntries(
                        workdir = frame.workdir,
                        subPath = frame.subPath,
                        entries = res?.first ?: emptyList(),
                        truncated = res?.second ?: false,
                        ok = res != null,
                        error = if (res == null) "not a readable directory" else null,
                    ),
                )
            }

            is OpenSession -> {
                // a new project: create the named folder if it doesn't exist yet (under an existing writable parent)
                val wd = dirs.validateOrCreateWorkdir(frame.workdir)
                if (wd == null) {
                    sink.emit(PocketError("bad_workdir", "not a readable directory: ${frame.workdir}"))
                } else {
                    dirs.noteRecent(wd.toString())
                    val convoId = registry.open(frame.copy(workdir = wd.toString()), sink)
                    if (convoId.isNotEmpty()) onOpened(convoId) // "" = backend unavailable (PocketError already sent)
                }
            }

            is SendPrompt -> {
                val expanded = if (frame.agencyAgentIds.isEmpty()) frame.text else
                    dev.ccpocket.daemon.agent.AgencyAgentService.expand(frame.text, frame.agencyAgentIds)
                if (!registry.sendPrompt(frame.copy(text = expanded))) sink.emit(SessionGone(frame.convoId))
            }
            // a verdict may resolve a SHELL ask (issue #3) or an agent tool ask — shell claims its own by askId
            is PermissionVerdict -> if (!shell.onVerdict(frame)) registry.verdict(frame)
            is SwitchMode -> registry.switchMode(frame)
            is ClearAllowRule -> registry.clearRule(frame)

            // MUST launch, not await: shell.run suspends on the human approval gate, but the relay transport
            // pumps inbound frames sequentially & inline — awaiting here would wedge the whole socket (for every
            // device/convo) until the verdict, which itself can't be read while we block. Fire it on the daemon
            // scope so the loop stays free to deliver that verdict.
            is RunShellCommand -> scope.launch {
                val wd = dirs.validateWorkdir(frame.workdir)
                if (wd == null) {
                    sink.emit(ShellResult(frame.convoId, frame.command, exitCode = -1, error = "not a readable directory: ${frame.workdir}"))
                } else {
                    // the daemon (not the phone) decides the mode → the approval gate can't be spoofed client-side
                    shell.run(frame.copy(workdir = wd.toString()), registry.modeOf(frame.convoId), sink::emit)
                }
            }

            is SwitchDirectory -> {
                val wd = dirs.validateWorkdir(frame.workdir)
                if (wd == null) {
                    sink.emit(PocketError("bad_workdir", "not a readable directory: ${frame.workdir}", frame.convoId))
                } else {
                    dirs.noteRecent(wd.toString())
                    registry.switchDir(frame.copy(workdir = wd.toString()))
                }
            }

            // fan-out: only a REAL close (last attached client) drops the quick-terminal state with it
            is CloseSession -> { if (registry.close(frame.convoId, sink, frame.force)) shell.forget(frame.convoId) }

            // history deletion: refuse ids with path separators BEFORE any backend touches the filesystem,
            // then reply with the refreshed list so the phone reconciles in one round-trip
            is DeleteSession -> scope.launch {
                val sid = frame.sessionId
                val err = when {
                    sid.isBlank() || sid.contains('/') || sid.contains('\\') || sid.contains("..") -> "bad_session_id"
                    else -> registry.deleteSession(frame.agent, frame.workdir, sid)
                }
                if (err != null) {
                    val message = when (err) {
                        "session_live" -> "session is running — close it before deleting"
                        "not_found" -> "no on-disk history found for this session"
                        else -> "cannot delete session ($err)"
                    }
                    sink.emit(PocketError(err, message))
                }
                val busy = registry.busySessionIds()
                val items = registry.listSessions(frame.workdir).map { if (it.sessionId in busy) it.copy(busy = true) else it }
                sink.emit(Sessions(frame.workdir, items))
            }
            is SetSessionArchived -> scope.launch {
                val sid = frame.sessionId
                val err = when {
                    sid.isBlank() || sid.contains('/') || sid.contains('\\') || sid.contains("..") -> "bad_session_id"
                    else -> registry.setSessionArchived(frame.workdir, sid, frame.archived)
                }
                if (err != null) {
                    val message = if (err == "session_live") "session is running — close it before archiving"
                    else "cannot ${if (frame.archived) "archive" else "restore"} session ($err)"
                    sink.emit(PocketError(err.substringBefore(':'), message))
                }
                val items = if (frame.archived) registry.listSessions(frame.workdir) else registry.listArchivedSessions(frame.workdir)
                sink.emit(Sessions(frame.workdir, items, archived = !frame.archived))
            }
            is CancelTurn -> registry.cancelTurn(frame)
            is CompactSession -> registry.compact(frame.convoId)
            is BranchSession -> registry.branch(frame.convoId)
            is SetCodexGoal -> registry.setGoal(frame)
            is StartCodexReview -> registry.startReview(frame)
            is ListCodexSkills -> registry.listSkills(frame)
            is SetCodexSkillEnabled -> registry.setSkillEnabled(frame)
            is ListCodexPlugins -> registry.listPlugins(frame)
            is SetCodexPluginInstalled -> registry.setPluginInstalled(frame)
            is ListCodexIntegrations -> registry.listIntegrations(frame)
            is ReloadCodexMcp -> registry.reloadMcp(frame)
            is LoginCodexMcp -> registry.loginMcp(frame)
            // task panel "stop" (issue #80): interrupt the agent's work for this job + settle its row killed
            is StopBackgroundJob -> registry.stopBackgroundJob(frame)

            // voice capture: buffer fast here; whisper runs on the service's own scope
            is AudioChunk -> transcribe.onChunk(frame, sink)
            is AudioCancel -> transcribe.onCancel(frame)

            // account switching: each spawns a `claude auth …` child — off the inbound pump, like FetchUsage
            is FetchAuthStatus -> scope.launch { auth.sendStatus(sink::emit) }
            is AuthLogin -> scope.launch { auth.login(frame.console, sink::emit, frame.force) }
            is AuthLoginCode -> scope.launch { auth.submitCode(frame.code, sink::emit) }
            is AuthLoginCancel -> scope.launch { auth.cancelLogin(sink::emit) }
            is AuthLogout -> scope.launch { auth.logout(sink::emit) }

            // phone-push switch: null enabled = query only; either way the daemon's truth is the reply
            is SetPushPrefs -> {
                frame.enabled?.let(prefs::setPushEnabled)
                sink.emit(PushPrefs(prefs.pushEnabled))
            }

            else -> sink.emit(PocketError("unsupported", "frame not handled by daemon: ${frame::class.simpleName}"))
        }
    }
}

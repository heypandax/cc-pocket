package dev.ccpocket.daemon.agent

import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.AgentModel
import dev.ccpocket.protocol.HistoryMessage
import dev.ccpocket.protocol.ImageData
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.SessionSummary
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * Drives ONE live conversation against ONE agent CLI (Claude stream-json / Codex app-server JSON-RPC).
 * Created per [dev.ccpocket.daemon.conversation.Conversation]; [attach] is (re)called on every process
 * (re)launch to reset per-process protocol state and rebind IO. This interface + [AgentEvent] is the
 * entire provider seam — Conversation, SessionRegistry and the phone protocol stay agent-agnostic.
 */
interface AgentBackend {
    val kind: AgentKind

    /** True for CLIs whose structured mode handles one prompt and then exits (Cursor Agent). */
    val exitsAfterTurn: Boolean get() = false

    /** Models currently available to this backend's signed-in account; empty when unsupported. */
    fun availableModels(): List<AgentModel> = emptyList()

    /** Optional provider-specific explanation for a process that exited without a terminal result. */
    fun processExitError(exitCode: Int?, stderr: String?): String? = null

    /** Build the OS process for [spec]. Pure (no side effects); the caller starts it. */
    fun processBuilder(spec: AgentSpec): ProcessBuilder

    /** (Re)bind to a freshly started process: store [io], reset per-process state, run any handshake
     *  (Codex: initialize → initialized → thread/start). Called on first launch AND every relaunch. */
    suspend fun attach(io: AgentIo, spec: AgentSpec)

    /** One raw stdout line → domain events. May update internal protocol state and write follow-ups
     *  via the attached IO (Codex bootstrap/turn plumbing). Must never throw. */
    suspend fun parse(line: String): List<AgentEvent>

    /** Encode + write a user turn (Codex buffers it until its thread handshake completes). */
    suspend fun sendPrompt(text: String, images: List<ImageData>)

    /** Encode + write an interrupt for the in-flight turn; no-op if the process isn't ready. */
    suspend fun interrupt()

    /** Start provider-native context compaction. True when accepted; false lets Conversation use the
     * backend's compatible /compact prompt path. */
    suspend fun compact(): Boolean = false

    /** Update or clear the provider's persistent thread goal. False means unsupported/not ready. */
    suspend fun setGoal(objective: String?, status: String?, tokenBudget: Long?, clear: Boolean): Boolean = false

    /** Write a permission decision for [askId] (an [AgentEvent.ControlRequest.requestId]).
     *  [remember] maps to a session-scoped "always allow" (Codex acceptForSession). */
    suspend fun respondPermission(
        askId: String,
        allow: Boolean,
        remember: Boolean,
        originalInput: JsonObject?,
        updatedInput: String?,
        denyMessage: String?,
    )

    /** Apply a runtime mode/model/effort change. Returns true if the process must be RELAUNCHED for it to
     *  take effect (Claude bakes flags at launch); false if it applies to the next turn (Codex). A null arg
     *  means "unchanged". */
    fun applySettings(mode: PermissionMode?, model: String?, effort: String?): Boolean

    /** Normalize a requested model before it's stored/launched; null = "use the default". Cursor drops
     *  display names ("Fable 5 300K Max No Thinking") that its own init once echoed back and phones may
     *  have persisted — `--model` only accepts ids, and launching with a display name exits 1. */
    fun sanitizeModel(model: String?): String? = model

    /** Hook fired when the process is shutting down (intentional stop or unexpected exit), once its
     *  transcript is quiet. Claude rewrites the .jsonl so the desktop --resume picker shows it; Codex no-op. */
    suspend fun onProcessEnded(sessionId: String?)

    /** Hook fired once per process when the agent reports its real session id. Claude journals it so a
     *  crashed daemon's transcripts can be unhidden for the resume pickers at next boot (issue #70). */
    suspend fun onSessionStarted(sessionId: String, workdir: String) {}

    // ---- disk: resume / listing / history (per-backend transcript stores) ----

    /** The on-disk transcript directory for [workdir] (Claude: ~/.claude/projects/<key>; Codex: ~/.codex/sessions). */
    fun transcriptDir(workdir: String): Path

    /** Resumable sessions for [workdir], newest first (reads transcript headers; no process launch). */
    fun listSessions(workdir: String): List<SessionSummary>

    /** Prior transcript of [sessionId] under [workdir], flattened for replay to the phone. */
    fun replayHistory(workdir: String, sessionId: String): List<HistoryMessage>

    /** Context tokens the last completed turn of [sessionId] left in the window — seeds the phone's
     *  usage statusline on resume. Null when unknown (no transcript / no usage yet / backend doesn't
     *  surface per-turn usage on disk, e.g. Codex). */
    fun resumeContextTokens(workdir: String, sessionId: String): Long?

    /** The model id the last completed turn of [sessionId] used — lets a cold resume announce the session's real
     *  model + context window before the first new turn's init lands. Null when unknown / not on disk (default;
     *  e.g. Codex). Claude reads it from the transcript. */
    fun resumeModel(workdir: String, sessionId: String): String? = null

    /** The model this backend WOULD use for a session started with NO explicit `--model` — read from config so
     *  a brand-new session's header shows the real model BEFORE the first turn (issue #96; lazy start #61 spawns
     *  no process pre-first-prompt, so there's no init to name it). Best-effort: null when nothing is configured
     *  (the account default then decides, which only the first turn's init can name — the phone shows a
     *  placeholder). MUST be cheap and DEFENSIVE — reads config only, never launches the agent, never throws: a
     *  failed eager resolve degrades to null, it never crashes or blocks the open (claude ≥1.3.1 crash-loops on
     *  eager-resolve failures). [workdir] lets a backend honor project-scoped config. Default null = other/older
     *  backends. */
    fun defaultModel(workdir: String): String? = null

    /** How many consecutive turns at the transcript's TAIL were API-failure placeholders — seeds the
     *  degraded-session warning on resume (issue #65). 0 = healthy/unknown (default; e.g. Codex). */
    fun resumeFailedTurnStreak(workdir: String, sessionId: String): Int = 0

    /** Delete [sessionId]'s on-disk history under [workdir] (transcript / rollout / chat store).
     *  Only called for sessions the daemon is NOT currently driving. True = something was removed.
     *  Default false = backend doesn't support deletion. */
    fun deleteSession(workdir: String, sessionId: String): Boolean = false
}

/** Builds a fresh [AgentBackend] per conversation. One factory per [AgentKind], registered in the daemon core. */
fun interface AgentBackendFactory {
    fun create(): AgentBackend
}

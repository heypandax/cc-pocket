package dev.ccpocket.daemon.cursor

import dev.ccpocket.daemon.agent.AgentBackend
import dev.ccpocket.daemon.agent.AgentEvent
import dev.ccpocket.daemon.agent.AgentIo
import dev.ccpocket.daemon.agent.AgentSpec
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.AgentModel
import dev.ccpocket.protocol.AgentModelVariant
import dev.ccpocket.protocol.HistoryMessage
import dev.ccpocket.protocol.ImageData
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.SessionSummary
import dev.ccpocket.protocol.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.nio.file.Path
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Cursor Agent adapter for `--print --output-format stream-json` (one process per run). */
class CursorBackend(private val cursorBin: String?) : AgentBackend {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var io: AgentIo? = null
    @Volatile private var resolvedExe: Path? = null
    @Volatile private var mode = PermissionMode.DEFAULT
    @Volatile private var model: String? = null
    private val emittedAssistant = LinkedHashSet<String>()
    private var lastAssistantSnapshot = ""
    private var finalText: String? = null
    private var workdir: Path = Path.of(".")
    private val temporaryImages = mutableListOf<Path>()
    private var cursorError: String? = null

    override val kind = AgentKind.CURSOR
    override val exitsAfterTurn = true

    override fun availableModels(): List<AgentModel> {
        val process = ProcessBuilder(exe().toString(), "--list-models").redirectErrorStream(true).start()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return emptyList()
        }
        val lines = process.inputStream.bufferedReader().readLines()
        if (process.exitValue() != 0) return emptyList()
        return parseModelLines(lines)
    }

    internal fun parseModelLines(lines: List<String>): List<AgentModel> {
        val raw = lines.mapNotNull { line ->
            val separator = line.indexOf(" - ")
            if (separator <= 0) null else AgentModel(line.substring(0, separator).trim(), line.substring(separator + 3).trim())
        }.distinctBy { it.id }
        // cursor-agent exposes every effort/thinking/fast permutation as a separate --model id, while Cursor's
        // own picker shows one logical model and a second parameter control. Preserve first-seen order: the CLI
        // deliberately lists its recommended/default permutation for each family before the exhaustive matrix.
        return raw.groupBy { modelFamilyId(it.id) }.values.map { family ->
            val preferred = family.first()
            preferred.copy(
                name = logicalModelName(preferred.name),
                variants = family.map { AgentModelVariant(it.id, variantName(it.name)) },
            )
        }
    }

    internal fun logicalModelName(display: String): String = display
        .replace(" (current)", "")
        .replace(Regex("\\s+(1M|300K|272K|200K)(\\s+.*)?$"), "")
        .replace(Regex("\\s+(Low|Medium|High|Extra High)( Fast)?$"), "")
        .replace(Regex("\\s+Thinking( Fast)?$"), "")
        .replace(Regex("\\s+Fast$"), "")
        .trim()

    internal fun variantName(display: String): String = display
        .replace(" (current)", "")
        .replace(Regex("^.*?\\s+(1M|300K|272K|200K)\\s*"), "")
        .trim()

    internal fun modelFamilyId(id: String): String {
        var base = id.removeSuffix("-fast")
        val suffixes = listOf(
            "-thinking-extra-high", "-thinking-xhigh", "-thinking-medium", "-thinking-high", "-thinking-low", "-thinking-max",
            "-extra-high-thinking", "-xhigh-thinking", "-medium-thinking", "-high-thinking", "-low-thinking", "-max-thinking",
            "-extra-high", "-xhigh", "-medium", "-high", "-low", "-none", "-thinking",
        )
        suffixes.firstOrNull { base.endsWith(it) }?.let { base = base.removeSuffix(it) }
        return base
    }

    private fun exe() = resolvedExe ?: CursorLauncher.resolveExecutable(cursorBin).also { resolvedExe = it }
    override fun processBuilder(spec: AgentSpec) = CursorLauncher.processBuilder(exe(), spec)

    override suspend fun attach(io: AgentIo, spec: AgentSpec) {
        this.io = io
        mode = spec.mode
        model = spec.model
        workdir = spec.workdir
        emittedAssistant.clear()
        lastAssistantSnapshot = ""
        finalText = null
        cursorError = null
        cleanupImages()
    }

    override suspend fun parse(line: String): List<AgentEvent> {
        val root = runCatching { json.parseToJsonElement(line.trim()) as? JsonObject }.getOrNull()
            ?: return if (line.isBlank()) emptyList() else listOf(AgentEvent.Unparseable(line))
        return when (root.str("type")) {
            "system" -> if (root.str("subtype") == "init") listOf(
                AgentEvent.SessionInit(root.str("session_id"), root.str("cwd"), root.str("model")),
            ) else emptyList()
            "thinking" -> if (root.str("subtype") == "delta") root.str("text")?.let { listOf(AgentEvent.AssistantThinking(it)) } ?: emptyList() else emptyList()
            "assistant" -> assistant(root)
            "tool_call" -> toolCall(root)
            "result" -> result(root)
            "error" -> {
                cursorError = root.str("message") ?: root.str("error") ?: "Cursor Agent reported an error"
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun assistant(root: JsonObject): List<AgentEvent> {
        val text = root.obj("message")?.arr("content")?.mapNotNull { item ->
            (item as? JsonObject)?.takeIf { it.str("type") == "text" }?.str("text")
        }?.joinToString("").orEmpty()
        if (text.isBlank()) return emptyList()
        finalText = text
        // Cursor emits assistant snapshots, not reliably deltas: an answer may arrive as "hello" and then
        // "hello world", and some versions repeat the final snapshot with whitespace-only differences. Sending
        // every snapshot makes the phone render the whole answer twice. Reconcile cumulative snapshots into a
        // delta while leaving genuinely separate assistant messages untouched.
        if (!emittedAssistant.add(text) || text.trim() == lastAssistantSnapshot.trim()) return emptyList()
        val previous = lastAssistantSnapshot
        val delta = when {
            previous.isEmpty() -> text
            text.startsWith(previous) -> text.removePrefix(previous)
            previous.startsWith(text) -> "" // stale/shorter snapshot arriving after the complete one
            else -> text
        }
        lastAssistantSnapshot = text
        return delta.takeIf { it.isNotEmpty() }?.let { listOf(AgentEvent.AssistantText(it)) }.orEmpty()
    }

    private fun toolCall(root: JsonObject): List<AgentEvent> {
        val callId = root.str("call_id")
        val call = root.obj("tool_call") ?: return emptyList()
        val entry = call.entries.firstOrNull() ?: return emptyList()
        val body = entry.value as? JsonObject
        val name = when (entry.key) {
            "readToolCall" -> "Read"
            "writeToolCall" -> "Write"
            "editToolCall" -> "Edit"
            "shellToolCall", "terminalToolCall" -> "Bash"
            else -> entry.key.removeSuffix("ToolCall")
        }
        val args = body?.obj("args")
        return when (root.str("subtype")) {
            "started" -> listOf(AgentEvent.AssistantToolUse(callId, name, args))
            "completed" -> {
                val result = body?.get("result")
                val failed = (result as? JsonObject)?.containsKey("error") == true
                listOf(AgentEvent.ToolResult(callId, result?.toString(), failed))
            }
            else -> emptyList()
        }
    }

    private fun result(root: JsonObject): List<AgentEvent> {
        val usage = root.obj("usage")
        val u = usage?.let {
            TokenUsage(
                inputTokens = it.long("inputTokens") ?: 0,
                outputTokens = it.long("outputTokens") ?: 0,
                cacheCreationInputTokens = it.long("cacheWriteTokens"),
                cacheReadInputTokens = it.long("cacheReadTokens"),
            )
        }
        val text = root.str("result") ?: cursorError ?: finalText
        return listOf(AgentEvent.TurnResult(text, u, root.bool("is_error") == true || root.str("subtype") != "success"))
    }

    override suspend fun sendPrompt(text: String, images: List<ImageData>) {
        val refs = images.mapIndexedNotNull { index, image -> writeImage(index, image) }
        val suffix = if (refs.isEmpty()) "" else refs.joinToString(prefix = "\n\nAttached images:\n", separator = "\n") { "@${it.fileName}" }
        io?.writeLine(text + suffix)
        io?.closeInput()
    }

    override suspend fun interrupt() { io?.stopProcess() }
    override suspend fun respondPermission(askId: String, allow: Boolean, remember: Boolean, originalInput: JsonObject?, updatedInput: String?, denyMessage: String?) = Unit

    // Cursor bakes model/mode flags into each one-shot run. The next turn always launches a new process.
    override fun applySettings(mode: PermissionMode?, model: String?, effort: String?): Boolean {
        mode?.let { this.mode = it }
        model?.let { this.model = it }
        return true
    }

    override suspend fun onProcessEnded(sessionId: String?) { cleanupImages() }
    override fun transcriptDir(workdir: String): Path = CursorPaths.sessionsRoot()
    override fun listSessions(workdir: String): List<SessionSummary> = CursorSessionScanner.scan(workdir)
    override fun replayHistory(workdir: String, sessionId: String): List<HistoryMessage> =
        CursorPaths.transcript(sessionId)?.let(CursorTranscriptReplay::read).orEmpty()

    // a Cursor session's record spans three stores (acp-sessions dir, chats/<hash>/<id> dir, public
    // agent-transcript dir) — remove whichever exist. Deleting the SESSION DIRECTORY (not a lone file)
    // in each store matches how cursor-agent itself lays them out.
    override fun deleteSession(workdir: String, sessionId: String): Boolean = CursorPaths.deleteSession(sessionId)
    override fun resumeContextTokens(workdir: String, sessionId: String): Long? = null
    override fun defaultModel(workdir: String): String = "auto"

    override fun processExitError(exitCode: Int?, stderr: String?): String {
        val detail = stderr?.trim().orEmpty()
        val lower = detail.lowercase()
        return when {
            "not authenticated" in lower || "login" in lower && "required" in lower ->
                "Cursor Agent is not logged in on this computer. Run: cursor-agent login"
            "model" in lower && ("invalid" in lower || "not found" in lower || "unsupported" in lower) ->
                "The selected Cursor model is unavailable for this account. Refresh the model list or choose Auto."
            "permission" in lower || "denied" in lower ->
                "Cursor blocked this action under the current permission mode. Choose Balanced/Autonomous or update Cursor CLI permissions."
            detail.isNotBlank() -> "Cursor Agent exited (code ${exitCode ?: "?"}): ${detail.take(300)}"
            else -> "Cursor Agent exited without a result (code ${exitCode ?: "?"}). Check cursor-agent status on the computer."
        }
    }

    private fun writeImage(index: Int, image: ImageData): Path? = runCatching {
        val suffix = when (image.mediaType.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            else -> ".png"
        }
        val path = Files.createTempFile(workdir, ".cc-pocket-image-${index + 1}-", suffix)
        Files.write(path, Base64.getDecoder().decode(image.base64))
        temporaryImages.add(path)
        path
    }.getOrNull()

    private fun cleanupImages() {
        temporaryImages.forEach { runCatching { Files.deleteIfExists(it) } }
        temporaryImages.clear()
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray
}

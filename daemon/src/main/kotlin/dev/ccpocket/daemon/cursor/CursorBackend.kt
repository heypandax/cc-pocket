package dev.ccpocket.daemon.cursor

import dev.ccpocket.daemon.agent.AgentBackend
import dev.ccpocket.daemon.agent.AgentEvent
import dev.ccpocket.daemon.agent.AgentIo
import dev.ccpocket.daemon.agent.AgentSpec
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.AgentModel
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
import java.util.concurrent.TimeUnit

/** Cursor Agent adapter for `--print --output-format stream-json` (one process per run). */
class CursorBackend(private val cursorBin: String?) : AgentBackend {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var io: AgentIo? = null
    @Volatile private var resolvedExe: Path? = null
    @Volatile private var mode = PermissionMode.DEFAULT
    @Volatile private var model: String? = null
    private val emittedAssistant = LinkedHashSet<String>()
    private var finalText: String? = null

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

    internal fun parseModelLines(lines: List<String>): List<AgentModel> = lines.mapNotNull { line ->
            val separator = line.indexOf(" - ")
            if (separator <= 0) null else AgentModel(line.substring(0, separator).trim(), line.substring(separator + 3).trim())
        }.distinctBy { it.id }

    private fun exe() = resolvedExe ?: CursorLauncher.resolveExecutable(cursorBin).also { resolvedExe = it }
    override fun processBuilder(spec: AgentSpec) = CursorLauncher.processBuilder(exe(), spec)

    override suspend fun attach(io: AgentIo, spec: AgentSpec) {
        this.io = io
        mode = spec.mode
        model = spec.model
        emittedAssistant.clear()
        finalText = null
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
            else -> emptyList()
        }
    }

    private fun assistant(root: JsonObject): List<AgentEvent> {
        val text = root.obj("message")?.arr("content")?.mapNotNull { item ->
            (item as? JsonObject)?.takeIf { it.str("type") == "text" }?.str("text")
        }?.joinToString("").orEmpty()
        if (text.isBlank()) return emptyList()
        finalText = text
        // Cursor currently emits the same completed assistant message twice; stream it once.
        return if (emittedAssistant.add(text)) listOf(AgentEvent.AssistantText(text)) else emptyList()
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
        val text = root.str("result") ?: finalText
        return listOf(AgentEvent.TurnResult(text, u, root.bool("is_error") == true || root.str("subtype") != "success"))
    }

    override suspend fun sendPrompt(text: String, images: List<ImageData>) {
        val suffix = if (images.isEmpty()) "" else "\n\n[CC Pocket: image attachments are not yet supported by the Cursor backend.]"
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

    override suspend fun onProcessEnded(sessionId: String?) = Unit
    override fun transcriptDir(workdir: String): Path = CursorPaths.sessionsRoot()
    override fun listSessions(workdir: String): List<SessionSummary> = CursorSessionScanner.scan(workdir)
    override fun replayHistory(workdir: String, sessionId: String): List<HistoryMessage> = emptyList()
    override fun resumeContextTokens(workdir: String, sessionId: String): Long? = null
    override fun defaultModel(workdir: String): String = "auto"

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray
}

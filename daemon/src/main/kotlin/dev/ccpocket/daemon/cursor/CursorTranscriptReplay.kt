package dev.ccpocket.daemon.cursor

import dev.ccpocket.protocol.ChatRole
import dev.ccpocket.protocol.HistoryMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

/** Replays Cursor's public agent-transcript JSONL (user/assistant text only). */
object CursorTranscriptReplay {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun read(file: Path, maxMessages: Int = 100, maxTextLen: Int = 4000): List<HistoryMessage> {
        if (!file.exists()) return emptyList()
        val out = ArrayList<HistoryMessage>()
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return@forEach
                    val role = when (obj.str("role")) {
                        "user" -> ChatRole.USER
                        "assistant" -> ChatRole.ASSISTANT
                        else -> return@forEach
                    }
                    val content = obj.obj("message")?.get("content") as? JsonArray ?: return@forEach
                    val text = content.mapNotNull { (it as? JsonObject)?.takeIf { part -> part.str("type") == "text" }?.str("text") }
                        .joinToString("").let { if (role == ChatRole.USER) userText(it) else assistantText(it) }
                    if (text.isNotBlank()) out += HistoryMessage(role, text.take(maxTextLen))
                }
            }
        }
        return if (out.size > maxMessages) out.takeLast(maxMessages) else out
    }

    internal fun userText(raw: String): String {
        val tagged = Regex("(?s)<user_query>\\s*(.*?)\\s*</user_query>").find(raw)?.groupValues?.get(1)
        return (tagged ?: raw.replace(Regex("(?s)<timestamp>.*?</timestamp>\\s*"), "")).trim()
    }

    internal fun assistantText(raw: String): String = raw.replace(Regex("\\s*\\[REDACTED]\\s*$"), "").trim()

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
}

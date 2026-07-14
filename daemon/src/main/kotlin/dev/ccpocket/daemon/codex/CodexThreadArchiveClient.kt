package dev.ccpocket.daemon.codex

import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.SessionSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Small, short-lived client for official Codex thread archive APIs. */
object CodexThreadArchiveClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun list(workdir: String): List<SessionSummary> = withServer { writer, reader ->
        val params = buildJsonObject {
            put("cwd", workdir)
            put("archived", true)
            put("limit", 200)
            put("sortKey", "updated_at")
            put("sortDirection", "desc")
        }
        request(writer, 2, "thread/list", params)
        val response = readResponse(reader, 2)
        response.rpcError()?.let { error(it) }
        parseList(response?.obj("result"), workdir)
    }

    /** Returns null on success, otherwise a safe error string for PocketError. */
    fun setArchived(threadId: String, archived: Boolean): String? = runCatching {
        withServer { writer, reader ->
            val method = if (archived) "thread/archive" else "thread/unarchive"
            request(writer, 2, method, buildJsonObject { put("threadId", threadId) })
            readResponse(reader, 2).rpcError()?.let { error(it) }
        }
    }.exceptionOrNull()?.message

    internal fun parseList(result: JsonObject?, workdir: String): List<SessionSummary> {
        val data = result?.get("data") as? JsonArray ?: return emptyList()
        return data.mapNotNull { (it as? JsonObject)?.toSummary(workdir) }
    }

    private fun JsonObject.toSummary(expectedCwd: String): SessionSummary? {
        val id = str("id") ?: return null
        val cwd = str("cwd") ?: return null
        if (dev.ccpocket.daemon.disk.ProjectPaths.normCwd(cwd) != dev.ccpocket.daemon.disk.ProjectPaths.normCwd(expectedCwd)) return null
        val preview = str("preview").orEmpty()
        val title = str("name")?.takeIf { it.isNotBlank() }
            ?: preview.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60)
            ?: id
        return SessionSummary(
            sessionId = id,
            title = title,
            firstPrompt = preview,
            messageCount = 0,
            cwd = cwd,
            lastModified = ((get("updatedAt") as? JsonPrimitive)?.longOrNull ?: 0L) * 1000,
            version = str("cliVersion"),
            agent = AgentKind.CODEX,
        )
    }

    private fun <T> withServer(block: (java.io.BufferedWriter, BufferedReader) -> T): T {
        val process = ProcessBuilder(CodexLauncher.resolveExecutable().toString(), "app-server").start()
        return try {
            val writer = process.outputStream.bufferedWriter()
            val reader = process.inputStream.bufferedReader()
            request(writer, 1, "initialize", buildJsonObject {
                put("clientInfo", buildJsonObject { put("name", "cc-pocket-archive"); put("version", "1.0") })
                put("capabilities", buildJsonObject { put("experimentalApi", false) })
            })
            readResponse(reader, 1).rpcError()?.let { error(it) }
            writer.write("""{"method":"initialized","params":null}"""); writer.newLine(); writer.flush()
            block(writer, reader)
        } finally {
            runCatching { process.outputStream.close() }
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
    }

    private fun request(writer: java.io.BufferedWriter, id: Long, method: String, params: JsonObject) {
        writer.write(json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("id", id); put("method", method); put("params", params)
        }))
        writer.newLine(); writer.flush()
    }

    private fun readResponse(reader: BufferedReader, id: Long): JsonObject? =
        CompletableFuture.supplyAsync {
            while (true) {
                val line = reader.readLine() ?: return@supplyAsync null
                val obj = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: continue
                if ((obj["id"] as? JsonPrimitive)?.longOrNull == id) return@supplyAsync obj
            }
            @Suppress("UNREACHABLE_CODE") null
        }.get(8, TimeUnit.SECONDS)

    private fun JsonObject?.rpcError(): String? = this?.obj("error")?.str("message")
    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
}

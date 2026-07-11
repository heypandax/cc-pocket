package dev.ccpocket.daemon.cursor

import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.SessionSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files

/** Lightweight Cursor history index: each ACP session has a stable id plus meta.json cwd/title. */
object CursorSessionScanner {
    private val json = Json { ignoreUnknownKeys = true }

    fun scan(workdir: String): List<SessionSummary> {
        val root = CursorPaths.sessionsRoot()
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { dirs ->
            dirs.filter(Files::isDirectory).map { dir ->
                runCatching {
                    val metaFile = dir.resolve("meta.json")
                    val meta = json.parseToJsonElement(Files.readString(metaFile)) as JsonObject
                    val cwd = meta["cwd"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                    if (normalize(cwd) != normalize(workdir)) return@runCatching null
                    val title = meta["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Cursor session"
                    SessionSummary(
                        sessionId = dir.fileName.toString(), title = title, firstPrompt = title,
                        messageCount = 0, cwd = cwd, lastModified = Files.getLastModifiedTime(metaFile).toMillis(),
                        agent = AgentKind.CURSOR,
                    )
                }.getOrNull()
            }.filter { it != null }.map { it!! }.toList().sortedByDescending { it.lastModified }
        }
    }

    fun cwdsByNewest(): Map<String, Long> {
        val root = CursorPaths.sessionsRoot()
        if (!Files.isDirectory(root)) return emptyMap()
        val out = mutableMapOf<String, Long>()
        Files.list(root).use { dirs ->
            dirs.filter(Files::isDirectory).forEach { dir ->
                runCatching {
                    val metaFile = dir.resolve("meta.json")
                    val meta = json.parseToJsonElement(Files.readString(metaFile)) as JsonObject
                    val cwd = meta["cwd"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
                    val mtime = Files.getLastModifiedTime(metaFile).toMillis()
                    if (mtime > (out[cwd] ?: 0L)) out[cwd] = mtime
                }
            }
        }
        return out
    }

    private fun normalize(path: String) = path.replace('\\', '/').trimEnd('/')
}

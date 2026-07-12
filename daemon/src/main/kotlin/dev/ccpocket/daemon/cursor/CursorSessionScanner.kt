package dev.ccpocket.daemon.cursor

import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.SessionSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cursor history index across BOTH stores:
 * - acp-sessions/<id>/meta.json — sessions created over ACP (older cursor-agent versions);
 * - chats/<md5(cwd)>/<id>/meta.json — native chats (IDE/CLI), and where NEW cursor-agent versions
 *   journal every `-p` run too, so without this store even cc-pocket's own sessions vanish.
 * Both resume through `cursor-agent --resume <id>` and share the public agent-transcript JSONL.
 */
object CursorSessionScanner {
    private val json = Json { ignoreUnknownKeys = true }
    private const val FALLBACK_TITLE = "Cursor session"

    fun scan(
        workdir: String,
        acpRoot: Path = CursorPaths.sessionsRoot(),
        chatsRoot: Path = CursorPaths.chatsRoot(),
        projectsRoot: Path = CursorPaths.projectsRoot(),
    ): List<SessionSummary> = scanAll(workdir, acpRoot, chatsRoot, projectsRoot)

    /** EVERY Cursor session across both stores; [workdir] null = no cwd filter (one pass for the
     *  directory listing, which previously re-walked both stores once per project dir). */
    fun scanAll(
        workdir: String? = null,
        acpRoot: Path = CursorPaths.sessionsRoot(),
        chatsRoot: Path = CursorPaths.chatsRoot(),
        projectsRoot: Path = CursorPaths.projectsRoot(),
    ): List<SessionSummary> {
        val rows = scanAcp(workdir, acpRoot) + scanChats(workdir, chatsRoot, projectsRoot)
        // one session can exist in both stores (new CLIs journal ACP runs as chats too): keep the freshest
        // row per id, but don't let it downgrade a real title to the placeholder
        return rows.groupBy { it.sessionId }.values.map { same ->
            val newest = same.maxBy { it.lastModified }
            val titled = same.sortedByDescending { it.lastModified }.firstOrNull { it.title != FALLBACK_TITLE }
            if (titled == null || titled === newest) newest else newest.copy(title = titled.title, firstPrompt = titled.firstPrompt)
        }.sortedByDescending { it.lastModified }
    }

    private fun scanAcp(workdir: String?, root: Path): List<SessionSummary> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { dirs ->
            dirs.filter(Files::isDirectory).map { dir ->
                runCatching {
                    val metaFile = dir.resolve("meta.json")
                    val meta = json.parseToJsonElement(Files.readString(metaFile)) as JsonObject
                    val cwd = meta.str("cwd") ?: return@runCatching null
                    if (workdir != null && normalize(cwd) != normalize(workdir)) return@runCatching null
                    val title = meta.str("title")?.takeIf { it.isNotBlank() } ?: FALLBACK_TITLE
                    SessionSummary(
                        sessionId = dir.fileName.toString(), title = title, firstPrompt = title,
                        messageCount = 0, cwd = cwd, lastModified = Files.getLastModifiedTime(metaFile).toMillis(),
                        agent = AgentKind.CURSOR,
                    )
                }.getOrNull()
            }.filter { it != null }.map { it!! }.toList()
        }
    }

    private fun scanChats(workdir: String?, chatsRoot: Path, projectsRoot: Path): List<SessionSummary> =
        chatMetas(chatsRoot).mapNotNull { (id, meta, metaFile) ->
            val cwd = meta.str("cwd") ?: return@mapNotNull null
            if (workdir != null && normalize(cwd) != normalize(workdir)) return@mapNotNull null
            // the chat meta usually has no title; the public transcript's first user prompt is the
            // same source Claude/Codex summaries fall back to
            val metaTitle = meta.str("title")?.takeIf { it.isNotBlank() }
            val prompt = metaTitle ?: CursorPaths.transcript(id, projectsRoot)?.let(CursorTranscriptReplay::firstUserPrompt)
            val title = metaTitle
                ?: prompt?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(60)
                ?: FALLBACK_TITLE
            SessionSummary(
                sessionId = id, title = title, firstPrompt = prompt ?: title,
                messageCount = 0, cwd = cwd, lastModified = chatMtime(meta, metaFile),
                agent = AgentKind.CURSOR,
            )
        }

    fun cwdsByNewest(
        acpRoot: Path = CursorPaths.sessionsRoot(),
        chatsRoot: Path = CursorPaths.chatsRoot(),
    ): Map<String, Long> {
        val out = mutableMapOf<String, Long>()
        fun note(cwd: String, mtime: Long) { if (mtime > (out[cwd] ?: 0L)) out[cwd] = mtime }
        if (Files.isDirectory(acpRoot)) {
            Files.list(acpRoot).use { dirs ->
                dirs.filter(Files::isDirectory).forEach { dir ->
                    runCatching {
                        val metaFile = dir.resolve("meta.json")
                        val meta = json.parseToJsonElement(Files.readString(metaFile)) as JsonObject
                        val cwd = meta.str("cwd") ?: return@runCatching
                        note(cwd, Files.getLastModifiedTime(metaFile).toMillis())
                    }
                }
            }
        }
        chatMetas(chatsRoot).forEach { (_, meta, metaFile) ->
            meta.str("cwd")?.let { note(it, chatMtime(meta, metaFile)) }
        }
        return out
    }

    /** All real, non-empty top-level chats under chats/<hash>/<chatId>/meta.json. */
    private fun chatMetas(chatsRoot: Path): List<Triple<String, JsonObject, Path>> {
        if (!Files.isDirectory(chatsRoot)) return emptyList()
        val out = ArrayList<Triple<String, JsonObject, Path>>()
        Files.list(chatsRoot).use { hashes ->
            hashes.filter(Files::isDirectory).forEach { hashDir ->
                Files.list(hashDir).use { chats ->
                    chats.filter(Files::isDirectory).forEach { dir ->
                        runCatching {
                            val metaFile = dir.resolve("meta.json")
                            val meta = json.parseToJsonElement(Files.readString(metaFile)) as JsonObject
                            if (meta.bool("isSubagent") == true) return@runCatching // internal Task runs, no cwd of their own
                            if (meta.bool("hasConversation") != true) return@runCatching // opened-then-abandoned shells
                            out += Triple(dir.fileName.toString(), meta, metaFile)
                        }
                    }
                }
            }
        }
        return out
    }

    // updatedAtMs tracks the conversation; the meta file's own mtime is a closest-effort fallback
    private fun chatMtime(meta: JsonObject, metaFile: Path): Long =
        meta.long("updatedAtMs") ?: runCatching { Files.getLastModifiedTime(metaFile).toMillis() }.getOrDefault(0L)

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

    private fun normalize(path: String) = path.replace('\\', '/').trimEnd('/')
}

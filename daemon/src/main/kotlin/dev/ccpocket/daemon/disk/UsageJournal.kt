package dev.ccpocket.daemon.disk

import dev.ccpocket.daemon.identity.Identity
import dev.ccpocket.daemon.util.logger
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.TokenUsage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Per-turn usage journal for backends whose CLIs leave NO usage records on disk. Claude transcripts
 * and Codex rollouts both carry per-turn token counts that [UsageService] reads back; Cursor's chat
 * store (store.db) records none — so without this journal every Cursor turn simply vanished from the
 * Settings usage screen. The daemon appends one JSONL line per completed turn (`~/.cc-pocket/
 * usage-journal.jsonl`), and [UsageService.aggregate] merges it with the on-disk scans.
 */
object UsageJournal {
    private val log = logger("UsageJournal")
    private val json = Json { ignoreUnknownKeys = true }

    // append-heavy, read-rarely: prune by entry count (not age) so the file stays bounded even
    // under heavy use; 20k turns comfortably covers the 90-day usage window the phone can ask for
    private const val MAX_LINES = 20_000
    private const val KEEP_LINES = 10_000

    @Serializable
    data class Entry(
        val ts: Long,
        val agent: AgentKind = AgentKind.CURSOR,
        val model: String? = null,
        val input: Long = 0,
        val output: Long = 0,
        val cacheRead: Long = 0,
        val cacheWrite: Long = 0,
    ) {
        val total: Long get() = input + output + cacheRead + cacheWrite
    }

    fun defaultFile(): File = File(Identity.defaultPath().parentFile, "usage-journal.jsonl")

    /** Append one completed turn. Never throws — usage accounting must not break a turn. */
    @Synchronized
    fun note(agent: AgentKind, model: String?, usage: TokenUsage, ts: Long = System.currentTimeMillis(), file: File = defaultFile()) {
        runCatching {
            val entry = Entry(
                ts = ts, agent = agent, model = model,
                input = usage.inputTokens, output = usage.outputTokens,
                cacheRead = usage.cacheReadInputTokens ?: 0, cacheWrite = usage.cacheCreationInputTokens ?: 0,
            )
            if (entry.total <= 0) return
            file.parentFile?.mkdirs()
            file.appendText(json.encodeToString(entry) + "\n")
            val lines = file.readLines()
            if (lines.size > MAX_LINES) file.writeText(lines.takeLast(KEEP_LINES).joinToString("\n") + "\n")
        }.onFailure { log.warn("journal append failed: ${it.message}") }
    }

    /** Every journaled turn (unparseable lines skipped). */
    @Synchronized
    fun read(file: File = defaultFile()): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                line.trim().takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString<Entry>(it) }.getOrNull() }
            }
        }.getOrDefault(emptyList())
    }
}

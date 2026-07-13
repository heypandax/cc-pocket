package dev.ccpocket.daemon.agent

import dev.ccpocket.protocol.AgencyAgent
import dev.ccpocket.protocol.AgencyAgents
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** Versioned Chinese Agency Agents catalog + verified on-demand prompt cache for CC Pocket. */
object AgencyAgentService {
    private const val MANIFEST = "https://raw.githubusercontent.com/ac54u-mobile/agency-agents/main/integrations/cc-pocket/dist/agents.zh-CN.json"
    private const val MAX_SELECTED = 3
    private const val MAX_PROMPT_CHARS = 60_000
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private val prompts = ConcurrentHashMap<String, String>()
    @Volatile private var catalog: Catalog? = null
    @Volatile private var fetchedAt = 0L

    @Serializable private data class Catalog(val catalogVersion: String? = null, val agents: List<Entry> = emptyList())
    @Serializable private data class Entry(
        val id: String, val nameZh: String, val summaryZh: String, val categoryZh: String,
        val emoji: String? = null, val rawUrl: String, val sha256: String,
    )

    fun list(): AgencyAgents = runCatching {
        val c = loadCatalog()
        AgencyAgents(c.agents.map { AgencyAgent(it.id, it.nameZh, it.summaryZh, it.categoryZh, it.emoji) }, c.catalogVersion)
    }.getOrElse { AgencyAgents(error = "中文 Agent 清单加载失败：${it.message}") }

    fun expand(text: String, ids: List<String>): String {
        if (ids.isEmpty()) return text
        val c = runCatching { loadCatalog() }.getOrNull() ?: return text
        val wanted = ids.distinct().take(MAX_SELECTED).mapNotNull { id -> c.agents.firstOrNull { it.id == id } }
        if (wanted.isEmpty()) return text
        val profiles = wanted.mapNotNull { entry -> loadPrompt(entry)?.let { entry to it } }
        if (profiles.isEmpty()) return text
        return buildString {
            append("<cc-pocket-agency-agents>\n")
            append("Apply the following selected specialist profiles to this user request. When multiple profiles are selected, combine their expertise without inventing a separate user request.\n")
            profiles.forEach { (entry, prompt) ->
                append("\n<agent id=\"").append(entry.id).append("\" name=\"").append(entry.nameZh).append("\">\n")
                append(prompt.take(MAX_PROMPT_CHARS)).append("\n</agent>\n")
            }
            append("</cc-pocket-agency-agents>\n\n<user-request>\n")
            append(text)
            append("\n</user-request>")
        }
    }

    @Synchronized private fun loadCatalog(): Catalog {
        val now = System.currentTimeMillis()
        catalog?.takeIf { now - fetchedAt < 10 * 60_000 }?.let { return it }
        val body = get(MANIFEST)
        return json.decodeFromString<Catalog>(body).also { catalog = it; fetchedAt = now }
    }

    private fun loadPrompt(entry: Entry): String? = prompts[entry.id] ?: runCatching {
        val body = get(entry.rawUrl)
        val actual = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(actual == entry.sha256) { "agent checksum mismatch: ${entry.id}" }
        prompts.putIfAbsent(entry.id, body) ?: body
    }.getOrNull()

    private fun get(url: String): String {
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() == 200) { "HTTP ${res.statusCode()}" }
        return res.body()
    }
}

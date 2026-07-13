package dev.ccpocket.daemon.claude

import dev.ccpocket.protocol.ClaudeLimitWindow
import dev.ccpocket.protocol.ClaudeLimits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Reads the signed-in Claude account's current limits from the OAuth usage endpoint — the same data
 * Claude Code's own /usage panel renders (verified against claude 2.1.x: `five_hour` / `seven_day` /
 * `seven_day_opus` blocks, each `{utilization: percent, resets_at: ISO-8601}`).
 *
 * The access token is READ, never refreshed: OAuth refresh uses token ROTATION and racing the CLI's
 * own refresh is exactly the logout bug credential isolation (issue #69) exists to prevent. A stale
 * token just means null here (the card shows its "unavailable" pane) until the next claude turn
 * refreshes the store. Token source chain: the daemon's isolated store → the default `~/.claude`
 * file store (Linux/Windows) → the macOS Keychain item (the DEFAULT store's login — normally the
 * same account; the isolated store's macOS Keychain naming is unverified, so it isn't probed).
 */
object ClaudeRateLimitsClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build() }
    @Volatile private var cached: ClaudeLimits? = null
    @Volatile private var cachedAt = 0L

    @Synchronized
    fun read(): ClaudeLimits? {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 8_000) return cached
        val fresh = runCatching { query(now) }.getOrNull()
        if (fresh != null) { cached = fresh; cachedAt = now }
        return fresh ?: cached?.takeIf { now - cachedAt < 60_000 }
    }

    private fun query(now: Long): ClaudeLimits? {
        val creds = credentials() ?: return null
        val request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/api/oauth/usage"))
            .header("Authorization", "Bearer ${creds.accessToken}")
            .header("anthropic-beta", "oauth-2025-04-20")
            .timeout(Duration.ofSeconds(6))
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        val obj = json.parseToJsonElement(response.body()) as? JsonObject ?: return null
        return parse(obj, creds.plan, now)
    }

    /** Windows are fixed-width by definition: `five_hour` = 300 min, the weekly blocks = 7×24×60. */
    internal fun parse(obj: JsonObject, plan: String?, now: Long): ClaudeLimits? {
        val session = obj.obj("five_hour")?.window(300)
        val weekly = obj.obj("seven_day")?.window(7 * 24 * 60)
        val weeklyOpus = obj.obj("seven_day_opus")?.window(7 * 24 * 60)
        if (session == null && weekly == null && weeklyOpus == null) return null
        return ClaudeLimits(planType = plan, session = session, weekly = weekly, weeklyOpus = weeklyOpus, capturedAt = now)
    }

    private fun JsonObject.window(windowMinutes: Int): ClaudeLimitWindow? {
        val used = (get("utilization") as? JsonPrimitive)?.doubleOrNull ?: return null
        val resets = str("resets_at")?.let { runCatching { OffsetDateTime.parse(it).toEpochSecond() }.getOrNull() } ?: return null
        return ClaudeLimitWindow(usedPercent = used, windowMinutes = windowMinutes, resetsAt = resets)
    }

    internal class Credentials(val accessToken: String, val plan: String?)

    private fun credentials(): Credentials? {
        for (file in listOf(
            File(ClaudeHome.defaultHome(), ".credentials.json"),
            File(System.getProperty("user.home"), ".claude/.credentials.json"),
        )) {
            runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()?.let { parseCredentials(it) }?.let { return it }
        }
        return keychainJson()?.let { parseCredentials(it) }
    }

    /** `{"claudeAiOauth":{accessToken, expiresAt(ms), subscriptionType, …}}`; an expired token is skipped
     *  (the endpoint would 401) so the chain can fall through to a store the CLI refreshed more recently. */
    internal fun parseCredentials(raw: String): Credentials? {
        val oauth = (runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject)?.obj("claudeAiOauth") ?: return null
        val token = oauth.str("accessToken")?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = (oauth["expiresAt"] as? JsonPrimitive)?.longOrNull
        if (expiresAt != null && expiresAt < System.currentTimeMillis() + 30_000) return null
        return Credentials(token, oauth.str("subscriptionType"))
    }

    private fun keychainJson(): String? {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return null
        return runCatching {
            val proc = ProcessBuilder("security", "find-generic-password", "-s", "Claude Code-credentials", "-w").start()
            proc.outputStream.close()
            if (!proc.waitFor(4, TimeUnit.SECONDS)) { proc.destroyForcibly(); return null }
            if (proc.exitValue() != 0) return null
            proc.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun JsonObject.obj(k: String) = get(k) as? JsonObject
    private fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.contentOrNull
}

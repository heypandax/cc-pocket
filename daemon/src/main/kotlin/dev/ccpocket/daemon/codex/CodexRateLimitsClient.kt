package dev.ccpocket.daemon.codex

import dev.ccpocket.protocol.CodexCredits
import dev.ccpocket.protocol.CodexAccountUsage
import dev.ccpocket.protocol.CodexAccountUsageDay
import dev.ccpocket.protocol.CodexAccountUsageSummary
import dev.ccpocket.protocol.CodexLimitWindow
import dev.ccpocket.protocol.CodexLimits
import dev.ccpocket.protocol.CodexLimitResetResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Reads the authenticated account's current limits from Codex app-server instead of stale rollout files. */
object CodexRateLimitsClient {
    data class AccountSnapshot(val limits: CodexLimits?, val usage: CodexAccountUsage?)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var cached: CodexLimits? = null
    @Volatile private var cachedUsage: CodexAccountUsage? = null
    @Volatile private var cachedAt = 0L

    fun read(): CodexLimits? = readAccountSnapshot().limits

    /** Reads limits and official account totals in one short-lived app-server connection. */
    @Synchronized
    fun readAccountSnapshot(): AccountSnapshot {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 8_000) return AccountSnapshot(cached, cachedUsage)
        val fresh = runCatching { query(now) }.getOrNull()
        if (fresh != null) {
            if (fresh.limits != null) cached = fresh.limits
            if (fresh.usage != null) cachedUsage = fresh.usage
            if (fresh.limits != null || fresh.usage != null) cachedAt = now
        }
        val usable = now - cachedAt < 60_000
        return AccountSnapshot(if (usable) cached else null, if (usable) cachedUsage else null)
    }

    private fun query(now: Long): AccountSnapshot {
        val exe = CodexLauncher.resolveExecutable().toString()
        val process = ProcessBuilder(exe, "app-server").start()
        return try {
            val writer = process.outputStream.bufferedWriter()
            val reader = process.inputStream.bufferedReader()
            writer.write("""{"id":1,"method":"initialize","params":{"clientInfo":{"name":"cc-pocket-usage","version":"1.0"},"capabilities":{"experimentalApi":true}}}""")
            writer.newLine(); writer.flush()
            readResponse(reader, 1)
            writer.write("""{"method":"initialized","params":null}"""); writer.newLine()
            writer.write("""{"id":2,"method":"account/rateLimits/read","params":null}""")
            writer.newLine(); writer.flush()
            val limits = (readResponse(reader, 2)?.get("result") as? JsonObject)?.let { parseResult(it, now) }
            writer.write("""{"id":3,"method":"account/usage/read","params":null}""")
            writer.newLine(); writer.flush()
            val usage = (readResponse(reader, 3)?.get("result") as? JsonObject)?.let { parseUsage(it, now) }
            AccountSnapshot(limits, usage)
        } finally {
            runCatching { process.outputStream.close() }
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
    }

    /** Spend one official reset credit. Callers must obtain an explicit user confirmation first. */
    @Synchronized
    fun consume(idempotencyKey: String): CodexLimitResetResult {
        val exe = CodexLauncher.resolveExecutable().toString()
        val process = ProcessBuilder(exe, "app-server").start()
        return try {
            val writer = process.outputStream.bufferedWriter()
            val reader = process.inputStream.bufferedReader()
            writer.write("""{"id":1,"method":"initialize","params":{"clientInfo":{"name":"cc-pocket-usage","version":"1.0"},"capabilities":{"experimentalApi":true}}}""")
            writer.newLine(); writer.flush()
            readResponse(reader, 1)
            writer.write("""{"method":"initialized","params":null}"""); writer.newLine()
            writer.write("""{"id":2,"method":"account/rateLimitResetCredit/consume","params":{"idempotencyKey":"$idempotencyKey"}}""")
            writer.newLine(); writer.flush()
            val consumed = readResponse(reader, 2) ?: return CodexLimitResetResult("error", error = "no response")
            val rpcError = consumed.obj("error")
            if (rpcError != null) return CodexLimitResetResult("error", error = rpcError.str("message") ?: "reset failed")
            val outcome = consumed.obj("result")?.str("outcome") ?: return CodexLimitResetResult("error", error = "missing outcome")

            writer.write("""{"id":3,"method":"account/rateLimits/read","params":null}""")
            writer.newLine(); writer.flush()
            val refreshed = readResponse(reader, 3)?.obj("result")?.let { parseResult(it, System.currentTimeMillis()) }
            if (refreshed != null) { cached = refreshed; cachedAt = System.currentTimeMillis() } else cachedAt = 0L
            CodexLimitResetResult(outcome, refreshed)
        } catch (t: Throwable) {
            CodexLimitResetResult("error", error = t.message ?: "reset failed")
        } finally {
            runCatching { process.outputStream.close() }
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
    }

    private fun parseResult(result: JsonObject, now: Long): CodexLimits? {
        val byId = result.obj("rateLimitsByLimitId")
        val snapshot = byId?.obj("codex") ?: result.obj("rateLimits") ?: return null
        val resets = result.obj("rateLimitResetCredits")?.get("availableCount")
            ?.let { it as? JsonPrimitive }?.longOrNull
        return parse(snapshot, now, resets)
    }

    private fun parseUsage(result: JsonObject, now: Long): CodexAccountUsage {
        val summary = result.obj("summary")
        val days = (result["dailyUsageBuckets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val day = element as? JsonObject ?: return@mapNotNull null
            val date = day.str("startDate") ?: return@mapNotNull null
            val tokens = (day["tokens"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            CodexAccountUsageDay(date, tokens)
        }
        return CodexAccountUsage(
            summary = CodexAccountUsageSummary(
                lifetimeTokens = summary.long("lifetimeTokens"),
                currentStreakDays = summary.long("currentStreakDays"),
                longestStreakDays = summary.long("longestStreakDays"),
                peakDailyTokens = summary.long("peakDailyTokens"),
                longestRunningTurnSec = summary.long("longestRunningTurnSec"),
            ),
            dailyUsageBuckets = days,
            capturedAt = now,
        )
    }

    private fun readResponse(reader: java.io.BufferedReader, id: Long): JsonObject? =
        CompletableFuture.supplyAsync {
            while (true) {
                val line = reader.readLine() ?: return@supplyAsync null
                val obj = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: continue
                if ((obj["id"] as? JsonPrimitive)?.longOrNull == id) return@supplyAsync obj
            }
            @Suppress("UNREACHABLE_CODE") null
        }.get(8, TimeUnit.SECONDS)

    private fun parse(s: JsonObject, now: Long, resetCreditsAvailable: Long?): CodexLimits {
        val primary = s.obj("primary")?.window()
        val secondary = s.obj("secondary")?.window()
        val credits = s.obj("credits")?.let {
            CodexCredits(it.bool("hasCredits"), it.bool("unlimited"), it.str("balance"))
        }
        return CodexLimits(
            planType = s.str("planType"), primary = primary, secondary = secondary, credits = credits,
            rateLimitReached = s["rateLimitReachedType"] != null && s["rateLimitReachedType"] !is JsonNull,
            capturedAt = now,
            resetCreditsAvailable = resetCreditsAvailable,
        )
    }

    private fun JsonObject.window(): CodexLimitWindow? {
        val used = (get("usedPercent") as? JsonPrimitive)?.doubleOrNull ?: return null
        val mins = (get("windowDurationMins") as? JsonPrimitive)?.longOrNull?.toInt() ?: return null
        val reset = (get("resetsAt") as? JsonPrimitive)?.longOrNull ?: return null
        return CodexLimitWindow(used, mins, reset)
    }
    private fun JsonObject.obj(k: String) = get(k) as? JsonObject
    private fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.long(k: String) = (this?.get(k) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(k: String) = (get(k) as? JsonPrimitive)?.booleanOrNull ?: false
}

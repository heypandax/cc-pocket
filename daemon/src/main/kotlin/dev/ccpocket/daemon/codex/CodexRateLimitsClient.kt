package dev.ccpocket.daemon.codex

import dev.ccpocket.protocol.CodexCredits
import dev.ccpocket.protocol.CodexLimitWindow
import dev.ccpocket.protocol.CodexLimits
import dev.ccpocket.protocol.CodexLimitResetResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var cached: CodexLimits? = null
    @Volatile private var cachedAt = 0L

    @Synchronized
    fun read(): CodexLimits? {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 8_000) return cached
        val fresh = runCatching { query(now) }.getOrNull()
        if (fresh != null) { cached = fresh; cachedAt = now }
        return fresh ?: cached?.takeIf { now - cachedAt < 60_000 }
    }

    private fun query(now: Long): CodexLimits? {
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
            val result = readResponse(reader, 2)?.get("result") as? JsonObject ?: return null
            parseResult(result, now)
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
    private fun JsonObject.bool(k: String) = (get(k) as? JsonPrimitive)?.booleanOrNull ?: false
}

package dev.ccpocket.daemon.voice

import dev.ccpocket.daemon.util.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Manages the xAI voice agent Node.js process lifecycle.
 * Started/stopped based on [DaemonPrefs.voiceAgentEnabled].
 *
 * The voice-agent directory lives inside the cc-pocket project:
 *   <projectRoot>/voice-agent/
 */
class VoiceAgentService(
    private val projectRoot: File,
) {
    private val log = logger("VoiceAgent")

    @Volatile var error: String? = null
        private set

    @Volatile private var process: Process? = null
    private val agentDir = File(projectRoot, "voice-agent")
    private val entryFile = File(agentDir, "dist/index.js")

    val exists: Boolean get() = File(agentDir, "package.json").isFile

    /** True when the process handle we own is alive. Does not see instances started elsewhere. */
    val running: Boolean get() = process?.isAlive == true

    /** Point-in-time state, including instances we don't own (probed via the agent's /health). */
    data class Status(
        val running: Boolean,
        val xaiConnected: Boolean?,
        val phoneNumber: String?,
        val error: String?,
    )

    fun status(): Status {
        val health = probeHealth()
        return Status(
            running = running || health != null,
            xaiConnected = health,
            phoneNumber = envValue("TWILIO_PHONE_NUMBER"),
            error = error,
        )
    }

    fun start(): Boolean {
        if (running) {
            log.info("voice agent already running")
            return true
        }
        if (probeHealth() != null) {
            log.info("voice agent already running (instance not owned by this process)")
            return true
        }
        if (!exists) {
            error = "voice-agent 目录不存在: $agentDir"
            log.warn(error!!)
            return false
        }
        if (!entryFile.isFile) {
            error = "voice-agent 构建产物不存在: $entryFile"
            log.warn(error!!)
            return false
        }

        error = null
        log.info("starting voice agent in $agentDir…")

        return try {
            // run node directly (not `npm start`): destroy() must reach the actual
            // node process, and npm in between leaves an orphan behind
            val logFile = File(agentDir, "voice-agent.log")
            val pb = ProcessBuilder("node", entryFile.absolutePath)
                .directory(agentDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

            // pass the env through so .env is read correctly
            pb.environment().putAll(System.getenv())

            process = pb.start()
            log.info("voice agent started (pid=${process!!.pid()})")
            true
        } catch (e: Exception) {
            error = "failed to start voice agent: ${e.message}"
            log.error(error, e)
            process = null
            false
        }
    }

    fun stop(): Boolean {
        val p = process
        if (p != null && p.isAlive) {
            log.info("stopping voice agent (pid=${p.pid()})…")
            try {
                p.destroy()
                val exited = p.waitFor(5, TimeUnit.SECONDS)
                if (!exited) {
                    p.destroyForcibly()
                    p.waitFor(2, TimeUnit.SECONDS)
                }
            } catch (e: Exception) {
                error = "failed to stop voice agent: ${e.message}"
                log.error(error, e)
                return false
            }
        }
        process = null

        // orphan sweep: an instance started by the CLI, or left over from a daemon
        // restart, still answers /health — we hold no handle, so kill by cmdline
        if (probeHealth() != null) {
            log.info("killing voice agent instance not owned by this process…")
            runCatching {
                ProcessBuilder("pkill", "-f", entryFile.absolutePath).start().waitFor(3, TimeUnit.SECONDS)
            }.onFailure { log.warn("pkill failed: ${it.message}") }
        }
        log.info("voice agent stopped")
        return true
    }

    /** Ensure the agent state matches [enabled]. Returns true if already in that state. */
    fun sync(enabled: Boolean): Boolean {
        return if (enabled) start() else stop()
    }

    /**
     * Probe the agent's /health endpoint.
     * null = no agent answering; true/false = agent up, xAI websocket connected or not.
     */
    fun probeHealth(): Boolean? {
        val port = envValue("PORT")?.toIntOrNull() ?: 3000
        return try {
            val conn = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(body).jsonObject["xaiConnected"]?.jsonPrimitive?.booleanOrNull ?: false
        } catch (_: Exception) {
            null
        }
    }

    private fun envValue(key: String): String? =
        File(agentDir, ".env").takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.trim()?.trim('"', '\'')
            ?.takeIf { it.isNotEmpty() }
}

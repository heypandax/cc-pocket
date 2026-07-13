package dev.ccpocket.daemon.voice

import dev.ccpocket.daemon.util.logger
import java.io.File
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

    @Volatile var running = false
        private set

    @Volatile var error: String? = null
        private set

    private var process: Process? = null
    private val agentDir = File(projectRoot, "voice-agent")

    val exists: Boolean get() = File(agentDir, "package.json").isFile

    fun start(): Boolean {
        if (running) {
            log.info("voice agent already running")
            return true
        }
        if (!exists) {
            error = "voice-agent 目录不存在: $agentDir"
            log.warn(error!!)
            return false
        }

        error = null
        log.info("starting voice agent in $agentDir…")

        return try {
            val pb = ProcessBuilder("npm", "start")
                .directory(agentDir)
                .redirectErrorStream(true)
                .redirectOutput(File(agentDir, "voice-agent.log"))

            // pass the env through so .env is read correctly
            pb.environment().putAll(System.getenv())

            process = pb.start()
            running = true
            log.info("voice agent started (pid=${process!!.pid()})")
            true
        } catch (e: Exception) {
            error = "failed to start voice agent: ${e.message}"
            log.error(error, e)
            running = false
            false
        }
    }

    fun stop(): Boolean {
        if (!running) return true

        val p = process ?: return true
        log.info("stopping voice agent (pid=${p.pid()})…")

        return try {
            p.destroy()
            val exited = p.waitFor(5, TimeUnit.SECONDS)
            if (!exited) {
                p.destroyForcibly()
                p.waitFor(2, TimeUnit.SECONDS)
            }
            running = false
            process = null
            log.info("voice agent stopped")
            true
        } catch (e: Exception) {
            error = "failed to stop voice agent: ${e.message}"
            log.error(error, e)
            false
        }
    }

    /** Ensure the agent state matches [enabled]. Returns true if already in that state. */
    fun sync(enabled: Boolean): Boolean {
        return if (enabled) start() else stop()
    }
}

package dev.ccpocket.daemon

import dev.ccpocket.daemon.agent.AgentBackendFactory
import dev.ccpocket.daemon.claude.AuthService
import dev.ccpocket.daemon.disk.DirectoryService
import dev.ccpocket.daemon.disk.SpawnedSessions
import dev.ccpocket.daemon.server.RequestRouter
import dev.ccpocket.daemon.session.SessionRegistry
import dev.ccpocket.daemon.shell.ShellService
import dev.ccpocket.daemon.transcribe.TranscribeService
import dev.ccpocket.protocol.AgentKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The transport-agnostic core: registry + services + router. Shared by the local server and the relay client.
 *  [backends] maps each agent kind to a factory that builds a fresh per-conversation driver.
 *  [claudeConfigDir] non-null = credential isolation (issue #69): auth commands (and the claude
 *  backends, wired by the caller) operate on the daemon's own CLAUDE_CONFIG_DIR. */
class DaemonCore(
    backends: Map<AgentKind, AgentBackendFactory>,
    val prefs: DaemonPrefs = DaemonPrefs.load(),
    claudeConfigDir: java.nio.file.Path? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val registry = SessionRegistry(scope, backends)

    init {
        // unhide transcripts a crashed previous instance stranded hidden (issue #70) — off the
        // constructor path (file IO over up to 200 journal entries must not delay startup)
        scope.launch(Dispatchers.IO) { runCatching { SpawnedSessions.sweepAtBoot() } }
    }

    val dirs = DirectoryService()
    val transcribe = TranscribeService(scope, registry::workdirOf)
    val shell = ShellService(scope)
    val auth = AuthService(
        scope, registry::busyForAuth, registry::closeIdleForAuth, registry::closeBusyForAuth,
        claudeConfigDir = claudeConfigDir,
    )
    // Resolve voice-agent dir: prefer CC_POCKET_HOME env var (set by systemd unit),
    // fall back to the daemon's own install dir, then cwd.
    private val voiceAgentDir: java.io.File by lazy {
        val envHome = System.getenv("CC_POCKET_HOME")
        if (envHome != null) {
            java.io.File(envHome).absoluteFile
        } else {
            // Try to resolve from the daemon's own JAR location
            val codeSource = dev.ccpocket.daemon.voice.VoiceAgentService::class.java.protectionDomain?.codeSource?.location
            if (codeSource != null && codeSource.protocol == "file") {
                // JAR is in <DEST>/lib/*.jar → go up two levels to <DEST>/
                val libDir = java.io.File(codeSource.toURI()).parentFile  // <DEST>/lib
                libDir?.parentFile?.absoluteFile ?: java.io.File(".").absoluteFile
            } else {
                java.io.File(".").absoluteFile
            }
        }
    }
    val voiceAgent = dev.ccpocket.daemon.voice.VoiceAgentService(projectRoot = voiceAgentDir)
    // auto-start if enabled in prefs
    init {
        if (prefs.voiceAgentEnabled) voiceAgent.start()
    }
    val router = RequestRouter(registry, dirs, transcribe, shell, scope, auth, prefs, voiceAgent)

    suspend fun shutdown() {
        voiceAgent.stop()
        registry.closeAll()
    }
}

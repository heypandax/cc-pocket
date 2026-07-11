package dev.ccpocket.daemon.cursor

import dev.ccpocket.daemon.agent.AgentSpec
import dev.ccpocket.daemon.agent.ExecutableResolver
import dev.ccpocket.protocol.PermissionMode
import java.io.File
import java.nio.file.Path

/** Resolves and launches Cursor Agent's one-shot structured-output mode. */
object CursorLauncher {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val envBin = System.getenv("CC_POCKET_CURSOR_BIN")
    private val exeNames = if (isWindows) listOf("cursor-agent.exe", "cursor-agent.cmd", "cursor-agent.bat", "cursor-agent") else listOf("cursor-agent")
    private val fallbackDirs = buildList {
        val home = System.getProperty("user.home")
        add(home + File.separator + ".local" + File.separator + "bin")
        add(home + File.separator + ".cursor" + File.separator + "bin")
        if (!isWindows) { add("/opt/homebrew/bin"); add("/usr/local/bin"); add("/usr/bin") }
    }

    fun resolveExecutable(explicit: String? = null): Path = ExecutableResolver.resolve(
        explicit, envBin, exeNames, fallbackDirs,
        "cursor-agent executable not found. Install Cursor CLI, or set CC_POCKET_CURSOR_BIN / pass --cursor-bin.",
    )

    fun buildArgs(spec: AgentSpec): List<String> = buildList {
        add("-p")
        add("--trust")
        add("--output-format"); add("stream-json")
        add("--stream-partial-output")
        spec.resumeId?.let { add("--resume"); add(it) }
        spec.model?.takeIf { it.isNotBlank() }?.let { add("--model"); add(it) }
        when (spec.mode) {
            PermissionMode.PLAN -> { add("--mode"); add("plan"); add("--sandbox"); add("enabled") }
            PermissionMode.DEFAULT -> { add("--auto-review"); add("--sandbox"); add("enabled") }
            PermissionMode.ACCEPT_EDITS -> { add("--force"); add("--sandbox"); add("enabled") }
            PermissionMode.BYPASS_PERMISSIONS -> { add("--force"); add("--sandbox"); add("disabled") }
        }
    }

    fun processBuilder(exe: Path, spec: AgentSpec): ProcessBuilder {
        val exeStr = exe.toString()
        val needsShell = isWindows && exeStr.lowercase().let { it.endsWith(".cmd") || it.endsWith(".bat") }
        val argv = buildList {
            if (needsShell) { add(System.getenv("ComSpec") ?: "cmd.exe"); add("/c") }
            add(exeStr)
            addAll(buildArgs(spec))
        }
        return ProcessBuilder(argv).apply {
            directory(spec.workdir.toFile())
            redirectErrorStream(false)
        }
    }
}

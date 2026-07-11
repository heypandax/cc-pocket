package dev.ccpocket.daemon.cursor

import java.nio.file.Path

object CursorPaths {
    fun cursorHome(): Path = System.getenv("CURSOR_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".cursor")
    fun sessionsRoot(): Path = cursorHome().resolve("acp-sessions")
}

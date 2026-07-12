package dev.ccpocket.daemon.cursor

import java.nio.file.Path

object CursorPaths {
    fun cursorHome(): Path = System.getenv("CURSOR_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".cursor")
    fun sessionsRoot(): Path = cursorHome().resolve("acp-sessions")
    /** Native chat store (IDE/CLI chats, and `-p` runs on newer cursor-agent): chats/<md5(cwd)>/<chatId>/. */
    fun chatsRoot(): Path = cursorHome().resolve("chats")
    fun projectsRoot(): Path = cursorHome().resolve("projects")

    fun transcript(sessionId: String, root: Path = projectsRoot()): Path? {
        if (sessionId.contains('/') || sessionId.contains('\\') || sessionId.contains("..")) return null
        if (!java.nio.file.Files.isDirectory(root)) return null
        return java.nio.file.Files.list(root).use { projects ->
            projects.map { it.resolve("agent-transcripts").resolve(sessionId).resolve("$sessionId.jsonl") }
                .filter(java.nio.file.Files::isRegularFile).findFirst().orElse(null)
        }
    }
}

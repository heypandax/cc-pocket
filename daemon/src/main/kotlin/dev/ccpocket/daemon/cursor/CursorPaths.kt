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
        if (!safeId(sessionId)) return null
        if (!java.nio.file.Files.isDirectory(root)) return null
        return java.nio.file.Files.list(root).use { projects ->
            projects.map { it.resolve("agent-transcripts").resolve(sessionId).resolve("$sessionId.jsonl") }
                .filter(java.nio.file.Files::isRegularFile).findFirst().orElse(null)
        }
    }

    /** Remove every on-disk trace of [sessionId]: its acp-sessions dir, its chats/<hash>/<id> dir, and its
     *  public agent-transcript dir. True if anything was deleted. The id is validated against traversal the
     *  same way [transcript] is; each removed path is a DIRECTORY NAMED by the id, never a user-supplied path.
     *  Roots default to the real stores; tests inject temp ones. */
    fun deleteSession(
        sessionId: String,
        acpRoot: Path = sessionsRoot(),
        chatsRoot: Path = chatsRoot(),
        projectsRoot: Path = projectsRoot(),
    ): Boolean {
        if (!safeId(sessionId)) return false
        var removed = false
        fun removeDir(dir: Path) {
            if (!java.nio.file.Files.isDirectory(dir)) return
            runCatching {
                java.nio.file.Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) }
                }
                removed = true
            }
        }
        removeDir(acpRoot.resolve(sessionId))
        if (java.nio.file.Files.isDirectory(chatsRoot)) {
            java.nio.file.Files.list(chatsRoot).use { hashes ->
                hashes.filter(java.nio.file.Files::isDirectory).forEach { removeDir(it.resolve(sessionId)) }
            }
        }
        if (java.nio.file.Files.isDirectory(projectsRoot)) {
            java.nio.file.Files.list(projectsRoot).use { dirs ->
                dirs.forEach { removeDir(it.resolve("agent-transcripts").resolve(sessionId)) }
            }
        }
        return removed
    }

    private fun safeId(sessionId: String): Boolean =
        sessionId.isNotBlank() && !sessionId.contains('/') && !sessionId.contains('\\') && !sessionId.contains("..")
}

package dev.ccpocket.daemon.cursor

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [CursorPaths.deleteSession] removes a session's dirs across all three stores (via injectable roots);
 *  ids with separators are refused before any IO. */
class CursorPathsDeleteTest {

    private fun mkTree(home: Path, id: String) {
        Files.createDirectories(home.resolve("acp-sessions").resolve(id)).resolve("meta.json").also { Files.writeString(it, "{}") }
        Files.createDirectories(home.resolve("chats").resolve("hash1").resolve(id)).resolve("meta.json").also { Files.writeString(it, "{}") }
        Files.createDirectories(home.resolve("projects").resolve("proj").resolve("agent-transcripts").resolve(id))
            .resolve("$id.jsonl").also { Files.writeString(it, "{}") }
    }

    private fun delete(home: Path, id: String) = CursorPaths.deleteSession(
        id,
        acpRoot = home.resolve("acp-sessions"),
        chatsRoot = home.resolve("chats"),
        projectsRoot = home.resolve("projects"),
    )

    @Test
    fun delete_removes_all_three_stores_and_leaves_siblings() {
        val home = Files.createTempDirectory("cursor-home")
        mkTree(home, "dead")
        mkTree(home, "alive")
        assertTrue(delete(home, "dead"))
        assertFalse(Files.exists(home.resolve("acp-sessions").resolve("dead")))
        assertFalse(Files.exists(home.resolve("chats").resolve("hash1").resolve("dead")))
        assertFalse(Files.exists(home.resolve("projects").resolve("proj").resolve("agent-transcripts").resolve("dead")))
        assertTrue(Files.exists(home.resolve("acp-sessions").resolve("alive")))
        assertTrue(Files.exists(home.resolve("chats").resolve("hash1").resolve("alive")))
        assertTrue(Files.exists(home.resolve("projects").resolve("proj").resolve("agent-transcripts").resolve("alive")))
    }

    @Test
    fun traversal_ids_are_refused_without_touching_disk() {
        val home = Files.createTempDirectory("cursor-home")
        mkTree(home, "x")
        assertFalse(delete(home, "../x"))
        assertFalse(delete(home, "a/b"))
        assertFalse(delete(home, ""))
        assertTrue(Files.exists(home.resolve("acp-sessions").resolve("x")))
    }

    @Test
    fun missing_session_reports_false() {
        val home = Files.createTempDirectory("cursor-home")
        assertFalse(delete(home, "never-existed"))
    }
}

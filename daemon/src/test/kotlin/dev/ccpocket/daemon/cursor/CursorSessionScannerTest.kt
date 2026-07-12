package dev.ccpocket.daemon.cursor

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CursorSessionScannerTest {
    private fun acpSession(root: Path, id: String, cwd: String, title: String? = null) {
        val dir = Files.createDirectories(root.resolve(id))
        val titleField = title?.let { ""","title":"$it"""" } ?: ""
        Files.writeString(dir.resolve("meta.json"), """{"cwd":"$cwd"$titleField}""")
    }

    private fun chat(root: Path, hash: String, id: String, body: String) {
        val dir = Files.createDirectories(root.resolve(hash).resolve(id))
        Files.writeString(dir.resolve("meta.json"), body)
    }

    private fun transcript(projectsRoot: Path, id: String, firstUserText: String) {
        val dir = Files.createDirectories(projectsRoot.resolve("proj").resolve("agent-transcripts").resolve(id))
        Files.writeString(
            dir.resolve("$id.jsonl"),
            """{"role":"user","message":{"content":[{"type":"text","text":"<timestamp>t</timestamp>\n<user_query>\n$firstUserText\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"ok"}]}}
""",
        )
    }

    @Test
    fun scan_merges_acp_sessions_with_native_chats() {
        val base = Files.createTempDirectory("cursor-scan")
        val acp = base.resolve("acp-sessions")
        val chats = base.resolve("chats")
        val projects = base.resolve("projects")

        acpSession(acp, "acp-1", "/repo", title = "ACP task")
        chat(chats, "hash1", "chat-1", """{"cwd":"/repo","hasConversation":true,"updatedAtMs":2000}""")
        transcript(projects, "chat-1", "fix the login bug")

        val rows = CursorSessionScanner.scan("/repo", acp, chats, projects)
        assertEquals(setOf("acp-1", "chat-1"), rows.map { it.sessionId }.toSet())
        assertEquals("fix the login bug", rows.first { it.sessionId == "chat-1" }.title)
        assertEquals("ACP task", rows.first { it.sessionId == "acp-1" }.title)
    }

    @Test
    fun scan_skips_subagents_empty_chats_and_other_cwds() {
        val base = Files.createTempDirectory("cursor-scan")
        val acp = base.resolve("acp-sessions")
        val chats = base.resolve("chats")
        val projects = base.resolve("projects")

        chat(chats, "hash1", "real", """{"cwd":"/repo","hasConversation":true,"updatedAtMs":1}""")
        chat(chats, "hash1", "sub", """{"cwd":"/repo","hasConversation":true,"isSubagent":true,"updatedAtMs":2}""")
        chat(chats, "hash1", "empty", """{"cwd":"/repo","hasConversation":false,"updatedAtMs":3}""")
        chat(chats, "hash2", "elsewhere", """{"cwd":"/other","hasConversation":true,"updatedAtMs":4}""")

        assertEquals(listOf("real"), CursorSessionScanner.scan("/repo", acp, chats, projects).map { it.sessionId })
    }

    @Test
    fun scan_dedupes_a_session_present_in_both_stores() {
        val base = Files.createTempDirectory("cursor-scan")
        val acp = base.resolve("acp-sessions")
        val chats = base.resolve("chats")
        val projects = base.resolve("projects")

        acpSession(acp, "s1", "/repo", title = "Named in ACP")
        chat(chats, "hash1", "s1", """{"cwd":"/repo","hasConversation":true,"updatedAtMs":${System.currentTimeMillis() + 60_000}}""")

        val rows = CursorSessionScanner.scan("/repo", acp, chats, projects)
        assertEquals(1, rows.size)
        // freshest mtime (the chat row) wins, but the placeholder must not clobber the real ACP title
        assertEquals("Named in ACP", rows.single().title)
        assertTrue(rows.single().lastModified > System.currentTimeMillis())
    }

    @Test
    fun chat_without_transcript_falls_back_to_placeholder_title() {
        val base = Files.createTempDirectory("cursor-scan")
        chat(base.resolve("chats"), "hash1", "c1", """{"cwd":"/repo","hasConversation":true,"updatedAtMs":9}""")
        val rows = CursorSessionScanner.scan("/repo", base.resolve("acp-sessions"), base.resolve("chats"), base.resolve("projects"))
        assertEquals("Cursor session", rows.single().title)
        assertEquals(9, rows.single().lastModified)
    }

    @Test
    fun cwdsByNewest_covers_both_stores_and_keeps_the_freshest_mtime() {
        val base = Files.createTempDirectory("cursor-scan")
        val acp = base.resolve("acp-sessions")
        val chats = base.resolve("chats")

        acpSession(acp, "a1", "/acp-only")
        chat(chats, "h1", "c1", """{"cwd":"/chat-only","hasConversation":true,"updatedAtMs":123}""")
        chat(chats, "h1", "c2", """{"cwd":"/chat-only","hasConversation":true,"updatedAtMs":456}""")

        val map = CursorSessionScanner.cwdsByNewest(acp, chats)
        assertEquals(456L, map["/chat-only"])
        assertTrue(map.containsKey("/acp-only"))
    }
}

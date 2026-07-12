package dev.ccpocket.daemon.cursor

import dev.ccpocket.protocol.ChatRole
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CursorTranscriptReplayTest {
    @Test
    fun replays_user_and_assistant_text_without_cursor_wrappers() {
        val file = Files.createTempFile("cursor-transcript", ".jsonl")
        Files.writeString(
            file,
            """{"role":"user","message":{"content":[{"type":"text","text":"<timestamp>today</timestamp>\n<user_query>\nhello\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"hi\n\n[REDACTED]"}]}}
{"type":"turn_ended","status":"success"}
""",
        )
        val history = CursorTranscriptReplay.read(file)
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), history.map { it.role })
        assertEquals(listOf("hello", "hi"), history.map { it.text })
        assertEquals("hello", CursorTranscriptReplay.firstUserPrompt(file))
    }
}

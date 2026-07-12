package dev.ccpocket.daemon.cursor

import dev.ccpocket.daemon.agent.AgentEvent
import dev.ccpocket.daemon.agent.AgentIo
import dev.ccpocket.daemon.agent.AgentSpec
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.ImageData
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CursorBackendTest {
    @Test
    fun init_text_tools_and_result_map_to_domain_events() = runBlocking {
        val backend = CursorBackend(null)
        backend.attach(AgentIo({}, {}), AgentSpec(Path.of("/repo")))

        val init = backend.parse("""{"type":"system","subtype":"init","cwd":"/repo","session_id":"cur-1","model":"Auto"}""").single()
        assertEquals(AgentEvent.SessionInit("cur-1", "/repo", "Auto"), init)

        val text = backend.parse("""{"type":"assistant","message":{"content":[{"type":"text","text":"hello"}]},"timestamp_ms":1}""").single()
        assertEquals(AgentEvent.AssistantText("hello"), text)
        assertTrue(backend.parse("""{"type":"assistant","message":{"content":[{"type":"text","text":"hello"}]}}""").isEmpty())

        val extended = backend.parse("""{"type":"assistant","message":{"content":[{"type":"text","text":"hello world"}]}}""").single()
        assertEquals(AgentEvent.AssistantText(" world"), extended)
        assertTrue(backend.parse("""{"type":"assistant","message":{"content":[{"type":"text","text":"hello world\n"}]}}""").isEmpty())

        val tool = backend.parse("""{"type":"tool_call","subtype":"started","call_id":"c1","tool_call":{"readToolCall":{"args":{"path":"README.md"}}}}""").single()
        assertIs<AgentEvent.AssistantToolUse>(tool)
        assertEquals("Read", tool.name)

        val done = backend.parse("""{"type":"result","subtype":"success","is_error":false,"result":"hello","usage":{"inputTokens":10,"outputTokens":2,"cacheReadTokens":3,"cacheWriteTokens":1}}""").single()
        assertIs<AgentEvent.TurnResult>(done)
        assertEquals(16, done.usage?.contextTokens)
    }

    @Test
    fun prompt_is_written_then_stdin_is_closed() = runBlocking {
        val calls = mutableListOf<String>()
        val backend = CursorBackend(null)
        backend.attach(
            AgentIo(writeLine = { calls += "write:$it" }, emit = {}, closeInput = { calls += "close" }),
            AgentSpec(Path.of("/repo")),
        )
        backend.sendPrompt("do it", emptyList())
        assertEquals(listOf("write:do it", "close"), calls)
    }

    @Test
    fun image_is_written_as_a_temporary_workspace_reference_then_cleaned() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("cursor-images")
        val calls = mutableListOf<String>()
        val backend = CursorBackend(null)
        backend.attach(
            AgentIo(writeLine = { calls += it }, emit = {}, closeInput = {}),
            AgentSpec(dir),
        )
        backend.sendPrompt("inspect", listOf(ImageData("image/png", "iVBORw0KGgo=")))
        val name = Regex("@([^\\s]+\\.png)").find(calls.single())!!.groupValues[1]
        val image = dir.resolve(name)
        assertTrue(java.nio.file.Files.exists(image))
        backend.onProcessEnded(null)
        assertFalse(java.nio.file.Files.exists(image))
    }

    @Test
    fun launcher_maps_permission_modes() {
        fun args(mode: PermissionMode) = CursorLauncher.buildArgs(AgentSpec(Path.of("/repo"), mode = mode))
        assertTrue("--auto-review" in args(PermissionMode.DEFAULT))
        assertTrue("plan" in args(PermissionMode.PLAN))
        assertTrue("--force" in args(PermissionMode.ACCEPT_EDITS))
        assertTrue("disabled" in args(PermissionMode.BYPASS_PERMISSIONS))
    }

    @Test
    fun model_catalog_parser_keeps_ids_and_display_names() {
        val models = CursorBackend(null).parseModelLines(
            listOf(
                "Available models", "", "auto - Auto (default)",
                "claude-fable-5-medium - Fable 5 Medium (NO ZDR)",
                "claude-fable-5-high - Fable 5 High (NO ZDR)",
                "claude-fable-5-thinking-high - Fable 5 High Thinking (NO ZDR)",
                "Tip: use --model",
            ),
        )
        assertEquals(listOf("auto", "claude-fable-5-medium"), models.map { it.id })
        assertEquals(3, models.last().variants.size)
        assertEquals("claude-fable-5-thinking-high", models.last().variants.last().id)
    }

    @Test
    fun model_family_parser_keeps_product_names_but_strips_parameters() {
        val backend = CursorBackend(null)
        assertEquals("gpt-5.3-codex", backend.modelFamilyId("gpt-5.3-codex-low-fast"))
        assertEquals("gpt-5.1-codex-max", backend.modelFamilyId("gpt-5.1-codex-max-medium"))
        assertEquals("claude-fable-5", backend.modelFamilyId("claude-fable-5-thinking-xhigh"))
        assertEquals("claude-4.6-sonnet", backend.modelFamilyId("claude-4.6-sonnet-medium-thinking"))
        assertEquals("gemini-3.1-pro", backend.modelFamilyId("gemini-3.1-pro"))
    }

    @Test
    fun cursor_exit_errors_are_actionable() {
        val backend = CursorBackend(null)
        assertTrue("cursor-agent login" in backend.processExitError(1, "Not authenticated; login required"))
        assertTrue("model" in backend.processExitError(1, "invalid model").lowercase())
        assertTrue("permission" in backend.processExitError(1, "Permission denied").lowercase())
    }
}

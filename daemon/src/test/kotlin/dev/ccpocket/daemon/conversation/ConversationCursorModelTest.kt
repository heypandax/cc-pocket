package dev.ccpocket.daemon.conversation

import dev.ccpocket.daemon.agent.AgentBackend
import dev.ccpocket.daemon.agent.AgentEvent
import dev.ccpocket.daemon.agent.AgentIo
import dev.ccpocket.daemon.agent.AgentSpec
import dev.ccpocket.daemon.cursor.CursorBackend
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.Frame
import dev.ccpocket.protocol.HistoryMessage
import dev.ccpocket.protocol.ImageData
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.SessionLive
import dev.ccpocket.protocol.SessionSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cursor's stream-json init often echoes model:"Auto" even when the process was launched with an
 * explicit --model. Conversation must not let that echo permanently overwrite the user's pick.
 */
class ConversationCursorModelTest {
    private fun win() = System.getProperty("os.name").lowercase().contains("win")

    private class CursorScriptedBackend(private val launches: List<() -> ProcessBuilder>) : AgentBackend {
        val specs = CopyOnWriteArrayList<AgentSpec>()
        private val cursor = CursorBackend(null)
        override val kind = AgentKind.CURSOR
        override val exitsAfterTurn = true
        override fun processBuilder(spec: AgentSpec): ProcessBuilder {
            specs.add(spec)
            return launches[minOf(specs.size, launches.size) - 1]()
        }
        override suspend fun attach(io: AgentIo, spec: AgentSpec) {}
        override suspend fun parse(line: String): List<AgentEvent> = cursor.parse(line)
        override suspend fun sendPrompt(text: String, images: List<ImageData>) {}
        override suspend fun interrupt() {}
        override suspend fun respondPermission(
            askId: String, allow: Boolean, remember: Boolean,
            originalInput: JsonObject?, updatedInput: String?, denyMessage: String?,
        ) {}
        override fun applySettings(mode: PermissionMode?, model: String?, effort: String?) = true
        override fun sanitizeModel(model: String?): String? = cursor.sanitizeModel(model)
        override suspend fun onProcessEnded(sessionId: String?) {}
        override fun transcriptDir(workdir: String): Path = Path.of(workdir)
        override fun listSessions(workdir: String): List<SessionSummary> = emptyList()
        override fun replayHistory(workdir: String, sessionId: String): List<HistoryMessage> = emptyList()
        override fun resumeContextTokens(workdir: String, sessionId: String): Long? = null
        override fun defaultModel(workdir: String): String = "auto"
    }

    private fun fixtureProcess(lines: List<String>): () -> ProcessBuilder {
        val f = Files.createTempDirectory("ccp-cursor-model").resolve("stream.jsonl")
            .apply { writeText(lines.joinToString("\n") + "\n") }
        return { ProcessBuilder("sh", "-c", "cat '${f.absolutePathString()}'; sleep 30") }
    }

    private fun withConvo(backend: CursorScriptedBackend, body: suspend (Conversation, () -> List<Frame>) -> Unit) =
        runBlocking {
            val dir = Files.createTempDirectory("ccp-cursor-model-wd")
            val frames = ArrayList<Frame>()
            val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            val convo = Conversation(
                convoId = "cCursorModel", initialWorkdir = dir, initialMode = PermissionMode.DEFAULT,
                initialSink = { f -> synchronized(frames) { frames.add(f) } },
                parentScope = scope, backend = backend,
            )
            try {
                convo.open(resumeId = null, model = null)
                body(convo) { synchronized(frames) { frames.toList() } }
            } finally {
                convo.close()
                scope.cancel()
            }
        }

    private suspend fun await(frames: () -> List<Frame>, cond: (List<Frame>) -> Boolean) {
        withTimeout(10_000) { while (!cond(frames())) delay(20) }
    }

    @Test
    fun cursor_init_auto_does_not_clobber_explicit_model_pick() {
        if (win()) return
        val backend = CursorScriptedBackend(
            listOf(
                fixtureProcess(
                    listOf(
                        """{"type":"system","subtype":"init","session_id":"cur-grok","cwd":"/tmp","model":"Auto"}""",
                        """{"type":"assistant","message":{"content":[{"type":"text","text":"ok"}]}}""",
                        """{"type":"result","subtype":"success","is_error":false,"result":"ok","usage":{"inputTokens":1,"outputTokens":1}}""",
                    ),
                ),
            ),
        )
        withConvo(backend) { convo, frames ->
            convo.switchModel("cursor-grok-4.5-high-fast")
            await(frames) { fs -> fs.filterIsInstance<SessionLive>().any { it.model == "cursor-grok-4.5-high-fast" } }
            convo.sendPrompt("hello", promptId = "p1")
            await(frames) { fs -> fs.filterIsInstance<SessionLive>().any { it.sessionId == "cur-grok" } }
            val liveAfterInit = frames().filterIsInstance<SessionLive>().last { it.sessionId == "cur-grok" }
            assertEquals("cursor-grok-4.5-high-fast", liveAfterInit.model)
            assertEquals("cursor-grok-4.5-high-fast", backend.specs.single().model)
        }
    }

    @Test
    fun cursor_init_auto_is_accepted_when_session_has_no_explicit_pick() {
        if (win()) return
        val backend = CursorScriptedBackend(
            listOf(
                fixtureProcess(
                    listOf(
                        """{"type":"system","subtype":"init","session_id":"cur-auto","cwd":"/tmp","model":"Auto"}""",
                        """{"type":"assistant","message":{"content":[{"type":"text","text":"ok"}]}}""",
                        """{"type":"result","subtype":"success","is_error":false,"result":"ok","usage":{"inputTokens":1,"outputTokens":1}}""",
                    ),
                ),
            ),
        )
        withConvo(backend) { convo, frames ->
            convo.sendPrompt("hello", promptId = "p1")
            await(frames) { fs ->
                fs.filterIsInstance<SessionLive>().any { it.sessionId == "cur-auto" && it.model == "auto" }
            }
            assertEquals(null, backend.specs.single().model)
        }
    }
}

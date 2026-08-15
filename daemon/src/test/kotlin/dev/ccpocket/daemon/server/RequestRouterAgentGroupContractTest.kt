package dev.ccpocket.daemon.server

import dev.ccpocket.daemon.DaemonPrefs
import dev.ccpocket.daemon.agent.AgentBackend
import dev.ccpocket.daemon.agent.AgentBackendFactory
import dev.ccpocket.daemon.agent.AgentEvent
import dev.ccpocket.daemon.agent.AgentIo
import dev.ccpocket.daemon.agent.AgentSpec
import dev.ccpocket.daemon.claude.AuthService
import dev.ccpocket.daemon.conversation.KeyedSink
import dev.ccpocket.daemon.conversation.OutboundSink
import dev.ccpocket.daemon.disk.DirectoryService
import dev.ccpocket.daemon.disk.FileExportService
import dev.ccpocket.daemon.disk.FileInboxService
import dev.ccpocket.daemon.disk.LiveProcesses
import dev.ccpocket.daemon.disk.ProjectPaths
import dev.ccpocket.daemon.disk.SessionGroups
import dev.ccpocket.daemon.presets.PresetService
import dev.ccpocket.daemon.presets.PresetStore
import dev.ccpocket.daemon.session.SessionRegistry
import dev.ccpocket.daemon.shell.ShellService
import dev.ccpocket.daemon.transcribe.TranscribeService
import dev.ccpocket.protocol.AgentGroupDelivery
import dev.ccpocket.protocol.AgentGroupHandoffBrief
import dev.ccpocket.protocol.AgentGroupLaunchProfile
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.ChatRole
import dev.ccpocket.protocol.ConvoHistory
import dev.ccpocket.protocol.Frame
import dev.ccpocket.protocol.HandoffAgentGroup
import dev.ccpocket.protocol.HistoryMessage
import dev.ccpocket.protocol.ImageData
import dev.ccpocket.protocol.ListSessions
import dev.ccpocket.protocol.OpenSession
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.GroupDelete
import dev.ccpocket.protocol.GroupRename
import dev.ccpocket.protocol.PocketError
import dev.ccpocket.protocol.PromptAck
import dev.ccpocket.protocol.RouteAgentGroup
import dev.ccpocket.protocol.SESSION_GROUP_COLLABORATION
import dev.ccpocket.protocol.SessionLive
import dev.ccpocket.protocol.SessionSummary
import dev.ccpocket.protocol.Sessions
import dev.ccpocket.protocol.SwitchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end daemon contracts for issue #232's single-view collaboration routing. */
class RequestRouterAgentGroupContractTest {

    private class RecordingBackend(
        private val summaries: List<SessionSummary>,
        private val prompts: MutableList<String>,
        private val throwOnPrompt: Boolean = false,
    ) : AgentBackend {
        override val kind = AgentKind.CLAUDE
        override fun processBuilder(spec: AgentSpec) = ProcessBuilder("sh", "-c", "sleep 30")
        override suspend fun attach(io: AgentIo, spec: AgentSpec) {}
        override suspend fun parse(line: String): List<AgentEvent> = emptyList()
        override suspend fun sendPrompt(text: String, images: List<ImageData>) {
            if (throwOnPrompt) error("synthetic prompt failure")
            prompts += text
        }
        override suspend fun interrupt() {}
        override suspend fun respondPermission(
            askId: String,
            allow: Boolean,
            remember: Boolean,
            originalInput: JsonObject?,
            updatedInput: String?,
            denyMessage: String?,
        ) {}
        override fun applySettings(mode: PermissionMode?, model: String?, effort: String?) = true
        override suspend fun onProcessEnded(sessionId: String?) {}
        override fun transcriptDir(workdir: String): Path = Path.of(workdir)
        override fun listSessions(workdir: String): List<SessionSummary> = summaries
        override fun replayHistory(workdir: String, sessionId: String): List<HistoryMessage> =
            listOf(HistoryMessage(ChatRole.USER, "history:$sessionId"))
        override fun resumeContextTokens(workdir: String, sessionId: String): Long? = null
    }

    /**
     * SessionGroups has a daemon-global default file. Keep each test away from the developer's real store,
     * and restore user.home even when an assertion fails. JUnit runs daemon tests sequentially by default.
     */
    private class Fixture(
        externalProbe: LiveProcesses.ExternalClaude = LiveProcesses.ExternalClaude.ABSENT,
        throwOnPrompt: Boolean = false,
    ) : AutoCloseable {
        private val oldHome = System.getProperty("user.home")
        val home: Path = Files.createTempDirectory("ccp-agent-group-home")
        val workdir: String = Files.createTempDirectory("ccp-agent-group-wd").toRealPath().toString()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val frames: MutableList<Frame> = CopyOnWriteArrayList()
        val prompts: MutableList<String> = CopyOnWriteArrayList()
        val caps = RequestRouter.ClientCapsHolder().apply { supportsAgentGroups = true }
        val sink: OutboundSink = KeyedSink("dev:phone", OutboundSink { frames += it })

        private val summaries = listOf(
            SessionSummary(
                sessionId = SOURCE_SESSION,
                title = "source",
                firstPrompt = "source",
                messageCount = 1,
                cwd = workdir,
                lastModified = 2,
                agent = AgentKind.CLAUDE,
            ),
            SessionSummary(
                sessionId = TARGET_SESSION,
                title = "target",
                firstPrompt = "target",
                messageCount = 1,
                cwd = workdir,
                lastModified = 1,
                agent = AgentKind.CLAUDE,
            ),
        )

        val registry: SessionRegistry
        val router: RequestRouter
        val groupId: String
        val sourceMemberId: String
        val targetMemberId: String

        init {
            System.setProperty("user.home", home.toString())
            registry = SessionRegistry(
                scope,
                backends = mapOf(
                    AgentKind.CLAUDE to AgentBackendFactory { RecordingBackend(summaries, prompts, throwOnPrompt) },
                ),
                processProbe = { _, _ -> externalProbe },
            )
            val stores = Files.createTempDirectory("ccp-agent-group-stores").toFile()
            router = RequestRouter(
                registry = registry,
                dirs = DirectoryService(),
                transcribe = TranscribeService(scope) { null },
                inbox = FileInboxService { null },
                shell = ShellService(scope),
                exports = FileExportService(scope, { null }),
                scope = scope,
                auth = AuthService(scope, { emptyList() }, { 0 }),
                prefs = DaemonPrefs.load(stores.resolve("prefs.json")),
                presets = PresetService(PresetStore.load(stores.resolve("presets.json")), { emptyList() }, { 0 }),
                scheduler = dev.ccpocket.daemon.schedule.SchedulerService(
                    dev.ccpocket.daemon.schedule.ScheduleStore.load(stores.resolve("schedules.json")),
                    executor = { null },
                ),
            )
            val group = assertNotNull(
                SessionGroups.create(workdir, "Review pair", purpose = SESSION_GROUP_COLLABORATION),
            )
            groupId = group.id
            sourceMemberId = assertNotNull(
                SessionGroups.configureMember(
                    workdir, groupId, SOURCE_SESSION, "planner", "plans",
                    AgentGroupLaunchProfile(agent = AgentKind.CLAUDE),
                ).member,
            ).id
            targetMemberId = assertNotNull(
                SessionGroups.configureMember(
                    workdir, groupId, TARGET_SESSION, "reviewer", "reviews",
                    AgentGroupLaunchProfile(agent = AgentKind.CLAUDE),
                ).member,
            ).id
        }

        suspend fun openSource(): String {
            router.handle(
                OpenSession(workdir, resumeId = SOURCE_SESSION),
                sink,
                caps = caps,
                deviceId = DEVICE,
            )
            return awaitFrame<SessionLive> { it.sessionId == SOURCE_SESSION }.convoId
        }

        suspend inline fun <reified T : Frame> awaitFrame(noinline predicate: (T) -> Boolean = { true }): T =
            withTimeout(5_000) {
                while (true) {
                    frames.filterIsInstance<T>().firstOrNull(predicate)?.let { return@withTimeout it }
                    delay(10)
                }
                error("unreachable")
            }

        suspend fun assertSourceStillStreams(convoId: String) {
            val before = frames.filterIsInstance<SessionLive>().count { it.convoId == convoId }
            router.handle(SwitchMode(convoId, PermissionMode.DEFAULT), sink, caps = caps, deviceId = DEVICE)
            withTimeout(5_000) {
                while (frames.filterIsInstance<SessionLive>().count { it.convoId == convoId } <= before) delay(10)
            }
        }

        fun makeTargetExternallyActive() {
            val transcript = ProjectPaths.dirFor(workdir).resolve("$TARGET_SESSION.jsonl")
            Files.createDirectories(transcript.parent)
            Files.writeString(transcript, "{}\n")
            Files.setLastModifiedTime(transcript, FileTime.fromMillis(System.currentTimeMillis() + 1_000))
        }

        override fun close() {
            val deliveries = frames.filterIsInstance<AgentGroupDelivery>()
            runBlocking {
                deliveries.mapNotNull { it.targetConvoId }.distinct().forEach { registry.close(it, force = true) }
                frames.filterIsInstance<SessionLive>().map { it.convoId }.distinct().forEach { registry.close(it, force = true) }
            }
            scope.cancel()
            System.setProperty("user.home", oldHome)
        }

        companion object {
            const val DEVICE = "phone"
            const val SOURCE_SESSION = "11111111-1111-1111-1111-111111111111"
            const val TARGET_SESSION = "22222222-2222-2222-2222-222222222222"
        }
    }

    @Test
    fun legacy_session_list_hides_collaboration_headers_and_member_metadata() = runBlocking {
        Fixture().use { fx ->
            val legacyFrames = CopyOnWriteArrayList<Frame>()
            fx.router.handle(
                ListSessions(fx.workdir),
                OutboundSink { legacyFrames += it },
                caps = RequestRouter.ClientCapsHolder(),
            )
            val legacy = legacyFrames.single() as Sessions
            assertTrue(legacy.groups.orEmpty().none { it.id == fx.groupId })
            assertTrue(legacy.items.all { it.group == null })
            assertTrue(legacy.items.all { it.memberId == null && it.memberName == null && it.memberRole == null })

            val modernFrames = CopyOnWriteArrayList<Frame>()
            fx.router.handle(ListSessions(fx.workdir), OutboundSink { modernFrames += it }, caps = fx.caps)
            val modern = modernFrames.single() as Sessions
            assertTrue(modern.groups.orEmpty().any { it.id == fx.groupId })
            assertEquals(setOf(fx.sourceMemberId, fx.targetMemberId), modern.items.mapNotNull { it.memberId }.toSet())
        }
    }

    @Test
    fun restricted_caller_is_refused_and_source_view_remains_attached() = runBlocking {
        Fixture().use { fx ->
            val sourceConvo = fx.openSource()
            fx.frames.clear()
            fx.router.handle(
                RouteAgentGroup(
                    "restricted-route", fx.workdir, fx.groupId, sourceConvo,
                    fx.targetMemberId, "review this",
                ),
                fx.sink,
                origin = "bridge",
                caps = fx.caps,
                deviceId = Fixture.DEVICE,
            )
            val refused = fx.awaitFrame<AgentGroupDelivery> { it.requestId == "restricted-route" }
            assertFalse(refused.ok)
            assertEquals("owner_only", refused.errorCode)
            fx.assertSourceStillStreams(sourceConvo)
        }
    }

    @Test
    fun legacy_group_mutations_cannot_bypass_collaboration_owner_only() = runBlocking {
        Fixture().use { fx ->
            fx.router.handle(
                GroupRename(fx.workdir, fx.groupId, "hijacked"),
                fx.sink,
                collabScope = dev.ccpocket.daemon.handoff.CollaboratorScope(
                    deviceId = "collaborator",
                    pathScope = emptyList(),
                    access = dev.ccpocket.protocol.HandoffAccess.REVIEW_READ_ONLY,
                ),
                caps = fx.caps,
            )
            assertEquals("Review pair", SessionGroups.groupsFor(fx.workdir).single { it.id == fx.groupId }.name)
            assertEquals("owner_only", fx.frames.filterIsInstance<PocketError>().last().code)

            fx.router.handle(
                GroupDelete(fx.workdir, fx.groupId),
                fx.sink,
                origin = "bridge",
                caps = fx.caps,
            )
            assertTrue(SessionGroups.groupsFor(fx.workdir).any { it.id == fx.groupId })
            assertEquals("owner_only", fx.frames.filterIsInstance<PocketError>().last().code)
        }
    }

    @Test
    fun route_and_handoff_fail_closed_without_switching_away_from_source() = runBlocking {
        Fixture(LiveProcesses.ExternalClaude.PRESENT).use { fx ->
            val sourceConvo = fx.openSource()
            fx.frames.clear()
            fx.makeTargetExternallyActive()

            fx.router.handle(
                RouteAgentGroup(
                    "external-route", fx.workdir, fx.groupId, sourceConvo,
                    fx.targetMemberId, "review this",
                ),
                fx.sink,
                caps = fx.caps,
                deviceId = Fixture.DEVICE,
            )
            val external = fx.awaitFrame<AgentGroupDelivery> { it.requestId == "external-route" }
            assertFalse(external.ok)
            assertEquals("target_external_active", external.errorCode)
            fx.assertSourceStillStreams(sourceConvo)

            fx.router.handle(
                HandoffAgentGroup(
                    "bad-handoff", fx.workdir, fx.groupId, sourceConvo,
                    fx.sourceMemberId, "missing-member",
                    AgentGroupHandoffBrief(
                        objective = "review",
                        conclusions = listOf("implementation is ready"),
                        constraints = listOf("keep contexts isolated"),
                        doneWhen = listOf("verdict returned"),
                    ),
                ),
                fx.sink,
                caps = fx.caps,
                deviceId = Fixture.DEVICE,
            )
            val badHandoff = fx.awaitFrame<AgentGroupDelivery> { it.requestId == "bad-handoff" }
            assertFalse(badHandoff.ok)
            assertEquals("not_member", badHandoff.errorCode)
            fx.assertSourceStillStreams(sourceConvo)
        }
    }

    @Test
    fun successful_route_rearms_before_retry_replay_without_repeating_the_prompt() = runBlocking {
        if (System.getProperty("os.name").lowercase().contains("win")) return@runBlocking
        Fixture().use { fx ->
            val sourceConvo = fx.openSource()
            fx.frames.clear()
            val request = RouteAgentGroup(
                "route-once", fx.workdir, fx.groupId, sourceConvo,
                fx.targetMemberId, "review exactly once", promptId = "prompt-once",
            )
            fx.router.handle(request, fx.sink, caps = fx.caps, deviceId = Fixture.DEVICE)
            val delivery = fx.awaitFrame<AgentGroupDelivery> { it.requestId == request.requestId && it.ok }
            val targetConvo = assertNotNull(delivery.targetConvoId)
            fx.awaitFrame<SessionLive> { it.convoId == targetConvo }
            fx.awaitFrame<ConvoHistory> { it.convoId == targetConvo }
            fx.awaitFrame<PromptAck> { it.convoId == targetConvo && it.promptId == "prompt-once" }
            withTimeout(5_000) { while (fx.prompts.size < 1) delay(10) }

            val deliveryIndex = fx.frames.indexOfFirst { it is AgentGroupDelivery && it.requestId == request.requestId }
            val liveIndex = fx.frames.indexOfFirst { it is SessionLive && it.convoId == targetConvo }
            val historyIndex = fx.frames.indexOfFirst { it is ConvoHistory && it.convoId == targetConvo }
            assertTrue(deliveryIndex in 0 until liveIndex, "Delivery must arm the client before target SessionLive")
            assertTrue(liveIndex < historyIndex, "target history must follow its SessionLive")

            fx.router.handle(request, fx.sink, caps = fx.caps, deviceId = Fixture.DEVICE)
            withTimeout(5_000) {
                while (
                    fx.frames.filterIsInstance<AgentGroupDelivery>().count { it.requestId == request.requestId } < 2 ||
                    fx.frames.filterIsInstance<SessionLive>().count { it.convoId == targetConvo } < 2 ||
                    fx.frames.filterIsInstance<ConvoHistory>().count { it.convoId == targetConvo } < 2
                ) delay(10)
            }
            delay(100)
            assertEquals(1, fx.prompts.size, "a (deviceId, requestId) retry must not enqueue a second agent turn")
            val deliveries = fx.frames.indices.filter {
                val frame = fx.frames[it]
                frame is AgentGroupDelivery && frame.requestId == request.requestId
            }
            val lives = fx.frames.indices.filter {
                val frame = fx.frames[it]
                frame is SessionLive && frame.convoId == targetConvo
            }
            val histories = fx.frames.indices.filter {
                val frame = fx.frames[it]
                frame is ConvoHistory && frame.convoId == targetConvo
            }
            assertEquals(2, deliveries.size)
            assertEquals(2, lives.size, "a lost receipt retry must rebuild the target view")
            assertEquals(2, histories.size, "the rebuilt view includes the target transcript")
            assertEquals(
                2,
                fx.frames.filterIsInstance<PromptAck>().count { it.promptId == "prompt-once" },
                "a retry must replay the proven PromptAck so the client can release its recovery copy",
            )
            assertTrue(deliveries[1] < lives[1], "retry Delivery must re-arm before its target SessionLive")
            assertTrue(lives[1] < histories[1], "retry history must follow its SessionLive")
            assertNull(fx.frames.filterIsInstance<AgentGroupDelivery>().last().errorCode)

            fx.router.handle(
                request.copy(text = "same id, different operation"),
                fx.sink,
                caps = fx.caps,
                deviceId = Fixture.DEVICE,
            )
            withTimeout(5_000) {
                while (fx.frames.filterIsInstance<AgentGroupDelivery>().count { it.requestId == request.requestId } < 3) delay(10)
            }
            val conflict = fx.frames.filterIsInstance<AgentGroupDelivery>().last()
            assertFalse(conflict.ok)
            assertEquals("request_conflict", conflict.errorCode)
            delay(100)
            assertEquals(1, fx.prompts.size, "a requestId collision must not enqueue another turn")
            assertEquals(2, fx.frames.filterIsInstance<SessionLive>().count { it.convoId == targetConvo })
            assertEquals(2, fx.frames.filterIsInstance<ConvoHistory>().count { it.convoId == targetConvo })
        }
    }

    @Test
    fun post_receipt_prompt_failure_is_correlated_and_never_pins_the_client_transaction() = runBlocking {
        if (System.getProperty("os.name").lowercase().contains("win")) return@runBlocking
        Fixture(throwOnPrompt = true).use { fx ->
            val sourceConvo = fx.openSource()
            fx.frames.clear()
            val request = RouteAgentGroup(
                "route-fails-after-receipt", fx.workdir, fx.groupId, sourceConvo,
                fx.targetMemberId, "review this", promptId = "prompt-fails",
            )

            fx.router.handle(request, fx.sink, caps = fx.caps, deviceId = Fixture.DEVICE)
            val accepted = fx.awaitFrame<AgentGroupDelivery> { it.requestId == request.requestId && it.ok }
            val failed = fx.awaitFrame<AgentGroupDelivery> { it.requestId == request.requestId && !it.ok }

            assertEquals(accepted.targetConvoId, failed.targetConvoId)
            assertEquals("delivery_failed", failed.errorCode)
            assertTrue(fx.frames.filterIsInstance<PromptAck>().none { it.promptId == "prompt-fails" })
            assertTrue(fx.frames.filterIsInstance<PocketError>().none { it.code == "delivery_failed" })
        }
    }

    @Test
    fun self_route_retry_never_closes_the_current_conversation_or_repeats_prompt() = runBlocking {
        if (System.getProperty("os.name").lowercase().contains("win")) return@runBlocking
        Fixture().use { fx ->
            val sourceConvo = fx.openSource()
            fx.frames.clear()
            val request = RouteAgentGroup(
                "self-route", fx.workdir, fx.groupId, sourceConvo,
                fx.sourceMemberId, "continue here", promptId = "self-prompt",
            )
            fx.router.handle(request, fx.sink, caps = fx.caps, deviceId = Fixture.DEVICE)
            fx.awaitFrame<AgentGroupDelivery> { it.requestId == request.requestId && it.ok }
            withTimeout(5_000) { while (fx.prompts.size < 1) delay(10) }

            fx.router.handle(request, fx.sink, caps = fx.caps, deviceId = Fixture.DEVICE)
            withTimeout(5_000) {
                while (fx.frames.filterIsInstance<AgentGroupDelivery>().count { it.requestId == request.requestId } < 2) delay(10)
            }
            assertEquals(1, fx.prompts.size)
            fx.assertSourceStillStreams(sourceConvo)
        }
    }
}

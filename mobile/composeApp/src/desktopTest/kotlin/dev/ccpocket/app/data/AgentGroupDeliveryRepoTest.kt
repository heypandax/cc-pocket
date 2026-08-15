package dev.ccpocket.app.data

import dev.ccpocket.protocol.AGENT_GROUP_DELIVERY_ROUTE
import dev.ccpocket.protocol.AgentGroupDelivery
import dev.ccpocket.protocol.AgentGroupLaunchProfile
import dev.ccpocket.protocol.AgentGroupMember
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.AssistantChunk
import dev.ccpocket.protocol.ConfigureAgentGroupMember
import dev.ccpocket.protocol.ChatRole
import dev.ccpocket.protocol.ConvoHistory
import dev.ccpocket.protocol.Frame
import dev.ccpocket.protocol.HistoryMessage
import dev.ccpocket.protocol.AgentGroupHandoffBrief
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.OpenSession
import dev.ccpocket.protocol.PromptAck
import dev.ccpocket.protocol.RouteAgentGroup
import dev.ccpocket.protocol.SESSION_GROUP_COLLABORATION
import dev.ccpocket.protocol.SessionGroup
import dev.ccpocket.protocol.SessionLive
import dev.ccpocket.protocol.SessionSummary
import dev.ccpocket.protocol.Sessions
import dev.ccpocket.protocol.StreamPiece
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentGroupDeliveryRepoTest {
    private val wd = "/w/project"
    private val source = summary("sid-source", "Source", "member-source")
    private val target = summary("sid-target", "Target", "member-target")
    private val targetProfile = AgentGroupLaunchProfile(
        agent = AgentKind.CODEX,
        model = "gpt-5.6-codex",
        mode = PermissionMode.BYPASS_PERMISSIONS,
        effort = "high",
        serviceTier = "priority",
    )
    private val group = SessionGroup(
        id = "group-1",
        name = "Ship feature",
        order = 0,
        purpose = SESSION_GROUP_COLLABORATION,
        members = listOf(
            AgentGroupMember("member-source", source.sessionId, "Planner", launchProfile = AgentGroupLaunchProfile()),
            AgentGroupMember("member-target", target.sessionId, "Builder", launchProfile = targetProfile),
        ),
    )

    private fun summary(id: String, title: String, memberId: String) = SessionSummary(
        sessionId = id,
        title = title,
        firstPrompt = title,
        messageCount = 1,
        cwd = wd,
        lastModified = 1,
        agent = if (id == "sid-target") AgentKind.CLAUDE else AgentKind.CLAUDE,
        group = "group-1",
        memberId = memberId,
    )

    private class Harness {
        val sent = mutableListOf<Frame>()
        val repo = PocketRepository(CoroutineScope(Dispatchers.Unconfined)).apply {
            onSendForTest = { sent += it }
        }
    }

    private fun Harness.seed(source: SessionSummary, target: SessionSummary, group: SessionGroup) {
        repo.receiveForTest(Sessions(wd, listOf(source, target), groups = listOf(group), agentGroupsSupported = true))
        repo.receiveForTest(SessionLive("convo-source", wd, source.sessionId, executing = false))
    }

    @Test
    fun capabilityAdvertisesAgentGroups() {
        assertTrue(pocketClientCaps().supportsAgentGroups)
    }

    @Test
    fun daemonCapabilityEchoNotLegacyGroupsControlsCollaboration() {
        val h = Harness()
        h.repo.receiveForTest(Sessions(wd, listOf(source, target), groups = listOf(group), agentGroupsSupported = false))
        h.repo.receiveForTest(SessionLive("convo-source", wd, source.sessionId, executing = false))

        assertEquals(null, h.repo.currentAgentGroup())
        assertFalse(h.repo.routeAgentGroup(group.id, "member-target", "must not send"))
        assertTrue(h.sent.none { it is RouteAgentGroup })
    }

    @Test
    fun routeSnapshotsAndConsumesOnlyItsReadyAttachmentsOnSuccess() {
        val h = Harness().also { it.seed(source, target, group) }
        h.repo.pendingImages += PendingImage(41, byteArrayOf(1, 2, 3), ImgState.Ready)
        h.repo.pendingFiles += PendingFile(
            42, "notes.md", 7, ByteArray(0), "text/markdown", FileUpState.Landed,
            path = ".cc-pocket/inbox/notes.md", landedName = "notes.md",
        )
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "review"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        assertEquals(1, request.images.size)
        assertTrue(request.text.contains("@.cc-pocket/inbox/notes.md"))
        assertEquals(1, h.repo.pendingImages.size, "attachments stay editable until the daemon commits")
        assertEquals(1, h.repo.pendingFiles.size)

        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )
        assertEquals(1, h.repo.pendingImages.size, "Delivery alone is not proof that the agent received them")
        assertEquals(1, h.repo.pendingFiles.size)
        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target", executing = true))
        h.repo.receiveForTest(PromptAck("convo-target", assertNotNull(request.promptId)))
        assertTrue(h.repo.pendingImages.isEmpty(), "matching PromptAck commits the snapshotted attachments")
        assertTrue(h.repo.pendingFiles.isEmpty())
    }

    @Test
    fun ordinarySendAndAttachmentRemovalAreBlockedUntilRouteAck() {
        val h = Harness().also { it.seed(source, target, group) }
        h.repo.pendingImages += PendingImage(41, byteArrayOf(1), ImgState.Ready)
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "only once"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )
        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target", executing = true))

        assertFalse(h.repo.sendPrompt("only once"))
        h.repo.removePendingImage(41)
        assertEquals(1, h.repo.pendingImages.size)
        assertTrue(h.sent.none { it is dev.ccpocket.protocol.SendPrompt })

        h.repo.receiveForTest(PromptAck("convo-target", assertNotNull(request.promptId)))
        assertFalse(h.repo.agentGroupDeliveryPending.value)
        assertTrue(h.repo.pendingImages.isEmpty())
    }

    @Test
    fun handoffSuccessDoesNotEmitComposerAcceptedReceipt() {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(
            h.repo.handoffAgentGroup(
                group.id, "member-source", "member-target",
                AgentGroupHandoffBrief("same as composer", listOf("known"), listOf("constraint"), listOf("done")),
            ),
        )
        val request = assertNotNull(h.sent.filterIsInstance<dev.ccpocket.protocol.HandoffAgentGroup>().singleOrNull())
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, dev.ccpocket.protocol.AGENT_GROUP_DELIVERY_HANDOFF, true, group.id,
                "convo-source", "member-target", targetSessionId = "sid-target", targetConvoId = "convo-target",
            ),
        )
        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target", executing = true))
        assertEquals(0, h.repo.agentGroupAcceptedGen.value)
        assertEquals(null, h.repo.agentGroupAcceptedText.value)
    }

    @Test
    fun acceptedDeliveryKeepsExactFrameUntilTargetLiveAndFencesSource() = runBlocking {
        val h = Harness().also { it.seed(source, target, group) }
        h.repo.receiveForTest(
            ConvoHistory("convo-source", listOf(HistoryMessage(ChatRole.USER, "source only"))),
        )
        assertEquals(1, h.repo.messages.size)
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "implement it"))
        val original = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())

        h.sent.clear()
        h.repo.restoreAfterReconnectForTest()
        val replay = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        assertEquals(original, replay, "an uncommitted reconnect reuses the exact frame and requestId")

        h.repo.receiveForTest(
            AgentGroupDelivery(
                requestId = original.requestId,
                kind = AGENT_GROUP_DELIVERY_ROUTE,
                ok = true,
                groupId = group.id,
                fromConvoId = "convo-source",
                targetMemberId = "member-target",
                targetSessionId = "sid-target",
                targetConvoId = "convo-target",
                promptId = original.promptId,
            ),
        )
        assertEquals(0, h.repo.agentGroupAcceptedGen.value, "Delivery must not clear the source payload")
        assertEquals("implement it", h.repo.draftFor("sid-source"))
        h.repo.receiveForTest(AssistantChunk("convo-source", 2, StreamPiece.Text("late source output")))
        assertTrue(h.repo.messages.isEmpty(), "delivery commit must reset source transcript before target history")

        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target", executing = false))
        h.repo.receiveForTest(
            ConvoHistory("convo-target", listOf(HistoryMessage(ChatRole.ASSISTANT, "target only"))),
        )
        assertEquals("sid-target", h.repo.sessionKey.value)
        assertEquals("Target", h.repo.chatTitle.value)
        assertEquals(1, h.repo.messages.size)
        assertTrue(h.repo.messages.single() is ChatItem.Assistant)
        h.repo.receiveForTest(PromptAck("convo-target", assertNotNull(original.promptId)))
        assertEquals(1, h.repo.agentGroupAcceptedGen.value)
        assertEquals("", h.repo.draftFor("sid-source"))
        h.sent.clear()
        h.repo.restoreAfterReconnectForTest()
        assertTrue(h.sent.none { it is RouteAgentGroup }, "matching PromptAck is the final payload commit")
    }

    @Test
    fun explicitDisconnectDropsPendingDeliveryInsteadOfReplayingItToAnotherMachine() = runBlocking {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "implement it"))
        h.repo.disconnect()
        h.sent.clear()

        h.repo.restoreAfterReconnectForTest()
        assertTrue(h.sent.none { it is RouteAgentGroup })
    }

    @Test
    fun committedReconnectReplaysExactFrameUntilDurableAck() = runBlocking {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "verify me"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )

        h.sent.clear()
        h.repo.restoreAfterReconnectForTest()

        assertEquals(request, assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull()))
        assertEquals(null, h.repo.agentGroupDeliveryError.value)
        assertEquals("verify me", h.repo.draftFor("sid-source"), "unacked source payload remains recoverable")
        assertTrue(h.sent.none { it is OpenSession }, "committed recovery must not reattach the stale source")
    }

    @Test
    fun committedRetryMayRebuildTargetUnderANewConversationAndStillSettleAck() {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "verify retry"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        fun receipt(convo: String) = AgentGroupDelivery(
            request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
            targetSessionId = "sid-target", targetConvoId = convo, promptId = request.promptId,
        )

        h.repo.receiveForTest(receipt("convo-target-old"))
        h.repo.receiveForTest(receipt("convo-target-new"))
        h.repo.receiveForTest(SessionLive("convo-target-new", wd, "sid-target", executing = true))
        h.repo.receiveForTest(PromptAck("convo-target-new", assertNotNull(request.promptId)))

        assertFalse(h.repo.agentGroupDeliveryPending.value)
        assertEquals("", h.repo.draftFor("sid-source"))
        assertTrue(h.repo.openSession(wd, "sid-third"))
    }

    @Test
    fun targetForkUsesStableMemberAndReleasesFenceForTheNextOpen() {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "verify fork"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )

        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target-fork", executing = true))
        assertEquals("sid-target-fork", h.repo.sessionKey.value)
        assertEquals("sid-target-fork", h.repo.sessionGroups.single().members.first { it.id == "member-target" }.sessionId)
        h.repo.receiveForTest(PromptAck("convo-target", assertNotNull(request.promptId)))

        assertTrue(h.repo.openSession(wd, "sid-third"))
        h.repo.receiveForTest(SessionLive("convo-third", wd, "sid-third", executing = false))
        assertEquals("convo-third", h.repo.convoId.value)
    }

    @Test
    fun targetForkUpdatesAmbiguousRecoveryToTheNewSessionId() {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "verify fork"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )
        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target-fork", executing = true))

        h.sent.clear()
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, false, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", errorCode = "open_failed", error = "ledger lost",
            ),
        )

        assertEquals("sid-target-fork", assertNotNull(h.sent.filterIsInstance<OpenSession>().lastOrNull()).resumeId)
    }

    @Test
    fun pendingDeliveryBlocksBackAndSessionSwitchUntilAck() {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "implement it"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        assertTrue(h.repo.agentGroupDeliveryPending.value)

        h.repo.backToBrowse()
        assertEquals("convo-source", h.repo.convoId.value, "BACK cannot locally cancel an on-wire transaction")
        assertFalse(h.repo.openSession(wd, "sid-other"), "session selection is fenced by the same transaction")
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )
        h.repo.receiveForTest(SessionLive("convo-target", wd, "sid-target", executing = false))
        h.repo.receiveForTest(PromptAck("convo-target", assertNotNull(request.promptId)))

        assertEquals("convo-target", h.repo.convoId.value)
        assertEquals(1, h.repo.agentGroupAcceptedGen.value)
        assertFalse(h.repo.agentGroupDeliveryPending.value)
    }

    @Test
    fun ledgerLossAfterSuccessReleasesFenceAndOpensTargetForVerification() {
        val h = Harness().also { it.seed(source, target, group) }
        assertTrue(h.repo.routeAgentGroup(group.id, "member-target", "implement it"))
        val request = assertNotNull(h.sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )

        h.sent.clear()
        h.repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, false, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", errorCode = "open_failed", error = "ledger lost",
            ),
        )
        assertEquals("delivery_state_lost", h.repo.agentGroupDeliveryError.value)
        assertEquals("sid-target", assertNotNull(h.sent.filterIsInstance<OpenSession>().lastOrNull()).resumeId)

        h.repo.receiveForTest(SessionLive("convo-source-restarted", wd, "sid-source", executing = false))
        assertEquals(null, h.repo.convoId.value, "late source announce must not reclaim the recovering view")
        h.repo.receiveForTest(SessionLive("convo-target-new", wd, "sid-target", executing = false))
        assertEquals("convo-target-new", h.repo.convoId.value)
    }

    @Test
    fun sameConversationHealRebindsHeldMemberWithoutChangingStableId() {
        val h = Harness().also { it.seed(source, target, group) }
        h.repo.receiveForTest(SessionLive("convo-source", wd, "sid-source-healed", executing = false))

        val held = assertNotNull(h.repo.sessionGroups.single().members.firstOrNull { it.id == "member-source" })
        assertEquals("sid-source-healed", held.sessionId)
        assertEquals("member-source", h.repo.currentAgentGroup()?.members?.first { it.id == held.id }?.id)
    }

    @Test
    fun editingMemberIdentityPreservesPersistedLaunchProfile() {
        val h = Harness().also { it.seed(source, target, group) }
        h.sent.clear()

        h.repo.configureAgentGroupMember(
            groupId = group.id,
            sessionId = target.sessionId,
            name = "Implementation",
            role = "ship code",
            memberId = "member-target",
        )

        val configured = assertNotNull(h.sent.filterIsInstance<ConfigureAgentGroupMember>().singleOrNull())
        assertEquals(targetProfile, configured.launchProfile)
        assertFalse(configured.launchProfile.agent == target.agent, "the current summary must not overwrite the member profile")
    }

    @Test
    fun silentDaemonReleasesTheDispatchLockAndKeepsThePayload() = runTest {
        val sent = mutableListOf<Frame>()
        val repo = PocketRepository(backgroundScope).apply { onSendForTest = { sent += it } }
        repo.receiveForTest(Sessions(wd, listOf(source, target), groups = listOf(group), agentGroupsSupported = true))
        repo.receiveForTest(SessionLive("convo-source", wd, source.sessionId, executing = false))

        assertTrue(repo.routeAgentGroup(group.id, "member-target", "implement it"))
        assertTrue(repo.agentGroupDeliveryPending.value)
        assertFalse(repo.openSession(wd, "sid-other"), "the lock holds while the request may still be answered")

        // no Delivery, no PocketError, no disconnect — the shape a router exception leaves behind
        advanceTimeBy(PocketRepository.AGENT_GROUP_DELIVERY_TIMEOUT_MS + 1_000)

        assertFalse(repo.agentGroupDeliveryPending.value, "an unanswered dispatch must not freeze the chat forever")
        assertEquals("delivery_timeout", repo.agentGroupDeliveryError.value)
        assertEquals("implement it", repo.draftFor("sid-source"), "nothing was accepted, so nothing may be consumed")
        assertEquals("convo-source", repo.convoId.value, "an unaccepted dispatch never switched the view")
        assertTrue(repo.openSession(wd, "sid-other"), "…and navigation is usable again")

        // …and if the daemon answers after we gave up, the accepted delivery is honoured, not dropped:
        // the prompt may be running in the target right now, so the user is sent there to check.
        val request = assertNotNull(sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        sent.clear()
        repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )
        runCurrent()
        assertEquals("delivery_state_lost", repo.agentGroupDeliveryError.value)
        assertTrue(
            sent.filterIsInstance<OpenSession>().any { it.resumeId == "sid-target" },
            "the late-accepted target is opened for verification instead of being dropped",
        )
        assertEquals("implement it", repo.draftFor("sid-source"), "the payload is still the user's to resend")
    }

    @Test
    fun silenceAfterAnAcceptedSwitchBecomesTheVerifyRecovery() = runTest {
        val sent = mutableListOf<Frame>()
        val repo = PocketRepository(backgroundScope).apply { onSendForTest = { sent += it } }
        repo.receiveForTest(Sessions(wd, listOf(source, target), groups = listOf(group), agentGroupsSupported = true))
        repo.receiveForTest(SessionLive("convo-source", wd, source.sessionId, executing = false))
        assertTrue(repo.routeAgentGroup(group.id, "member-target", "implement it"))
        runCurrent() // the frame goes out on the test dispatcher
        val request = assertNotNull(sent.filterIsInstance<RouteAgentGroup>().singleOrNull())
        repo.receiveForTest(
            AgentGroupDelivery(
                request.requestId, AGENT_GROUP_DELIVERY_ROUTE, true, group.id, "convo-source", "member-target",
                targetSessionId = "sid-target", targetConvoId = "convo-target", promptId = request.promptId,
            ),
        )
        sent.clear()

        advanceTimeBy(PocketRepository.AGENT_GROUP_DELIVERY_TIMEOUT_MS + 1_000)

        // the switch was committed, so the agent may well have taken the prompt: same ambiguity as a lost
        // ledger — never auto-resend, open the target and ask the user to look
        assertEquals("delivery_state_lost", repo.agentGroupDeliveryError.value)
        assertFalse(repo.agentGroupDeliveryPending.value)
        assertEquals("sid-target", assertNotNull(sent.filterIsInstance<OpenSession>().lastOrNull()).resumeId)
        assertEquals("implement it", repo.draftFor("sid-source"))
        assertTrue(sent.none { it is RouteAgentGroup }, "an ambiguous outcome is never resent automatically")
    }

    @Test
    fun rosterRefusalAnswersTheMemberFormAndSurvivesTheListRepush() {
        val h = Harness().also { it.seed(source, target, group) }
        val before = h.repo.messages.size

        h.repo.configureAgentGroupMember(group.id, target.sessionId, "Planner", null, "member-target")
        assertTrue(h.repo.agentGroupRosterBusy.value)
        h.repo.receiveForTest(dev.ccpocket.protocol.PocketError("duplicate_name", "member names must be unique"))

        assertEquals("duplicate_name", h.repo.agentGroupRosterError.value)
        assertFalse(h.repo.agentGroupRosterBusy.value)
        assertEquals(before, h.repo.messages.size, "a roster refusal must not land in an unrelated transcript")

        // the daemon re-pushes the list right after the refusal — that must not wipe the form's reason
        h.repo.receiveForTest(Sessions(wd, listOf(source, target), groups = listOf(group), agentGroupsSupported = true))
        assertEquals("duplicate_name", h.repo.agentGroupRosterError.value)
        assertEquals(0, h.repo.agentGroupRosterGen.value)

        h.repo.configureAgentGroupMember(group.id, target.sessionId, "Builder 2", null, "member-target")
        h.repo.receiveForTest(Sessions(wd, listOf(source, target), groups = listOf(group), agentGroupsSupported = true))
        assertEquals(1, h.repo.agentGroupRosterGen.value, "a settled edit closes the form")
        assertEquals(null, h.repo.agentGroupRosterError.value)
    }

    @Test
    fun selfRouteIsRefusedByClientAndNeverArmsASwitch() = runBlocking {
        val selfGroup = group.copy(members = listOf(group.members.first()))
        val h = Harness().also { it.seed(source, target, selfGroup) }

        assertFalse(h.repo.routeAgentGroup(selfGroup.id, "member-source", "self"))
        h.repo.restoreAfterReconnectForTest()
        assertTrue(h.sent.none { it is RouteAgentGroup })
        assertEquals("convo-source", h.repo.convoId.value)
    }
}

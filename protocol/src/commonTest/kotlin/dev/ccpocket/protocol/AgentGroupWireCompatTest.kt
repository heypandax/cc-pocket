package dev.ccpocket.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wire locks for issue #232's deliberately additive collaboration-group MVP. */
class AgentGroupWireCompatTest {

    private val profile = AgentGroupLaunchProfile(
        agent = AgentKind.CODEX,
        model = "gpt-5.6-sol",
        mode = PermissionMode.PLAN,
        permissionMode = "auto",
        effort = "xhigh",
        serviceTier = "priority",
    )

    private val member = AgentGroupMember(
        id = "member-daemon-stable-1",
        sessionId = "session-current-2",
        name = "protocol",
        role = "Own the wire contract",
        order = 3,
        launchProfile = profile,
    )

    @Test
    fun collaboration_group_and_launch_profile_roundtrip() {
        val group = SessionGroup(
            id = "group-1",
            name = "Issue 232",
            order = 7,
            purpose = SESSION_GROUP_COLLABORATION,
            members = listOf(member),
            defaultMemberId = member.id,
        )

        assertEquals(group, PocketJson.decodeFromString<SessionGroup>(PocketJson.encodeToString(group)))
    }

    @Test
    fun old_group_shape_gets_safe_additive_defaults() {
        val old = PocketJson.decodeFromString<SessionGroup>(
            """{"id":"group-old","name":"Before collaboration","order":1}""",
        )

        assertEquals(SESSION_GROUP_ORGANIZATION, old.purpose)
        assertTrue(old.members.isEmpty())
        assertNull(old.defaultMemberId)
    }

    @Test
    fun purpose_is_an_open_string_and_unknown_value_survives() {
        val future = PocketJson.decodeFromString<SessionGroup>(
            """{"id":"group-future","name":"Future","order":1,"purpose":"supervision"}""",
        )

        assertEquals("supervision", future.purpose)
        assertEquals(future, PocketJson.decodeFromString<SessionGroup>(PocketJson.encodeToString(future)))
    }

    @Test
    fun client_capability_defaults_false_and_roundtrips_true() {
        val old = PocketJson.decodeFromString<ClientCaps>("""{"supportsAgents":["codex"]}""")
        assertFalse(old.supportsAgentGroups)

        val current = ClientCaps(supportsAgents = listOf("codex"), supportsAgentGroups = true)
        assertEquals(current, PocketJson.decodeFromString<ClientCaps>(PocketJson.encodeToString(current)))

        val oldSessions = PocketJson.decodeFromString<Sessions>("""{"workdir":"/repo","items":[]}""")
        assertFalse(oldSessions.agentGroupsSupported)
        val supported = Sessions("/repo", emptyList(), groups = emptyList(), agentGroupsSupported = true)
        assertEquals(supported, PocketJson.decodeFromString<Sessions>(PocketJson.encodeToString(supported)))
    }

    @Test
    fun group_create_purpose_is_additive() {
        val old = PocketJson.decodeFromString<GroupCreate>(
            """{"workdir":"/repo","name":"Ordinary"}""",
        )
        assertEquals(SESSION_GROUP_ORGANIZATION, old.purpose)

        val collaboration = GroupCreate("/repo", "Agents", SESSION_GROUP_COLLABORATION)
        assertEquals(
            collaboration,
            PocketJson.decodeFromString<GroupCreate>(PocketJson.encodeToString(collaboration)),
        )
    }

    @Test
    fun agent_group_request_and_delivery_family_roundtrips() {
        val brief = AgentGroupHandoffBrief(
            objective = "Verify the new wire",
            conclusions = listOf("route by stable member id"),
            constraints = listOf("do not route by name or session title"),
            doneWhen = listOf("protocol tests pass"),
        )
        val frames: List<Frame> = listOf(
            ConfigureAgentGroup("/repo", "group-1", SESSION_GROUP_COLLABORATION),
            ConfigureAgentGroupMember(
                workdir = "/repo",
                groupId = "group-1",
                sessionId = member.sessionId,
                name = member.name,
                role = member.role,
                launchProfile = profile,
                memberId = member.id,
                defaultMember = true,
            ),
            RemoveAgentGroupMember("/repo", "group-1", member.id),
            RouteAgentGroup(
                requestId = "request-route-1",
                workdir = "/repo",
                groupId = "group-1",
                fromConvoId = "convo-source",
                targetMemberId = member.id,
                text = "Run the wire tests",
                images = listOf(ImageData("image/png", "AA==")),
                promptId = "prompt-route-1",
            ),
            HandoffAgentGroup(
                requestId = "request-handoff-1",
                workdir = "/repo",
                groupId = "group-1",
                fromConvoId = "convo-source",
                fromMemberId = "member-source",
                targetMemberId = member.id,
                brief = brief,
                promptId = "prompt-handoff-1",
            ),
            AgentGroupDelivery(
                requestId = "request-route-1",
                kind = AGENT_GROUP_DELIVERY_ROUTE,
                ok = true,
                groupId = "group-1",
                fromConvoId = "convo-source",
                targetMemberId = member.id,
                targetSessionId = member.sessionId,
                targetConvoId = "convo-target",
                promptId = "prompt-route-1",
            ),
            AgentGroupDelivery(
                requestId = "request-handoff-1",
                kind = AGENT_GROUP_DELIVERY_HANDOFF,
                ok = false,
                groupId = "group-1",
                fromConvoId = "convo-source",
                targetMemberId = member.id,
                promptId = "prompt-handoff-1",
                errorCode = "target_unavailable",
                error = "Target is unavailable",
            ),
        )

        for ((index, frame) in frames.withIndex()) {
            val envelope = Envelope(id = "agent-group-$index", ts = index.toLong(), body = frame)
            val json = PocketJson.encodeToString(envelope)
            assertEquals(envelope, PocketJson.decodeFromString<Envelope>(json), json)
        }
    }

    @Test
    fun route_and_handoff_wire_keys_are_stable_member_ids() {
        val routeJson = PocketJson.encodeToString(
            RouteAgentGroup("r1", "/repo", "g1", "source-convo", "member-target", "hello"),
        )
        assertTrue("\"targetMemberId\":\"member-target\"" in routeJson, routeJson)
        assertFalse("targetName" in routeJson, routeJson)
        assertFalse("targetSessionTitle" in routeJson, routeJson)

        val handoffJson = PocketJson.encodeToString(
            HandoffAgentGroup(
                "h1", "/repo", "g1", "source-convo", "member-source", "member-target",
                AgentGroupHandoffBrief("continue"),
            ),
        )
        assertTrue("\"targetMemberId\":\"member-target\"" in handoffJson, handoffJson)
        assertFalse("targetName" in handoffJson, handoffJson)
        assertFalse("targetSessionTitle" in handoffJson, handoffJson)
    }
}

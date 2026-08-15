package dev.ccpocket.app.ui

import dev.ccpocket.protocol.AgentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollaborationGroupUiTest {
    private fun member(id: String, label: String = id, model: String? = "sonnet") = CollaborationMemberUi(
        memberId = "member-$id",
        sessionId = id,
        label = label,
        role = null,
        agent = AgentKind.CLAUDE,
        model = model,
        status = CollaborationMemberStatus.IDLE,
    )

    @Test
    fun target_is_structured_state_and_never_parsed_from_prompt_text() {
        val state = CollaborationTargetState()
        state.select("g1", member("s2", "Coder"))

        assertEquals("g1", state.target?.groupId)
        assertEquals("member-s2", state.target?.memberId)
        assertEquals("s2", state.target?.sessionId)
        assertEquals("Coder", state.target?.label)
        // The reducer intentionally accepts no prompt string: "@someone" can never change authority.
    }

    @Test
    fun stale_or_cross_group_target_is_cleared_but_fresh_metadata_is_reconciled() {
        val state = CollaborationTargetState()
        state.select("g1", member("s2", "Coder"))
        state.reconcile("g1", listOf(member("s3", "Implementer").copy(memberId = "member-s2")))
        assertEquals("Implementer", state.target?.label, "server metadata refreshes the visible @ label")
        assertEquals("s3", state.target?.sessionId, "a fork/heal can move the stable member to a new transcript")

        state.reconcile("g2", listOf(member("s3", "Implementer").copy(memberId = "member-s2")))
        assertNull(state.target, "the same member id in another group is not a valid old target")
    }

    @Test
    fun handoff_requires_all_four_context_sections_and_trims_them() {
        val incomplete = CollaborationBriefDraft(objective = "ship", conclusions = "known", constraints = "safe")
        assertFalse(incomplete.complete)

        val complete = incomplete.copy(doneWhen = " tests green ")
        assertTrue(complete.complete)
        assertEquals("tests green", complete.trimmed().doneWhen)
    }

    @Test
    fun brief_list_sections_reach_the_wire_as_separate_items() {
        val brief = CollaborationBriefDraft(
            objective = " ship the route fence ",
            conclusions = "PromptAck is the commit point\n- Delivery only switches the view\n\n",
            constraints = "no shared transcript",
            doneWhen = "tests green\nreal device checked",
        ).toBrief()

        assertEquals("ship the route fence", brief.objective)
        // one bullet per line, "- " typed by hand absorbed: the daemon renders each element as its own
        // markdown bullet, so a multi-line blob in one element breaks out of the list on the target's side
        assertEquals(listOf("PromptAck is the commit point", "Delivery only switches the view"), brief.conclusions)
        assertEquals(listOf("no shared transcript"), brief.constraints)
        assertEquals(listOf("tests green", "real device checked"), brief.doneWhen)
    }

    @Test
    fun uncertain_delivery_is_a_verification_task_not_a_refusal() {
        assertEquals(CollaborationNoticeKind.VERIFY, agentGroupDeliveryNoticeKind("delivery_state_lost"))
        assertEquals(CollaborationNoticeKind.REFUSED, agentGroupDeliveryNoticeKind("delivery_timeout"))
        assertEquals(CollaborationNoticeKind.REFUSED, agentGroupDeliveryNoticeKind("owner_only"))
    }

    @Test
    fun suggested_names_are_friendly_editable_and_case_insensitively_unique() {
        assertEquals("opus", suggestedCollaborationMemberName(AgentKind.CLAUDE, "claude-opus-4-8", emptyList()))
        assertEquals("opus 3", suggestedCollaborationMemberName(AgentKind.CLAUDE, "claude-opus-4-8", listOf("Opus", "opus 2")))
        assertEquals("Codex", suggestedCollaborationMemberName(AgentKind.CODEX, null, emptyList()))
    }

    @Test
    fun member_input_mirrors_daemon_safety_rules() {
        assertEquals(
            CollaborationMemberInputError.DUPLICATE,
            validateCollaborationMemberInput(" builder ", "ship", listOf("Builder"), false, 2).error,
        )
        assertEquals(
            CollaborationMemberInputError.NAME,
            validateCollaborationMemberInput("@all", "ship", emptyList(), false, 2).error,
        )
        assertEquals(
            CollaborationMemberInputError.GROUP_FULL,
            validateCollaborationMemberInput("Reviewer", "review", emptyList(), false, 8).error,
        )
        assertNull(validateCollaborationMemberInput(" Builder 2 ", " implementation ", listOf("Builder"), false, 2).error)
    }
}

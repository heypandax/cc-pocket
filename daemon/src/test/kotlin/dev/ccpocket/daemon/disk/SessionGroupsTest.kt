package dev.ccpocket.daemon.disk

import dev.ccpocket.protocol.AgentGroupLaunchProfile
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.SESSION_GROUP_COLLABORATION
import dev.ccpocket.protocol.SESSION_GROUP_ORGANIZATION
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionGroupsTest {

    private fun tempFile() = Files.createTempFile("ccp-groups", ".json").toFile().also { it.delete() }

    @Test
    fun create_assign_groupOf_and_ordering() {
        val f = tempFile()
        val wd = "/Users/panda/proj"

        val a = SessionGroups.create(wd, "Feature", f)
        val b = SessionGroups.create(wd, "Bugs", f)
        assertNotNull(a); assertNotNull(b)
        assertEquals(0, a.order); assertEquals(1, b.order)
        assertTrue(a.id != b.id)

        // insertion order preserved
        assertEquals(listOf("Feature", "Bugs"), SessionGroups.groupsFor(wd, f).map { it.name })

        assertTrue(SessionGroups.assign(wd, "sid-1", a.id, f))
        assertEquals(a.id, SessionGroups.groupOf(wd, "sid-1", f))
        assertNull(SessionGroups.groupOf(wd, "sid-unknown", f))
    }

    @Test
    fun rename_and_blank_name_rejected() {
        val f = tempFile()
        val wd = "/w"
        val g = SessionGroups.create(wd, "  Trimmed  ", f)!!
        assertEquals("Trimmed", g.name) // trimmed

        assertNull(SessionGroups.create(wd, "   ", f)) // blank rejected
        assertTrue(SessionGroups.rename(wd, g.id, "New name", f))
        assertEquals("New name", SessionGroups.groupsFor(wd, f).single().name)
        assertFalse(SessionGroups.rename(wd, g.id, "  ", f))          // blank rejected
        assertFalse(SessionGroups.rename(wd, "nope", "X", f))         // missing group
    }

    @Test
    fun assign_out_and_reassign() {
        val f = tempFile()
        val wd = "/w"
        val g = SessionGroups.create(wd, "G", f)!!
        SessionGroups.assign(wd, "s", g.id, f)
        assertEquals(g.id, SessionGroups.groupOf(wd, "s", f))
        // move out of any group
        assertTrue(SessionGroups.assign(wd, "s", null, f))
        assertNull(SessionGroups.groupOf(wd, "s", f))
        // assigning to a non-existent group is refused
        assertFalse(SessionGroups.assign(wd, "s", "ghost", f))
    }

    @Test
    fun delete_group_falls_back_its_sessions_but_keeps_others() {
        val f = tempFile()
        val wd = "/w"
        val g1 = SessionGroups.create(wd, "One", f)!!
        val g2 = SessionGroups.create(wd, "Two", f)!!
        SessionGroups.assign(wd, "a", g1.id, f)
        SessionGroups.assign(wd, "b", g1.id, f)
        SessionGroups.assign(wd, "c", g2.id, f)

        assertTrue(SessionGroups.delete(wd, g1.id, f))
        // g1's sessions fall back to ungrouped; g2's assignment untouched
        assertNull(SessionGroups.groupOf(wd, "a", f))
        assertNull(SessionGroups.groupOf(wd, "b", f))
        assertEquals(g2.id, SessionGroups.groupOf(wd, "c", f))
        assertEquals(listOf("Two"), SessionGroups.groupsFor(wd, f).map { it.name })
        assertFalse(SessionGroups.delete(wd, "already-gone", f))
    }

    @Test
    fun inherit_copies_membership_on_fork() {
        val f = tempFile()
        val wd = "/w"
        val g = SessionGroups.create(wd, "G", f)!!
        SessionGroups.assign(wd, "parent", g.id, f)

        // forked child inherits the parent's group
        assertTrue(SessionGroups.inherit(wd, "parent", "child", f))
        assertEquals(g.id, SessionGroups.groupOf(wd, "child", f))

        // parent unchanged
        assertEquals(g.id, SessionGroups.groupOf(wd, "parent", f))

        // an ungrouped parent inherits nothing
        assertFalse(SessionGroups.inherit(wd, "loner", "loner-child", f))
        assertNull(SessionGroups.groupOf(wd, "loner-child", f))

        // inherit into a group that was since deleted is a no-op
        SessionGroups.assign(wd, "p2", g.id, f)
        SessionGroups.delete(wd, g.id, f)
        assertFalse(SessionGroups.inherit(wd, "p2", "c2", f))
    }

    @Test
    fun persistence_survives_reload() {
        val f = tempFile()
        val wd = "/persisted/proj"
        val g = SessionGroups.create(wd, "Persisted", f)!!
        SessionGroups.assign(wd, "sid", g.id, f)

        // force a fresh read by touching another file first (defeats the in-memory snapshot), then re-read f
        val other = tempFile()
        SessionGroups.create("/other", "x", other)
        assertEquals("Persisted", SessionGroups.groupsFor(wd, f).single().name)
        assertEquals(g.id, SessionGroups.groupOf(wd, "sid", f))

        // the file is real JSON keyed by the project dir-key
        val raw = f.readText()
        assertTrue(ProjectPaths.dirKey(wd) in raw, raw)
    }

    @Test
    fun projects_are_partitioned_by_dirKey() {
        val f = tempFile()
        val a = SessionGroups.create("/proj/a", "GA", f)!!
        SessionGroups.create("/proj/b", "GB", f)
        // a group under /proj/a is invisible to /proj/b
        assertEquals(listOf("GA"), SessionGroups.groupsFor("/proj/a", f).map { it.name })
        assertEquals(listOf("GB"), SessionGroups.groupsFor("/proj/b", f).map { it.name })
        assertNull(SessionGroups.groupOf("/proj/b", "x", f))
        // assigning under the wrong project can't see the other's group id
        assertFalse(SessionGroups.assign("/proj/b", "s", a.id, f))
    }

    @Test
    fun malformed_sessionId_is_rejected() {
        val f = tempFile()
        val wd = "/w"
        val g = SessionGroups.create(wd, "G", f)!!
        assertFalse(SessionGroups.assign(wd, "bad id with spaces", g.id, f))
        assertFalse(SessionGroups.assign(wd, "../escape", g.id, f))
    }

    @Test
    fun collaboration_roster_is_atomic_named_and_stable() {
        val f = tempFile()
        val wd = "/collab"
        val group = SessionGroups.create(wd, "Agents", f, SESSION_GROUP_COLLABORATION)!!
        assertEquals(SESSION_GROUP_COLLABORATION, group.purpose)
        assertFalse(SessionGroups.assign(wd, "sid-codex", group.id, f), "legacy assign cannot create a naked member")

        val created = SessionGroups.configureMember(
            wd, group.id, "sid-codex", "  Codex  ", "implementation",
            AgentGroupLaunchProfile(agent = AgentKind.CODEX, model = "gpt-5.1-codex"), file = f,
        )
        assertTrue(created.ok, created.error)
        val member = created.member!!
        assertEquals("Codex", member.name)
        assertEquals(member.id, SessionGroups.memberOf(wd, "sid-codex", f)?.id)
        assertEquals(group.id, SessionGroups.groupOf(wd, "sid-codex", f))
        val stored = SessionGroups.groupsFor(wd, f).single()
        assertEquals(member.id, stored.defaultMemberId)
        assertEquals(AgentKind.CODEX, stored.members.single().launchProfile.agent)

        val duplicate = SessionGroups.configureMember(
            wd, group.id, "sid-other", "codex", null, AgentGroupLaunchProfile(), file = f,
        )
        assertEquals("duplicate_name", duplicate.errorCode)
        assertNull(SessionGroups.groupOf(wd, "sid-other", f))
        assertFalse(SessionGroups.assign(wd, "sid-codex", null, f), "legacy unassign cannot orphan roster metadata")
    }

    @Test
    fun member_names_reject_all_at_and_controls() {
        val f = tempFile()
        val wd = "/names"
        val group = SessionGroups.create(wd, "Agents", f, SESSION_GROUP_COLLABORATION)!!
        for (name in listOf("all", "@all", "bad\nname")) {
            val result = SessionGroups.configureMember(
                wd, group.id, "sid-${name.hashCode().toUInt()}", name, null, AgentGroupLaunchProfile(), file = f,
            )
            assertEquals("invalid_name", result.errorCode, name)
        }
    }

    @Test
    fun collaboration_fork_rebinds_stable_member_identity() {
        val f = tempFile()
        val wd = "/fork-collab"
        val group = SessionGroups.create(wd, "Agents", f, SESSION_GROUP_COLLABORATION)!!
        val before = SessionGroups.configureMember(
            wd, group.id, "parent", "Planner", null, AgentGroupLaunchProfile(), file = f,
        ).member!!

        assertTrue(SessionGroups.inherit(wd, "parent", "child", f))
        assertNull(SessionGroups.memberOf(wd, "parent", f))
        assertNull(SessionGroups.groupOf(wd, "parent", f))
        assertEquals(before.id, SessionGroups.memberOf(wd, "child", f)?.id)
        assertEquals("child", SessionGroups.groupsFor(wd, f).single().members.single().sessionId)
    }

    @Test
    fun collaboration_fork_rebind_refuses_an_already_assigned_target() {
        val f = tempFile()
        val wd = "/fork-conflict"
        val agents = SessionGroups.create(wd, "Agents", f, SESSION_GROUP_COLLABORATION)!!
        val ordinary = SessionGroups.create(wd, "Ordinary", f)!!
        val member = SessionGroups.configureMember(
            wd, agents.id, "parent", "Planner", null, AgentGroupLaunchProfile(), file = f,
        ).member!!
        SessionGroups.assign(wd, "occupied", ordinary.id, f)

        assertFalse(SessionGroups.inherit(wd, "parent", "occupied", f))
        assertEquals(member.id, SessionGroups.memberOf(wd, "parent", f)?.id)
        assertEquals(ordinary.id, SessionGroups.groupOf(wd, "occupied", f))
    }

    @Test
    fun collaboration_group_caps_new_members_at_eight_but_allows_updates() {
        val f = tempFile()
        val wd = "/cap"
        val group = SessionGroups.create(wd, "Agents", f, SESSION_GROUP_COLLABORATION)!!
        val members = (1..8).map { n ->
            SessionGroups.configureMember(
                wd, group.id, "sid-$n", "Agent$n", null, AgentGroupLaunchProfile(), file = f,
            ).member!!
        }
        val ninth = SessionGroups.configureMember(
            wd, group.id, "sid-9", "Agent9", null, AgentGroupLaunchProfile(), file = f,
        )
        assertEquals("group_full", ninth.errorCode)

        val updated = SessionGroups.configureMember(
            wd, group.id, members.first().sessionId, "Lead", "planner", members.first().launchProfile,
            memberId = members.first().id, file = f,
        )
        assertTrue(updated.ok, updated.error)
        assertEquals("Lead", SessionGroups.groupsFor(wd, f).single().members.first().name)
    }

    @Test
    fun configure_member_moves_from_ordinary_group_but_not_another_collaboration_roster() {
        val f = tempFile()
        val wd = "/atomic-move"
        val ordinary = SessionGroups.create(wd, "Filed", f)!!
        val first = SessionGroups.create(wd, "First agents", f, SESSION_GROUP_COLLABORATION)!!
        val second = SessionGroups.create(wd, "Second agents", f, SESSION_GROUP_COLLABORATION)!!
        SessionGroups.assign(wd, "session", ordinary.id, f)

        val moved = SessionGroups.configureMember(
            wd, first.id, "session", "Coder", null, AgentGroupLaunchProfile(), file = f,
        )
        assertTrue(moved.ok, moved.error)
        assertEquals(first.id, SessionGroups.groupOf(wd, "session", f))
        assertEquals(moved.member!!.id, SessionGroups.memberOf(wd, "session", f)?.id)

        val refused = SessionGroups.configureMember(
            wd, second.id, "session", "Other", null, AgentGroupLaunchProfile(), file = f,
        )
        assertEquals("not_member", refused.errorCode)
        assertEquals(first.id, SessionGroups.groupOf(wd, "session", f))
        assertEquals(moved.member.id, SessionGroups.memberOf(wd, "session", f)?.id)
    }

    @Test
    fun purpose_transition_never_exposes_naked_members_and_downgrade_clears_roster() {
        val f = tempFile()
        val wd = "/purpose"
        val ordinary = SessionGroups.create(wd, "Ordinary", f)!!
        SessionGroups.assign(wd, "existing", ordinary.id, f)
        assertFalse(SessionGroups.configurePurpose(wd, ordinary.id, SESSION_GROUP_COLLABORATION, f))
        assertEquals(SESSION_GROUP_ORGANIZATION, SessionGroups.groupsFor(wd, f).single().purpose)

        val collab = SessionGroups.create(wd, "Agents", f, SESSION_GROUP_COLLABORATION)!!
        val member = SessionGroups.configureMember(
            wd, collab.id, "agent", "Agent", null, AgentGroupLaunchProfile(), file = f,
        ).member!!
        assertTrue(SessionGroups.configurePurpose(wd, collab.id, SESSION_GROUP_ORGANIZATION, f))
        val downgraded = SessionGroups.groupsFor(wd, f).first { it.id == collab.id }
        assertTrue(downgraded.members.isEmpty())
        assertNull(downgraded.defaultMemberId)
        assertEquals(collab.id, SessionGroups.groupOf(wd, member.sessionId, f), "session remains an ordinary group member")
        assertNull(SessionGroups.memberOf(wd, member.sessionId, f))
    }
}

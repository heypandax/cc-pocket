package dev.ccpocket.daemon.disk

import dev.ccpocket.daemon.identity.Identity
import dev.ccpocket.daemon.util.logger
import dev.ccpocket.protocol.SessionGroup
import dev.ccpocket.protocol.AgentGroupLaunchProfile
import dev.ccpocket.protocol.AgentGroupMember
import dev.ccpocket.protocol.SESSION_GROUP_COLLABORATION
import dev.ccpocket.protocol.SESSION_GROUP_ORGANIZATION
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * Optional one-level GROUPING of a project's sessions (issue #119): `project → group (optional) → session`,
 * scheme A (a session belongs to 0 or 1 group). The metadata lives HERE, on the daemon, so it is consistent
 * across every paired client (the phone/desktop hold no group truth of their own) and survives app reinstalls.
 *
 * Membership is keyed on the backend-agnostic [SessionGroup] id ↔ `sessionId` — Claude and Codex sessions
 * group identically, nothing couples to a `~/.claude` transcript path. Groups are partitioned by
 * [ProjectPaths.dirKey] so one file holds every project's groups:
 *
 *   { "<dirKey>": { "groups": [{id,name,order}, …], "assign": { "<sessionId>": "<groupId>" } } }
 *
 * Deleting a group drops its [ProjectGroups.assign] entries (its sessions fall back to "ungrouped"), never the
 * sessions themselves. Orphan assigns (a session that no longer exists) are harmless and reaped lazily — a stale
 * `sessionId → groupId` entry simply never matches a live summary, so we don't sweep them.
 *
 * Persisted like [SpawnedSessions]: owner-only file next to `identity.json`, atomic tmp+rename, all access
 * `@Synchronized`. Reads are served from an mtime-guarded in-memory snapshot so enriching a session list
 * (one [groupOf] per row) doesn't reparse the file per session.
 */
object SessionGroups {
    private val log = logger("SessionGroups")

    private const val MAX_GROUPS_PER_PROJECT = 100
    private const val MAX_PROJECTS = 1000
    private const val MAX_NAME_LEN = 60
    private const val MAX_MEMBER_NAME_LEN = 32
    private const val MAX_MEMBER_ROLE_LEN = 80
    private const val MAX_MEMBERS_PER_COLLABORATION_GROUP = 8

    // groupIds are daemon-minted; sessionIds ride in from the wire — validate both before they steer a
    // stored map key (a hostile id must never influence the rewrite path — same guard as SpawnedSessions).
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** One project's groups + its session→group assignments. */
    @Serializable
    private data class ProjectGroups(
        val groups: List<SessionGroup> = emptyList(),
        val assign: Map<String, String> = emptyMap(),
    )

    fun defaultFile(): File = File(Identity.defaultPath().parentFile, "session-groups.json")

    // mtime-guarded snapshot: skip the reparse when neither the file nor the target path changed. Keyed on the
    // file so a test's temp file and the prod default can't read each other's cache.
    private var cacheFile: File? = null
    private var cacheMtime: Long = -1
    private var cache: Map<String, ProjectGroups> = emptyMap()

    @Synchronized
    private fun load(file: File): Map<String, ProjectGroups> {
        val mtime = if (file.exists()) file.lastModified() else 0L
        if (file == cacheFile && mtime == cacheMtime) return cache
        val parsed =
            if (file.exists()) runCatching { json.decodeFromString<Map<String, ProjectGroups>>(file.readText()) }
                .getOrElse { log.warn("groups read failed (${it.message}) — starting empty"); emptyMap() }
            else emptyMap()
        cacheFile = file; cacheMtime = mtime; cache = parsed
        return parsed
    }

    @Synchronized
    private fun persist(file: File, data: Map<String, ProjectGroups>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(data))
            // owner-only, like identity.json — the file maps project paths to session ids
            runCatching { Files.setPosixFilePermissions(tmp.toPath(), PosixFilePermissions.fromString("rw-------")) }
            runCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                .recoverCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
            cacheFile = file; cacheMtime = file.lastModified(); cache = data
        }.onFailure { log.warn("groups write failed: ${it.message}") }
    }

    private fun keyOf(workdir: String) = ProjectPaths.dirKey(workdir)
    private fun newGroupId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    // ── reads ────────────────────────────────────────────────────────────────

    /** [workdir]'s groups, ordered by insertion. Empty when the project has none. */
    @Synchronized
    fun groupsFor(workdir: String, file: File = defaultFile()): List<SessionGroup> =
        load(file)[keyOf(workdir)]?.groups?.sortedBy { it.order } ?: emptyList()

    /** The group [sessionId] belongs to under [workdir], or null (ungrouped / unknown). */
    @Synchronized
    fun groupOf(workdir: String, sessionId: String, file: File = defaultFile()): String? =
        load(file)[keyOf(workdir)]?.assign?.get(sessionId)

    /** Stable roster identity for [sessionId], or null for ordinary/ungrouped sessions. */
    @Synchronized
    fun memberOf(workdir: String, sessionId: String, file: File = defaultFile()): AgentGroupMember? {
        val project = load(file)[keyOf(workdir)] ?: return null
        val groupId = project.assign[sessionId] ?: return null
        val group = project.groups.firstOrNull { it.id == groupId && it.purpose == SESSION_GROUP_COLLABORATION }
            ?: return null
        return group.members.firstOrNull { it.sessionId == sessionId }
    }

    // ── writes ───────────────────────────────────────────────────────────────

    /** Create a new group under [workdir]; returns it (with a freshly minted id + trailing order), or null if
     *  [name] is blank or a cap is hit. */
    @Synchronized
    fun create(
        workdir: String,
        name: String,
        file: File = defaultFile(),
        purpose: String = SESSION_GROUP_ORGANIZATION,
    ): SessionGroup? {
        val nm = name.trim().take(MAX_NAME_LEN)
        if (nm.isEmpty()) return null
        if (purpose != SESSION_GROUP_ORGANIZATION && purpose != SESSION_GROUP_COLLABORATION) return null
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        if (key !in data && data.size >= MAX_PROJECTS) return null
        val proj = data[key] ?: ProjectGroups()
        if (proj.groups.size >= MAX_GROUPS_PER_PROJECT) return null
        val order = (proj.groups.maxOfOrNull { it.order } ?: -1) + 1
        val group = SessionGroup(id = newGroupId(), name = nm, order = order, purpose = purpose)
        data[key] = proj.copy(groups = proj.groups + group)
        persist(file, data)
        return group
    }

    /** Rename an existing group. Returns false if [name] is blank or the group doesn't exist. */
    @Synchronized
    fun rename(workdir: String, groupId: String, name: String, file: File = defaultFile()): Boolean {
        if (!ID.matches(groupId)) return false
        val nm = name.trim().take(MAX_NAME_LEN)
        if (nm.isEmpty()) return false
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: return false
        if (proj.groups.none { it.id == groupId }) return false
        data[key] = proj.copy(groups = proj.groups.map { if (it.id == groupId) it.copy(name = nm) else it })
        persist(file, data)
        return true
    }

    /** Delete a group and drop every assignment into it (its sessions fall back to ungrouped; the sessions
     *  themselves are untouched). Returns false if the group didn't exist. */
    @Synchronized
    fun delete(workdir: String, groupId: String, file: File = defaultFile()): Boolean {
        if (!ID.matches(groupId)) return false
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: return false
        if (proj.groups.none { it.id == groupId }) return false
        data[key] = proj.copy(
            groups = proj.groups.filterNot { it.id == groupId },
            assign = proj.assign.filterValues { it != groupId },
        )
        persist(file, data)
        return true
    }

    /** Move [sessionId] into [groupId] (null = out of any group). Returns false on a bad id or an assign into a
     *  group that doesn't exist. Assigning is idempotent. */
    @Synchronized
    fun assign(workdir: String, sessionId: String, groupId: String?, file: File = defaultFile()): Boolean {
        if (!ID.matches(sessionId)) return false
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: ProjectGroups()
        val assign = proj.assign.toMutableMap()
        // Collaboration membership is roster-authoritative. Letting the legacy move action mutate either
        // edge would create a group member without metadata (or metadata without a member).
        val current = assign[sessionId]?.let { id -> proj.groups.firstOrNull { it.id == id } }
        if (current?.purpose == SESSION_GROUP_COLLABORATION) return false
        if (groupId == null) {
            if (assign.remove(sessionId) == null) return true // already ungrouped — nothing to persist, but not an error
        } else {
            if (!ID.matches(groupId)) return false
            val target = proj.groups.firstOrNull { it.id == groupId } ?: return false
            if (target.purpose == SESSION_GROUP_COLLABORATION) return false
            if (assign[sessionId] == groupId) return true // no-op
            assign[sessionId] = groupId
        }
        data[key] = proj.copy(assign = assign)
        persist(file, data)
        return true
    }

    /** Copy [fromSid]'s group membership onto [toSid] — used when a session FORKS (heal / take-over / conditional
     *  fork mint a new sessionId) so the branch inherits its parent's group. No-op (false) when the parent had
     *  none or the target group has since been deleted. */
    @Synchronized
    fun inherit(workdir: String, fromSid: String, toSid: String, file: File = defaultFile()): Boolean {
        if (!ID.matches(fromSid) || !ID.matches(toSid) || fromSid == toSid) return false
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: return false
        val gid = proj.assign[fromSid] ?: return false
        val group = proj.groups.firstOrNull { it.id == gid } ?: return false
        if (group.purpose == SESSION_GROUP_COLLABORATION) {
            val member = group.members.firstOrNull { it.sessionId == fromSid } ?: return false
            val assignedTarget = proj.assign[toSid]
            val rosterTarget = proj.groups.asSequence().flatMap { it.members.asSequence() }.firstOrNull { it.sessionId == toSid }
            if (assignedTarget != null && assignedTarget != gid) return false
            if (rosterTarget != null && rosterTarget.id != member.id) return false
            if (assignedTarget == gid && rosterTarget?.id == member.id) return true
            val rebound = group.copy(members = group.members.map { if (it.id == member.id) it.copy(sessionId = toSid) else it })
            data[key] = proj.copy(
                groups = proj.groups.map { if (it.id == gid) rebound else it },
                assign = proj.assign - fromSid + (toSid to gid),
            )
            persist(file, data)
            return true
        }
        if (proj.assign[toSid] == gid) return true
        data[key] = proj.copy(assign = proj.assign + (toSid to gid))
        persist(file, data)
        return true
    }

    // ── issue #232 collaboration roster ──

    data class MemberMutation(
        val member: AgentGroupMember? = null,
        val errorCode: String? = null,
        val error: String? = null,
    ) {
        val ok: Boolean get() = member != null
    }

    /** Change group purpose without ever exposing a half-rostered collaboration group. An organization
     * group can be upgraded only while empty; downgrade preserves ordinary assignments but clears roster. */
    @Synchronized
    fun configurePurpose(workdir: String, groupId: String, purpose: String, file: File = defaultFile()): Boolean {
        if (!ID.matches(groupId)) return false
        if (purpose != SESSION_GROUP_ORGANIZATION && purpose != SESSION_GROUP_COLLABORATION) return false
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: return false
        val group = proj.groups.firstOrNull { it.id == groupId } ?: return false
        if (group.purpose == purpose) return true
        if (purpose == SESSION_GROUP_COLLABORATION && proj.assign.values.any { it == groupId }) return false
        val changed = if (purpose == SESSION_GROUP_ORGANIZATION) {
            group.copy(purpose = purpose, members = emptyList(), defaultMemberId = null)
        } else {
            group.copy(purpose = purpose)
        }
        data[key] = proj.copy(groups = proj.groups.map { if (it.id == groupId) changed else it })
        persist(file, data)
        return true
    }

    /** Atomic roster upsert + session assignment. Names are NFKC-normalized, explicit-@ safe and unique
     * case-insensitively within the group. [memberId] null creates the stable daemon identity. */
    @Synchronized
    fun configureMember(
        workdir: String,
        groupId: String,
        sessionId: String,
        name: String,
        role: String?,
        launchProfile: AgentGroupLaunchProfile,
        memberId: String? = null,
        defaultMember: Boolean = false,
        file: File = defaultFile(),
    ): MemberMutation {
        if (!ID.matches(groupId) || !ID.matches(sessionId) || (memberId != null && !ID.matches(memberId))) {
            return MemberMutation(errorCode = "not_member", error = "invalid group, member, or session id")
        }
        val normalized = normalizeMemberName(name)
            ?: return MemberMutation(errorCode = "invalid_name", error = "member name must be 1-$MAX_MEMBER_NAME_LEN safe characters and cannot be all")
        val normalizedRole = role?.let { Normalizer.normalize(it, Normalizer.Form.NFKC).trim() }
            ?.takeIf { it.isNotEmpty() }
        if (normalizedRole != null && (normalizedRole.length > MAX_MEMBER_ROLE_LEN || normalizedRole.any(Char::isISOControl))) {
            return MemberMutation(errorCode = "invalid_role", error = "member role is too long or contains control characters")
        }
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: return MemberMutation(errorCode = "invalid_group", error = "group does not exist")
        val group = proj.groups.firstOrNull { it.id == groupId && it.purpose == SESSION_GROUP_COLLABORATION }
            ?: return MemberMutation(errorCode = "invalid_group", error = "group is not a collaboration group")
        val existing = memberId?.let { id -> group.members.firstOrNull { it.id == id } }
        if (memberId != null && existing == null) return MemberMutation(errorCode = "not_member", error = "member does not exist")
        if (existing == null && group.members.size >= MAX_MEMBERS_PER_COLLABORATION_GROUP) {
            return MemberMutation(errorCode = "group_full", error = "a collaboration group supports at most $MAX_MEMBERS_PER_COLLABORATION_GROUP members")
        }
        if (group.members.any { it.id != memberId && memberNameKey(it.name) == memberNameKey(normalized) }) {
            return MemberMutation(errorCode = "duplicate_name", error = "member names must be unique in a collaboration group")
        }
        if (group.members.any { it.id != memberId && it.sessionId == sessionId }) {
            return MemberMutation(errorCode = "not_member", error = "that session already belongs to another member")
        }
        val assignedElsewhere = proj.assign[sessionId]
        if (assignedElsewhere != null && assignedElsewhere != groupId) {
            val prior = proj.groups.firstOrNull { it.id == assignedElsewhere }
            if (prior?.purpose == SESSION_GROUP_COLLABORATION) {
                return MemberMutation(errorCode = "not_member", error = "that session already belongs to another collaboration member")
            }
            // Explicit configure is the atomic "add this existing session as a member" operation. It may
            // move out of an ordinary filing group because that group carries no roster metadata to orphan.
        }
        val id = existing?.id ?: newGroupId()
        val order = existing?.order ?: ((group.members.maxOfOrNull { it.order } ?: -1) + 1)
        val member = AgentGroupMember(id, sessionId, normalized, normalizedRole, order, launchProfile)
        val members = if (existing == null) group.members + member else group.members.map { if (it.id == id) member else it }
        val changed = group.copy(
            members = members.sortedBy { it.order },
            defaultMemberId = if (defaultMember || group.defaultMemberId == null) id else group.defaultMemberId,
        )
        var assigns = proj.assign
        if (existing != null && existing.sessionId != sessionId) assigns = assigns - existing.sessionId
        assigns = assigns + (sessionId to groupId)
        data[key] = proj.copy(groups = proj.groups.map { if (it.id == groupId) changed else it }, assign = assigns)
        persist(file, data)
        return MemberMutation(member = member)
    }

    @Synchronized
    fun removeMember(workdir: String, groupId: String, memberId: String, file: File = defaultFile()): Boolean {
        if (!ID.matches(groupId) || !ID.matches(memberId)) return false
        val key = keyOf(workdir)
        val data = load(file).toMutableMap()
        val proj = data[key] ?: return false
        val group = proj.groups.firstOrNull { it.id == groupId && it.purpose == SESSION_GROUP_COLLABORATION } ?: return false
        val member = group.members.firstOrNull { it.id == memberId } ?: return false
        val remaining = group.members.filterNot { it.id == memberId }
        val changed = group.copy(
            members = remaining,
            defaultMemberId = if (group.defaultMemberId == memberId) remaining.minByOrNull { it.order }?.id else group.defaultMemberId,
        )
        data[key] = proj.copy(
            groups = proj.groups.map { if (it.id == groupId) changed else it },
            assign = proj.assign - member.sessionId,
        )
        persist(file, data)
        return true
    }

    private fun normalizeMemberName(raw: String): String? {
        if (raw.any(Char::isISOControl)) return null
        val value = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
        if (value.isEmpty() || value.length > MAX_MEMBER_NAME_LEN) return null
        if (value.any(Char::isISOControl) || '@' in value) return null
        if (value.any { !(it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '.') }) return null
        if (memberNameKey(value) == "all") return null
        return value
    }

    private fun memberNameKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
}

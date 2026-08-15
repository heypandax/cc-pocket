package dev.ccpocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ccpocket.app.theme.Metric
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.protocol.AgentGroupHandoffBrief
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.SessionGroup
import dev.ccpocket.protocol.SessionSummary
import dev.ccpocket.app.resources.*
import dev.ccpocket.app.normalizeNfkc
import org.jetbrains.compose.resources.stringResource

/**
 * Client-side presentation model for one member of an issue #232 collaboration group.
 *
 * [memberId] is the daemon-minted stable routing identity; [sessionId] locates the member's current,
 * independent transcript and is non-null because the MVP only admits existing persistent sessions. They
 * stay separate because fork/heal/clear may replace a transcript id without changing who `@name` means.
 * A title is deliberately not used as identity: titles are mutable session content.
 */
data class CollaborationMemberUi(
    val memberId: String,
    val sessionId: String,
    val label: String,
    val role: String?,
    val agent: AgentKind,
    val model: String?,
    val status: CollaborationMemberStatus,
)

/** Every state must be backed by an existing fact: pending ask, busy process, live transcript, link. */
enum class CollaborationMemberStatus { NEEDS_INPUT, BUSY, LIVE, IDLE, OFFLINE }

/**
 * The composer's target is structured state, never inferred from arbitrary prompt text.
 *
 * `@label` is only a rendering convention. The wire route must carry [groupId] and [memberId]; [sessionId]
 * is a snapshot for view reconciliation, never routing authority. The daemon validates live membership.
 */
data class CollaborationTarget(
    val groupId: String,
    val memberId: String,
    val sessionId: String,
    val label: String,
)

/** The four context-isolating fields required for a delegation brief in issue #232. */
data class CollaborationBriefDraft(
    val objective: String = "",
    val conclusions: String = "",
    val constraints: String = "",
    val doneWhen: String = "",
) {
    val complete: Boolean
        get() = objective.isNotBlank() && conclusions.isNotBlank() &&
            constraints.isNotBlank() && doneWhen.isNotBlank()

    fun trimmed() = copy(
        objective = objective.trim(),
        conclusions = conclusions.trim(),
        constraints = constraints.trim(),
        doneWhen = doneWhen.trim(),
    )

    /**
     * The three list fields are entered one item per line, so they must reach the wire as real list items:
     * the daemon renders each element as its own markdown bullet, and a multi-line blob wrapped in a
     * single-element list breaks out of that list on the target's side.
     */
    fun toBrief(): AgentGroupHandoffBrief {
        val t = trimmed()
        fun items(value: String) = value.lines().map { it.trim().removePrefix("- ").trim() }.filter { it.isNotEmpty() }
        return AgentGroupHandoffBrief(
            objective = t.objective,
            conclusions = items(t.conclusions),
            constraints = items(t.constraints),
            doneWhen = items(t.doneWhen),
        )
    }
}

enum class CollaborationMemberInputError { NAME, DUPLICATE, ROLE, GROUP_FULL }

data class CollaborationMemberInput(
    val name: String,
    val role: String?,
    val error: CollaborationMemberInputError? = null,
)

/** Mirror daemon validation so the common, actionable failures never disappear into a list refresh. */
fun validateCollaborationMemberInput(
    rawName: String,
    rawRole: String,
    usedNames: Collection<String>,
    editingExisting: Boolean,
    memberCount: Int,
): CollaborationMemberInput {
    val name = normalizeNfkc(rawName).trim().replace(Regex("\\s+"), " ")
    val role = normalizeNfkc(rawRole).trim().takeIf { it.isNotEmpty() }
    val invalidName = name.isEmpty() || name.length > 32 || name.equals("all", ignoreCase = true) ||
        '@' in name || name.any { it.isISOControl() || !(it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '.') }
    val error = when {
        invalidName -> CollaborationMemberInputError.NAME
        usedNames.any { normalizeNfkc(it).trim().equals(name, ignoreCase = true) } -> CollaborationMemberInputError.DUPLICATE
        role != null && (role.length > 80 || role.any(Char::isISOControl)) -> CollaborationMemberInputError.ROLE
        !editingExisting && memberCount >= 8 -> CollaborationMemberInputError.GROUP_FULL
        else -> null
    }
    return CollaborationMemberInput(name, role, error)
}

/**
 * Seed a friendly, editable member label from the configured agent/model and make it unique within one
 * group. Comparisons are case-insensitive, matching the daemon's ConfigureAgentGroupMember validation.
 */
fun suggestedCollaborationMemberName(agent: AgentKind, model: String?, usedNames: Collection<String>): String {
    val modelName = modelLabelForAgent(agent, model).trim()
    val base = modelName.ifBlank { agentName(agent) }
    val used = usedNames.mapTo(HashSet()) { it.trim().lowercase() }
    if (base.lowercase() !in used) return base
    var suffix = 2
    while ("$base $suffix".lowercase() in used) suffix++
    return "$base $suffix"
}

/** Map roster + existing session facts into the only status claims the client can prove. */
fun collaborationMembersUi(
    group: SessionGroup,
    sessions: List<SessionSummary>,
    currentSessionId: String?,
    currentNeedsInput: Boolean,
    online: Boolean,
    needsInputSessionIds: Set<String> = emptySet(),
): List<CollaborationMemberUi> = group.members.sortedBy { it.order }.map { member ->
    val session = sessions.firstOrNull { it.sessionId == member.sessionId }
    val status = when {
        !online -> CollaborationMemberStatus.OFFLINE
        member.sessionId in needsInputSessionIds ||
            (member.sessionId == currentSessionId && currentNeedsInput) -> CollaborationMemberStatus.NEEDS_INPUT
        session?.busy == true -> CollaborationMemberStatus.BUSY
        session?.live == true -> CollaborationMemberStatus.LIVE
        else -> CollaborationMemberStatus.IDLE
    }
    CollaborationMemberUi(
        memberId = member.id,
        sessionId = member.sessionId,
        label = member.name,
        role = member.role,
        agent = member.launchProfile.agent,
        model = session?.model ?: member.launchProfile.model,
        status = status,
    )
}

/**
 * Pure target-state reducer shared by phone and desktop Compose surfaces. Changing groups clears the old
 * target rather than accidentally routing to a member from a stale list; selecting a target requires that
 * the exact stable member id is still present in the supplied group snapshot.
 */
class CollaborationTargetState {
    var target by mutableStateOf<CollaborationTarget?>(null)
        private set

    fun select(groupId: String, member: CollaborationMemberUi) {
        target = CollaborationTarget(groupId, member.memberId, member.sessionId, member.label)
    }

    fun reconcile(groupId: String?, members: List<CollaborationMemberUi>) {
        val held = target ?: return
        val live = members.firstOrNull { it.memberId == held.memberId }
        target = if (groupId == held.groupId && live != null) {
            CollaborationTarget(held.groupId, live.memberId, live.sessionId, live.label)
        } else {
            null
        }
    }

    fun clear() { target = null }
}

/**
 * How loudly one delivery outcome should read.
 *
 * The "uncertain" outcomes are NOT failures: the daemon lost its ledger mid-switch, so the message may
 * well have landed. Painting them danger-red taught the opposite of what the user must do — go and look.
 */
enum class CollaborationNoticeKind { REFUSED, VERIFY }

fun agentGroupDeliveryNoticeKind(code: String?): CollaborationNoticeKind =
    if (code == "delivery_state_lost") CollaborationNoticeKind.VERIFY else CollaborationNoticeKind.REFUSED

/** Turn daemon refusal codes into user-facing copy; raw protocol identifiers never reach the sheet. */
@Composable
fun agentGroupDeliveryErrorText(code: String?): String? = when (code) {
    null -> null
    "target_external_active" -> stringResource(Res.string.ag_error_target_external_active)
    "open_failed" -> stringResource(Res.string.ag_error_open_failed)
    "invalid_group" -> stringResource(Res.string.ag_error_invalid_group)
    "not_member" -> stringResource(Res.string.ag_error_not_member)
    "owner_only" -> stringResource(Res.string.ag_error_owner_only)
    "delivery_state_lost" -> stringResource(Res.string.ag_error_delivery_state_lost)
    "delivery_timeout" -> stringResource(Res.string.ag_error_delivery_timeout)
    else -> stringResource(Res.string.ag_error_delivery_failed)
}

/** Daemon roster refusals, in the member form that caused them. */
@Composable
fun agentGroupRosterErrorText(code: String?): String? = when (code) {
    null -> null
    "duplicate_name" -> stringResource(Res.string.ag_error_duplicate_name)
    "invalid_name" -> stringResource(Res.string.ag_error_invalid_name)
    "invalid_role" -> stringResource(Res.string.ag_error_invalid_role)
    "group_full" -> stringResource(Res.string.ag_error_group_full)
    "invalid_group" -> stringResource(Res.string.ag_error_invalid_group)
    else -> stringResource(Res.string.ag_error_not_member)
}

/** One delivery outcome, in the weight its meaning deserves. VERIFY reads as a task, not as an error. */
@Composable
fun CollaborationDeliveryNotice(code: String, modifier: Modifier = Modifier) {
    val body = agentGroupDeliveryErrorText(code) ?: return
    val verify = agentGroupDeliveryNoticeKind(code) == CollaborationNoticeKind.VERIFY
    val tint = if (verify) Tok.warn else Tok.danger
    Column(
        modifier.fillMaxWidth().background(tint.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
            .border(1.dp, tint.copy(alpha = 0.34f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        if (verify) {
            Text(
                stringResource(Res.string.ag_verify_title), color = tint, fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(body, color = if (verify) Tok.tx2 else tint, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}

/**
 * The in-flight lock. A route/delegate is one indivisible transaction — send, back, stop, switching
 * sessions, clearing the draft and removing attachments are all refused until the target's own PromptAck
 * proves the agent took it. That much refusal has to be *said*, not inferred from dead controls.
 */
@Composable
fun CollaborationLockBanner(targetLabel: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Tok.accent.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
            .border(1.dp, Tok.accent.copy(alpha = 0.34f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(
            stringResource(Res.string.ag_lock_title, targetLabel), color = Tok.accent,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(Res.string.ag_lock_body), color = Tok.tx2, fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The compact member switcher above a collaboration transcript. Each member keeps its own transcript. */
@Composable
fun CollaborationMemberStrip(
    members: List<CollaborationMemberUi>,
    currentMemberId: String?,
    targetMemberId: String?,
    onOpenMember: (CollaborationMemberUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (members.isEmpty()) return
    Column(modifier.fillMaxWidth().background(Tok.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Metric.gutter, end = Metric.gutter, top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.ag_members).uppercase(), color = Tok.muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.weight(1f))
            Text(stringResource(Res.string.ag_independent_transcripts), color = Tok.muted, fontSize = 10.5.sp)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Metric.gutter, vertical = 9.dp),
        ) {
            items(members, key = { it.memberId }) { member ->
                CollaborationMemberChip(
                    member = member,
                    current = member.memberId == currentMemberId,
                    target = member.memberId == targetMemberId,
                    onOpen = { onOpenMember(member) },
                )
            }
        }
    }
}

/** Status word, but only for the states that ask something of the user. LIVE/IDLE stay a quiet dot —
 *  the same "label only what needs attention" rule the agent badges follow. */
@Composable
private fun statusLabel(status: CollaborationMemberStatus): String? = when (status) {
    CollaborationMemberStatus.NEEDS_INPUT -> stringResource(Res.string.ag_status_needs_input)
    CollaborationMemberStatus.BUSY -> stringResource(Res.string.ag_status_busy)
    CollaborationMemberStatus.OFFLINE -> stringResource(Res.string.ag_status_offline)
    CollaborationMemberStatus.LIVE, CollaborationMemberStatus.IDLE -> null
}

private fun statusColor(status: CollaborationMemberStatus) = when (status) {
    CollaborationMemberStatus.NEEDS_INPUT -> Tok.warn
    CollaborationMemberStatus.BUSY -> Tok.ok
    CollaborationMemberStatus.LIVE -> Tok.ok.copy(alpha = 0.7f)
    CollaborationMemberStatus.IDLE -> Tok.muted
    CollaborationMemberStatus.OFFLINE -> Tok.danger
}

/** One member chip. ONE action: open that member. Choosing where a message goes is the composer's job —
 *  a chip that both navigated and re-targeted put two different outcomes under one 34dp touch. */
@Composable
private fun CollaborationMemberChip(
    member: CollaborationMemberUi,
    current: Boolean,
    target: Boolean,
    onOpen: () -> Unit,
) {
    val tint = agentColor(member.agent)
    val statusFull = stringResource(when (member.status) {
        CollaborationMemberStatus.NEEDS_INPUT -> Res.string.ag_status_needs_input
        CollaborationMemberStatus.BUSY -> Res.string.ag_status_busy
        CollaborationMemberStatus.LIVE -> Res.string.ag_status_live
        CollaborationMemberStatus.IDLE -> Res.string.ag_status_idle
        CollaborationMemberStatus.OFFLINE -> Res.string.ag_status_offline
    })
    val loud = statusLabel(member.status)
    val unknownModel = stringResource(Res.string.ag_model_unknown)
    val dot = statusColor(member.status)
    Row(
        Modifier.heightIn(min = Metric.touch).background(
            if (current) tint.copy(alpha = 0.13f) else Tok.raised,
            RoundedCornerShape(12.dp),
        ).border(
            1.dp,
            if (target) Tok.accent else if (current) tint.copy(alpha = 0.45f) else Tok.hair,
            RoundedCornerShape(12.dp),
        ).clickable(role = Role.Button, onClick = onOpen)
            .semantics {
                contentDescription = listOf(
                    member.label,
                    agentName(member.agent),
                    member.model ?: unknownModel,
                    statusFull,
                ).joinToString(", ")
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.width(124.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.label, color = Tok.tx, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (target) {
                    Spacer(Modifier.width(4.dp))
                    Text("@", color = Tok.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (current) Text(stringResource(Res.string.ag_open).uppercase(), color = tint, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                listOfNotNull(loud, member.role?.takeIf { it.isNotBlank() }, agentName(member.agent), member.model).joinToString(" · "),
                color = if (loud == null) Tok.tx2 else dot, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The composer's dispatch row: the one place that answers "where does this message go?".
 *
 * Resting ([target] null) it is a single quiet entry — the composer then behaves exactly as it always has.
 * Armed it states the destination, offers the structured brief as an equal-weight second verb, and lets the
 * user come back to the current session in one tap. There is deliberately no "@me" resting target: a pill
 * that names yourself and changes nothing is noise on every single turn.
 */
@Composable
fun CollaborationDispatchBar(
    target: CollaborationTarget?,
    onPickTarget: () -> Unit,
    onClearTarget: () -> Unit,
    onDelegate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (target == null) {
        Row(
            modifier.heightIn(min = Metric.touch).clip(RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClick = onPickTarget).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("@", color = Tok.tx2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text(stringResource(Res.string.ag_pick_target), color = Tok.tx2, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CollaborationTargetChip(target, onChange = onPickTarget)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Tok.hair, RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button, onClick = onDelegate).padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.ag_delegate_action), color = Tok.tx,
                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.ag_clear_target), color = Tok.muted, fontSize = 11.sp,
                modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onClearTarget).padding(horizontal = 8.dp, vertical = 9.dp),
            )
        }
        Text(
            stringResource(Res.string.ag_target_caption, target.label), color = Tok.muted,
            fontSize = 10.5.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Explicit target pill. Its id stays outside the editable prompt; only the friendly `@label` is shown. */
@Composable
fun CollaborationTargetChip(target: CollaborationTarget, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 36.dp).background(Tok.accent.copy(alpha = 0.11f), RoundedCornerShape(999.dp))
            .border(1.dp, Tok.accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onChange).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("@${target.label}", color = Tok.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Rounded.SwapHoriz, stringResource(Res.string.ag_change_target), tint = Tok.accent, modifier = Modifier.size(15.dp))
    }
}

/** Pick where this message goes. Lists every OTHER member with the same status vocabulary as the strip,
 *  so "who is free / who is stuck on a permission card" is answered at the moment of choosing. */
@Composable
fun CollaborationTargetPickerSheet(
    members: List<CollaborationMemberUi>,
    currentMemberId: String?,
    selectedMemberId: String?,
    onPick: (CollaborationMemberUi) -> Unit,
    onDismiss: () -> Unit,
) {
    PocketSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.ag_target_picker_title), color = Tok.tx, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.ag_route_hint), color = Tok.muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            members.filter { it.memberId != currentMemberId }.forEach { member ->
                val picked = member.memberId == selectedMemberId
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                        .background(Tok.surface)
                        .border(1.dp, if (picked) Tok.accent.copy(alpha = 0.5f) else Tok.hair, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onPick(member); onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(statusColor(member.status), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("@${member.label}", color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(statusLabel(member.status), member.role?.takeIf { it.isNotBlank() }, agentName(member.agent), member.model)
                                .joinToString(" · "),
                            color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (picked) {
                        Icon(
                            Icons.Rounded.Check, stringResource(Res.string.ag_target_selected),
                            tint = Tok.accent, modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text(stringResource(Res.string.cancel), color = Tok.tx2) }
            }
        }
    }
}

/** The empty-roster escape hatch: a collaboration group is created empty, and until now the only way to
 *  fill it was the per-session "move to group" sheet — an entry nobody finds from the group they just made. */
@Composable
fun AddCollaborationMemberSheet(
    candidates: List<SessionSummary>,
    onPick: (SessionSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    PocketSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.ag_add_member_title), color = Tok.tx, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (candidates.isEmpty()) {
                Text(
                    stringResource(Res.string.ag_add_member_empty), color = Tok.tx2, fontSize = 12.5.sp,
                    lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
            candidates.forEach { session ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Tok.surface)
                        .clickable(role = Role.Button) { onPick(session); onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.title ?: session.firstPrompt ?: session.sessionId,
                            color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(agentName(session.agent ?: AgentKind.CLAUDE), session.model).joinToString(" · "),
                            color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text(stringResource(Res.string.cancel), color = Tok.tx2) }
            }
        }
    }
}

/** The zero-member state of a collaboration group, wherever its sessions are listed. */
@Composable
fun CollaborationEmptyRosterRow(onAdd: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
            .then(if (onAdd != null) Modifier.clickable(role = Role.Button, onClick = onAdd) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.ag_members_empty), color = Tok.muted, fontSize = 11.5.sp)
        Spacer(Modifier.width(8.dp))
        if (onAdd != null) {
            Text(
                stringResource(Res.string.ag_add_member_hint), color = Tok.accent,
                fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Edit the daemon-owned, group-unique @ name and optional responsibility for one existing member. */
@Composable
fun ConfigureCollaborationMemberSheet(
    member: CollaborationMemberUi,
    suggestedName: String,
    saving: Boolean,
    error: String? = null,
    usedNames: Collection<String> = emptyList(),
    memberCount: Int = 0,
    editingExisting: Boolean = true,
    onSave: (name: String, role: String?) -> Unit,
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by remember(member.memberId) { mutableStateOf(member.label.ifBlank { suggestedName }) }
    var role by remember(member.memberId) { mutableStateOf(member.role.orEmpty()) }
    val validated = validateCollaborationMemberInput(name, role, usedNames, editingExisting, memberCount)
    val validationError = when (validated.error) {
        CollaborationMemberInputError.NAME -> stringResource(Res.string.ag_error_invalid_name)
        CollaborationMemberInputError.DUPLICATE -> stringResource(Res.string.ag_error_duplicate_name)
        CollaborationMemberInputError.ROLE -> stringResource(Res.string.ag_error_invalid_role)
        CollaborationMemberInputError.GROUP_FULL -> stringResource(Res.string.ag_error_group_full)
        null -> null
    }
    PocketSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.ag_configure_member), color = Tok.tx, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(agentName(member.agent), member.model).joinToString(" · "),
                color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 14.dp),
            )
            BriefField(stringResource(Res.string.ag_member_name), stringResource(Res.string.ag_member_name_hint), name) { name = it }
            BriefField(stringResource(Res.string.ag_member_role), stringResource(Res.string.ag_member_role_hint), role) { role = it }
            error?.let { Text(it, color = Tok.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            validationError?.let { Text(it, color = Tok.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                onRemove?.let { remove ->
                    TextButton(onClick = remove, enabled = !saving) {
                        Text(stringResource(Res.string.ag_remove_member), color = Tok.danger)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onDismiss, enabled = !saving) { Text(stringResource(Res.string.cancel), color = Tok.tx2) }
                TextButton(
                    onClick = { if (validated.error == null) onSave(validated.name, validated.role) },
                    enabled = validated.error == null && !saving,
                ) {
                    Text(
                        stringResource(if (saving) Res.string.ag_saving else Res.string.ag_save_member),
                        color = if (validated.error == null && !saving) Tok.accent else Tok.muted,
                    )
                }
            }
        }
    }
}

/**
 * Structured delegation between two members. Deliberately NOT called a handoff in the UI: this app already
 * uses "handoff" for transferring a live session to another PERSON (invite, takeover, return). This one
 * never leaves the machine and never comes back — the only thing that crosses is the brief below.
 *
 * Source and target are stated outside the four editable fields so the direction is verifiable before
 * dispatch. [onSubmit] cannot fire until all four fields contain real text.
 */
@Composable
fun CollaborationDelegateSheet(
    source: CollaborationMemberUi,
    target: CollaborationMemberUi,
    sending: Boolean,
    error: String? = null,
    onSubmit: (CollaborationBriefDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(source.memberId, target.memberId) { mutableStateOf(CollaborationBriefDraft()) }
    val perLine = stringResource(Res.string.ag_line_hint)
    PocketSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.ag_delegate_title), color = Tok.tx, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.ag_delegate_hint), color = Tok.muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp).background(Tok.surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DelegateEndpoint(source, stringResource(Res.string.ag_from), Modifier.weight(1f))
                Text("→", color = Tok.accent, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 10.dp))
                DelegateEndpoint(target, stringResource(Res.string.ag_to), Modifier.weight(1f))
            }
            BriefField(stringResource(Res.string.ag_objective), stringResource(Res.string.ag_objective_hint), draft.objective) { draft = draft.copy(objective = it) }
            BriefField(stringResource(Res.string.ag_conclusions), stringResource(Res.string.ag_conclusions_hint), draft.conclusions, perLine) { draft = draft.copy(conclusions = it) }
            BriefField(stringResource(Res.string.ag_constraints), stringResource(Res.string.ag_constraints_hint), draft.constraints, perLine) { draft = draft.copy(constraints = it) }
            BriefField(stringResource(Res.string.ag_done_when), stringResource(Res.string.ag_done_when_hint), draft.doneWhen, perLine) { draft = draft.copy(doneWhen = it) }
            error?.let { Text(it, color = Tok.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss, enabled = !sending) { Text(stringResource(Res.string.cancel), color = Tok.tx2) }
                TextButton(
                    onClick = { if (draft.complete && !sending) onSubmit(draft.trimmed()) },
                    enabled = draft.complete && !sending,
                ) { Text(stringResource(if (sending) Res.string.ag_sending else Res.string.ag_delegate_action), color = if (draft.complete && !sending) Tok.accent else Tok.muted) }
            }
        }
    }
}

@Composable
private fun DelegateEndpoint(member: CollaborationMemberUi, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Tok.muted, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
        Text(member.label, color = Tok.tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(agentName(member.agent), member.model).joinToString(" · "),
            color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BriefField(label: String, hint: String, value: String, note: String? = null, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Tok.tx2, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
            note?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, color = Tok.muted, fontSize = 9.5.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(top = 5.dp).background(Tok.surface, RoundedCornerShape(10.dp))
                .border(1.dp, Tok.hair, RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) Text(hint, color = Tok.muted, fontSize = 13.sp, lineHeight = 18.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Tok.tx, fontSize = 13.sp, lineHeight = 18.sp),
                cursorBrush = SolidColor(Tok.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

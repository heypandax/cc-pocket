package dev.ccpocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ccpocket.app.data.PocketRepository
import dev.ccpocket.app.data.ActivityKind
import dev.ccpocket.app.epochMillis
import dev.ccpocket.app.resources.*
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.protocol.LARGE_CONTEXT_WINDOW
import dev.ccpocket.protocol.contextWindowFor
import dev.ccpocket.protocol.BackgroundJob
import dev.ccpocket.protocol.DEFAULT_CONTEXT_WINDOW
import dev.ccpocket.protocol.JobKind
import dev.ccpocket.protocol.JobStatus
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.AgentModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

// ── model + effort option sets (what `--model` / `--effort` accept) ──
// Keep this list to models that OpenAI documents as selectable in Codex. Availability still depends on
// the signed-in account/workspace; the custom-id field below remains the escape hatch for gateways and
// newly rolled-out models between app releases.
internal val CODEX_MODEL_OPTIONS = listOf(
    "gpt-5.6-sol",
    "gpt-5.6-terra",
    "gpt-5.6-luna",
    "gpt-5.5",
    "gpt-5.3-codex-spark",
    "gpt-5.4",
    "gpt-5.4-mini",
) // shared with the desktop ⋯ popover
internal val CURSOR_MODEL_OPTIONS = listOf(
    "auto",
    "composer-2.5",
    "gpt-5.6-sol-medium",
    "gpt-5.6-terra-medium",
    "gpt-5.6-luna-medium",
    "gpt-5.3-codex",
    "claude-fable-5-high",
    "claude-fable-5-thinking-high",
    "claude-opus-4-8-high",
    "claude-sonnet-5-high",
    "gemini-3.1-pro",
) // verified against cursor-agent --list-models; custom id supports every account-specific variant

/** Keep bundled models visible when Cursor's account catalog only returns a partial rollout. */
internal fun mergedCursorModels(live: List<AgentModel>): List<Pair<String, String>> {
    if (live.isNotEmpty()) return live.map { it.name to it.id }
    return CURSOR_MODEL_OPTIONS.map { it to it }
}

internal fun cursorModelForVariant(models: List<AgentModel>, modelId: String?): AgentModel? =
    models.firstOrNull { model -> model.id == modelId || model.variants.any { it.id == modelId } }

internal fun modelFamily(id: String): String = when {
    id.equals("auto", true) -> "Recommended"
    id.contains("fable", true) -> "Fable"
    id.contains("claude", true) || id.contains("opus", true) || id.contains("sonnet", true) -> "Claude"
    id.contains("codex", true) -> "Codex"
    id.startsWith("gpt", true) -> "GPT"
    id.contains("composer", true) -> "Composer"
    id.contains("gemini", true) -> "Gemini"
    else -> "Other"
}

internal fun modelFamilyRank(name: String): Int = when (name) {
    "Current" -> 0
    "Recommended" -> 1
    "Fable" -> 2
    "Claude" -> 3
    "Codex" -> 4
    "GPT" -> 5
    "Composer" -> 6
    "Gemini" -> 7
    else -> 8
}
internal val CLAUDE_MODEL_OPTIONS = listOf("Fable" to "fable", "Opus" to "opus", "Sonnet" to "sonnet", "Haiku" to "haiku") // display name → alias; shared by both shells' pickers
internal val EFFORT_OPTIONS = listOf("low", "medium", "high", "xhigh", "max") // shared: live /effort picker + Settings default

/** Short header alias for a model id: "claude-opus-4-8[1m]" -> "opus". */
fun modelAlias(model: String?): String {
    val m = model?.trim().orEmpty()
    if (m.isEmpty()) return ""
    return m.removePrefix("claude-").takeWhile { it != '-' && it != '[' && it != '_' }.ifBlank { m }
}

/** Compact human token count: 45200 -> "45k", 1000000 -> "1.0M" (one decimal, truncated). */
fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "${(n / 100_000) / 10.0}M" // integer /100k then /10.0 = one truncated decimal
    n >= 1_000 -> "${n / 1000}k"
    else -> n.toString()
}

/** Context-occupancy color ramp, shared by the session sheet's [ContextBar] and the chat statusline:
 *  [base] under 80%, warn to 95%, danger past it. One definition keeps the thresholds in lockstep;
 *  callers pick the calm base (the bar fills with accent, the corner text rests at muted). */
fun contextColor(frac: Float, base: Color = Tok.accent): Color = when {
    frac >= 0.95f -> Tok.danger
    frac >= 0.80f -> Tok.warn
    else -> base
}

// ════════════════════════════════════════════════════════════════════
//  Session info (read-only): model · effort · mode · dir · context bar
// ════════════════════════════════════════════════════════════════════
@Composable
fun SessionInfoSheet(repo: PocketRepository, onDismiss: () -> Unit) {
    PocketSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp, top = 4.dp)) {
            Text(stringResource(Res.string.session_info_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.label_agent), color = Tok.tx2, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    AgentTag(repo.sessionAgent.value ?: AgentKind.CLAUDE, small = false)
                }
                Hairline()
                AboutRow(stringResource(Res.string.label_model), repo.model.value ?: stringResource(Res.string.value_default))
                Hairline()
                AboutRow(stringResource(Res.string.label_effort), repo.effort.value ?: stringResource(Res.string.value_default))
                Hairline()
                AboutRow(stringResource(Res.string.label_mode), MODE_BY[repo.mode.value]?.tech ?: repo.mode.value.name)
            }
            ContextBar(used = repo.contextUsed.value, total = repo.contextWindow.value)
            Column(Modifier.padding(top = 10.dp)) {
                Text(stringResource(Res.string.label_workdir), color = Tok.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                TailPathText(repo.workdir.value ?: "", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ContextBar(used: Long?, total: Long?) {
    Column(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.label_context), color = Tok.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
            // total == null → no known denominator (Codex): show raw occupancy instead of a fake /200k
            val label = when {
                total == null -> if (used == null) "—" else "~${formatTokens(used)}"
                used == null -> "— / ${formatTokens(total)}"
                else -> "~${formatTokens(used)} / ${formatTokens(total)}"
            }
            Text(label, color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        val frac = if (used == null || total == null || total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
        val fill = contextColor(frac)
        Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tok.hair)) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(fill))
        }
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.hair))

// ════════════════════════════════════════════════════════════════════
//  Quick actions: switch model / effort, compact, clear, simplify
// ════════════════════════════════════════════════════════════════════
enum class QuickActionSection { MAIN, MODEL, EFFORT, ACTIVITY, GOAL, REVIEW, SKILLS }

@Composable
fun QuickActionsSheet(
    repo: PocketRepository,
    onTerminal: () -> Unit,
    onMode: () -> Unit,
    onFiles: () -> Unit,
    initialSection: QuickActionSection = QuickActionSection.MAIN,
    onDismiss: () -> Unit,
) {
    var sub by remember(initialSection) { mutableStateOf(initialSection) }
    var clearArmed by remember { mutableStateOf(false) }
    val modelScroll = rememberScrollState()
    PocketSheet(onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp, top = 4.dp)
                .then(
                    if (sub != QuickActionSection.MAIN) Modifier.fillMaxHeight(0.88f).verticalScroll(modelScroll)
                    else Modifier,
                ),
        ) {
            when (sub) {
                QuickActionSection.MAIN -> {
                    Text(stringResource(Res.string.quick_actions_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionRow(stringResource(Res.string.terminal_open)) { onTerminal(); onDismiss() }
                        ActionRow(stringResource(Res.string.qa_files)) { onFiles(); onDismiss() }
                        ActionRow(
                            stringResource(Res.string.activity_title),
                            value = repo.activityEvents.size.takeIf { it > 0 }?.toString(),
                            chevron = true,
                        ) { sub = QuickActionSection.ACTIVITY }
                        ActionRow(stringResource(Res.string.qa_compact)) { repo.compactConversation(); onDismiss() }
                        if (repo.sessionAgent.value != AgentKind.CURSOR) {
                            ActionRow(stringResource(Res.string.qa_branch)) { repo.branchConversation(); onDismiss() }
                        }
                        if (repo.sessionAgent.value == AgentKind.CODEX) {
                            ActionRow(stringResource(Res.string.qa_review_native), chevron = true) { sub = QuickActionSection.REVIEW }
                            ActionRow(stringResource(Res.string.qa_skills), value = repo.codexSkills.size.takeIf { it > 0 }?.toString(), chevron = true) { sub = QuickActionSection.SKILLS }
                            ActionRow(
                                stringResource(Res.string.qa_goal),
                                value = when (repo.codexGoal.value?.status) {
                                    null -> null
                                    "paused" -> stringResource(Res.string.goal_paused)
                                    "complete" -> stringResource(Res.string.goal_complete)
                                    else -> stringResource(Res.string.goal_active)
                                },
                                chevron = true,
                            ) { sub = QuickActionSection.GOAL }
                        }
                        if (repo.hasSimplify()) ActionRow(stringResource(Res.string.qa_simplify)) { repo.sendPrompt("/simplify"); onDismiss() }
                        ActionRow(
                            stringResource(Res.string.qa_clear),
                            value = if (clearArmed) stringResource(Res.string.qa_clear_hint) else null,
                            danger = true,
                        ) {
                            if (clearArmed) { repo.clearConversation(); onDismiss() } else clearArmed = true
                        }
                    }
                }
                QuickActionSection.MODEL -> ModelPicker(repo, onBack = { sub = QuickActionSection.MAIN }, onDone = onDismiss)
                QuickActionSection.EFFORT -> {
                    val cursorModel = cursorModelForVariant(repo.cursorModels, repo.model.value)
                    if (repo.sessionAgent.value == AgentKind.CURSOR && cursorModel != null && cursorModel.variants.isNotEmpty()) {
                        OptionPicker(
                            title = stringResource(Res.string.label_effort),
                            options = cursorModel.variants.map { it.name },
                            selected = cursorModel.variants.firstOrNull { it.id == repo.model.value }?.name,
                            onBack = { sub = QuickActionSection.MAIN },
                        ) { label ->
                            cursorModel.variants.firstOrNull { it.name == label }?.let { repo.switchModel(it.id) }
                            onDismiss()
                        }
                    } else {
                        OptionPicker(
                            title = stringResource(Res.string.label_effort),
                            options = EFFORT_OPTIONS,
                            selected = repo.effort.value,
                            onBack = { sub = QuickActionSection.MAIN },
                        ) { repo.switchEffort(it); onDismiss() }
                    }
                }
                QuickActionSection.ACTIVITY -> ActivityView(repo, onBack = { sub = QuickActionSection.MAIN })
                QuickActionSection.GOAL -> GoalEditor(repo, onBack = { sub = QuickActionSection.MAIN }, onDone = onDismiss)
                QuickActionSection.REVIEW -> ReviewEditor(repo, onBack = { sub = QuickActionSection.MAIN }, onDone = onDismiss)
                QuickActionSection.SKILLS -> SkillsView(repo, onBack = { sub = QuickActionSection.MAIN })
            }
        }
    }
}

@Composable
private fun SkillsView(repo: PocketRepository, onBack: () -> Unit) {
    LaunchedEffect(Unit) { repo.loadCodexSkills() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp))
        Text(stringResource(Res.string.skills_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(stringResource(Res.string.skills_refresh), color = Tok.accent, fontSize = 12.sp, modifier = Modifier.clickable { repo.loadCodexSkills(true) }.padding(6.dp))
    }
    Text(stringResource(Res.string.skills_subtitle), color = Tok.tx2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
    if (repo.codexSkillsLoading.value) CircularProgressIndicator(Modifier.padding(20.dp).size(22.dp), strokeWidth = 2.dp)
    repo.codexSkillsError.value?.let { Text(it, color = Tok.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repo.codexSkills.forEach { skill ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface)
                    .border(1.dp, Tok.hair, RoundedCornerShape(10.dp))
                    .clickable { repo.setCodexSkillEnabled(skill.path, !skill.enabled) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(skill.displayName ?: skill.name, color = Tok.tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(skill.shortDescription ?: skill.description, color = Tok.tx2, fontSize = 11.5.sp, maxLines = 2)
                    Text(skill.scope.uppercase(), color = Tok.muted, fontSize = 9.5.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text(if (skill.enabled) stringResource(Res.string.skills_enabled) else stringResource(Res.string.skills_disabled), color = if (skill.enabled) Tok.ok else Tok.muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ReviewEditor(repo: PocketRepository, onBack: () -> Unit, onDone: () -> Unit) {
    val targets = listOf(
        "uncommittedChanges" to Res.string.review_uncommitted,
        "baseBranch" to Res.string.review_base_branch,
        "commit" to Res.string.review_commit,
        "custom" to Res.string.review_custom,
    )
    var target by remember { mutableStateOf("uncommittedChanges") }
    var value by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp))
        Text(stringResource(Res.string.review_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Text(stringResource(Res.string.review_subtitle), color = Tok.tx2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        targets.forEach { (id, label) ->
            val selected = target == id
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Tok.accent.copy(alpha = 0.12f) else Tok.surface)
                    .border(1.dp, if (selected) Tok.accent else Tok.hair, RoundedCornerShape(10.dp))
                    .clickable { target = id; value = "" }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), color = if (selected) Tok.accent else Tok.tx, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (selected) Text("✓", color = Tok.accent, fontSize = 13.sp)
            }
        }
    }
    if (target != "uncommittedChanges") {
        val label = when (target) {
            "baseBranch" -> Res.string.review_branch_hint
            "commit" -> Res.string.review_commit_hint
            else -> Res.string.review_custom_hint
        }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(stringResource(label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = if (target == "custom") 3 else 1,
            singleLine = target != "custom",
        )
    }
    val enabled = target == "uncommittedChanges" || value.isNotBlank()
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            if (enabled) { repo.startCodexReview(target, value); onDone() }
        }) { Text(stringResource(Res.string.review_start), color = if (enabled) Tok.accent else Tok.muted) }
    }
}

@Composable
private fun GoalEditor(repo: PocketRepository, onBack: () -> Unit, onDone: () -> Unit) {
    val current = repo.codexGoal.value
    var objective by remember(current?.updatedAt) { mutableStateOf(current?.objective.orEmpty()) }
    var budget by remember(current?.updatedAt) { mutableStateOf(current?.tokenBudget?.toString().orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp))
        Text(stringResource(Res.string.goal_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    current?.let { goal ->
        Text(
            stringResource(Res.string.goal_usage, formatTokens(goal.tokensUsed), goalElapsed(goal.timeUsedSeconds)),
            color = Tok.muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 5.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("active" to Res.string.goal_active, "paused" to Res.string.goal_paused, "complete" to Res.string.goal_complete).forEach { (status, label) ->
                val selected = goal.status == status
                Text(
                    stringResource(label), color = if (selected) Tok.base else Tok.tx2, fontSize = 11.5.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Tok.accent else Tok.surface)
                        .clickable { repo.updateCodexGoalStatus(status) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    repo.codexGoalError.value?.let {
        Text(it, color = Tok.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    OutlinedTextField(
        value = objective,
        onValueChange = { objective = it },
        label = { Text(stringResource(Res.string.goal_objective)) },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        minLines = 3,
    )
    OutlinedTextField(
        value = budget,
        onValueChange = { budget = it.filter(Char::isDigit).take(12) },
        label = { Text(stringResource(Res.string.goal_budget)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        if (current != null) TextButton(onClick = { repo.clearCodexGoal(); onDone() }) {
            Text(stringResource(Res.string.goal_clear), color = Tok.danger)
        }
        TextButton(onClick = {
            if (objective.isNotBlank()) { repo.setCodexGoal(objective, budget.toLongOrNull()); onDone() }
        }) { Text(stringResource(Res.string.goal_save), color = if (objective.isBlank()) Tok.muted else Tok.accent) }
    }
}

private fun goalElapsed(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}

@Composable
private fun ActivityView(repo: PocketRepository, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp))
        Text(stringResource(Res.string.activity_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Text(stringResource(Res.string.activity_subtitle), color = Tok.tx2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
    if (repo.activityEvents.isEmpty()) {
        Text(stringResource(Res.string.activity_empty), color = Tok.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repo.activityEvents.asReversed().take(100).forEach { event ->
            val color = when { event.ok == false -> Tok.danger; event.ok == true -> Tok.ok; else -> Tok.accent }
            val kind = when (event.kind) {
                ActivityKind.PERMISSION -> Res.string.activity_kind_permission
                ActivityKind.TOOL -> Res.string.activity_kind_tool
                ActivityKind.TURN -> Res.string.activity_kind_turn
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface)
                    .border(1.dp, Tok.hair, RoundedCornerShape(10.dp)).padding(11.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(color))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(kind) + " · " + event.title, color = Tok.tx, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    event.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, maxLines = 2)
                    }
                }
                Text(relativeTime(event.at), color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, value: String? = null, danger: Boolean = false, chevron: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tok.surface)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (danger) Tok.danger else Tok.tx, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1) }
        if (chevron) Text(" ›", color = Tok.muted, fontSize = 16.sp)
    }
}

@Composable
private fun OptionPicker(title: String, options: List<String>, selected: String?, onBack: () -> Unit, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp))
        Text(title, color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { opt ->
            val isSel = opt.equals(selected, ignoreCase = true)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) Tok.accent.copy(alpha = 0.12f) else Tok.surface)
                    .clickable { onPick(opt) }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(opt, color = if (isSel) Tok.accent else Tok.tx, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (isSel) Text("✓", color = Tok.accent, fontSize = 14.sp)
            }
        }
    }
}

/** One row in the [ModelPicker]: a display [name], the `--model` value [pick] shown in mono as [id], and a
 *  context-window pill ([ctx], filled terracotta when [big]). Uses the app's real model aliases, not invented ids. */
private data class ModelChoice(val name: String, val id: String, val pick: String, val ctx: String, val big: Boolean, val unavailable: Boolean = false)

/** Context-window pill — filled terracotta for a 1M window, muted outline otherwise. */
@Composable
private fun CtxPill(ctx: String, big: Boolean) {
    Text(
        ctx, color = if (big) Tok.base else Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .then(if (big) Modifier.background(Tok.accent) else Modifier.border(1.dp, Tok.hair, RoundedCornerShape(999.dp)))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Model picker (design cc-pocket/Model Picker.html) — reached from Quick actions → Model (NOT a new top-bar
 * button; the bar is already busy). Rich rows: display name + mono `--model` value + a 1M/200K context pill +
 * a check. Tapping starts a switch: the row shows a spinner while the daemon relaunches; the sheet closes once
 * the model is re-announced (or after a short timeout, so it never hangs). Claude uses the real opus/sonnet/haiku
 * aliases; Codex sessions list Codex model ids.
 */
@Composable
private fun ModelPicker(repo: PocketRepository, onBack: () -> Unit, onDone: () -> Unit) {
    val agent = repo.sessionAgent.value ?: AgentKind.CLAUDE
    LaunchedEffect(agent) {
        if (agent == AgentKind.CURSOR) repo.refreshCursorModels()
    }
    val selectedRaw = if (agent != AgentKind.CLAUDE) repo.model.value else modelAlias(repo.model.value)
    val selected = if (agent == AgentKind.CURSOR) {
        cursorModelForVariant(repo.cursorModels, selectedRaw)?.id ?: selectedRaw
    } else selectedRaw
    // On the first open, don't flash the small bundled catalog and replace it seconds later with Cursor's
    // account catalog. Keep only the optimistic current row until discovery completes; bundled models are
    // used only after an explicit discovery error.
    val cursorWaiting = agent == AgentKind.CURSOR && repo.cursorModels.isEmpty() && repo.cursorModelsError.value == null
    val choices = when (agent) {
        AgentKind.CODEX -> CODEX_MODEL_OPTIONS.map { ModelChoice(it, it, it, "", false) }
        AgentKind.CURSOR -> {
            if (cursorWaiting) {
                val id = selected?.takeIf { it.isNotBlank() } ?: "auto"
                listOf(ModelChoice(if (id == "auto") "Auto (default)" else id, id, id, "", false))
            } else {
                mergedCursorModels(repo.cursorModels.toList())
                    .map { (name, id) -> ModelChoice(name, id, id, "", false) }
            }
        }
        AgentKind.CLAUDE -> CLAUDE_MODEL_OPTIONS.map { (name, alias) ->
            val big = contextWindowFor(alias) == LARGE_CONTEXT_WINDOW
            ModelChoice(name, alias, alias, if (big) "1M" else "200K", big)
        }
    }
    var switchingTo by remember { mutableStateOf<String?>(null) }
    var query by remember(agent) { mutableStateOf("") }
    val visibleChoices = choices.filter {
        query.isBlank() || it.name.contains(query.trim(), true) || it.id.contains(query.trim(), true)
    }
    val sections = if (agent == AgentKind.CURSOR) {
        // Cursor already returns a deliberate account-specific order. Preserve it exactly; only lift the active
        // row into a small Current section so it remains obvious, then show every other row in provider order.
        val current = visibleChoices.filter { it.pick.equals(selected, true) }
        buildList {
            if (current.isNotEmpty()) add("Current" to current)
            val rest = visibleChoices.filterNot { it.pick.equals(selected, true) }
            if (rest.isNotEmpty()) add("Cursor models" to rest)
        }
    } else {
        visibleChoices.groupBy { if (it.pick.equals(selected, true)) "Current" else modelFamily(it.id) }
            .toList().sortedBy { (name, _) -> modelFamilyRank(name) }
    }
    // close once the daemon confirms the switch (model re-announced through SessionLive)…
    LaunchedEffect(switchingTo, repo.model.value) {
        val target = switchingTo ?: return@LaunchedEffect
        val now = if (agent != AgentKind.CLAUDE) repo.model.value else modelAlias(repo.model.value)
        // raw compare too: a custom id ("kimi-k2…") never alias-matches, but the daemon echoes it verbatim
        if (now.equals(target, ignoreCase = true) || repo.model.value?.equals(target, ignoreCase = true) == true) onDone()
    }
    // …or after a short timeout, so a silent relaunch never leaves the sheet stuck spinning
    LaunchedEffect(switchingTo) { if (switchingTo != null) { delay(4000); onDone() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(enabled = switchingTo == null, onClick = onBack).padding(end = 4.dp))
        Text(stringResource(Res.string.qa_model), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cursorWaiting) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(15.dp), color = Tok.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("Loading models available to this Cursor account…", color = Tok.tx2, fontSize = 12.5.sp)
            }
        }
        if (choices.size > 6) {
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text("Search model or ID", color = Tok.muted, fontSize = 13.sp) },
                leadingIcon = { Text("⌕", color = Tok.muted, fontSize = 18.sp) },
                textStyle = TextStyle(color = Tok.tx, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        if (agent == AgentKind.CURSOR && repo.cursorModelsError.value != null) {
            Text(
                "Live model discovery failed — showing bundled models. You can still enter a custom model ID.",
                color = Tok.warn, fontSize = 11.5.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        sections.forEach { (section, models) ->
            Text(
                section.uppercase(), color = if (section == "Current") Tok.accent else Tok.muted,
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 1.dp),
            )
            models.forEach { c ->
            val isSel = c.pick.equals(selected, ignoreCase = true)
            val isSwitching = switchingTo?.equals(c.pick, ignoreCase = true) == true
            val raised = isSwitching || (isSel && switchingTo == null)
            val dimmed = (switchingTo != null && !isSwitching) || c.unavailable
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (raised) Tok.raised else Color.Transparent)
                    .then(if (raised) Modifier.border(1.dp, Tok.hair, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable(enabled = switchingTo == null && !c.unavailable) { switchingTo = c.pick; repo.switchModel(c.pick) }
                    .alpha(if (dimmed) 0.45f else 1f)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, color = Tok.tx, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (c.unavailable) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(Res.string.model_not_installed), color = Tok.muted, fontSize = 10.5.sp,
                                modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, Tok.hair, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.id, color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, maxLines = 1)
                        if (c.ctx.isNotEmpty()) { Spacer(Modifier.width(8.dp)); CtxPill(c.ctx, c.big) }
                    }
                }
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    when {
                        isSwitching -> CircularProgressIndicator(Modifier.size(17.dp), color = Tok.accent, strokeWidth = 2.dp)
                        isSel -> Text("✓", color = Tok.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
        if (visibleChoices.isEmpty()) {
            Text("No matching models", color = Tok.muted, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
        }
    }
    // Custom model id (issue #54): third-party gateways (cc-switch presets etc.) route ids a fixed list
    // can't know, and `--model` passes any string through — so hand that power to the user. Prefilled when
    // the session already runs an id outside the presets, with the same ✓/spinner the preset rows use.
    val presetActive = choices.any { it.pick.equals(selected, ignoreCase = true) }
    val customActive = !presetActive && !repo.model.value.isNullOrBlank()
    var custom by remember { mutableStateOf(if (customActive) repo.model.value.orEmpty() else "") }
    Column(Modifier.padding(top = 12.dp)) {
        Text(stringResource(Res.string.model_custom_label), color = Tok.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                custom, { custom = it },
                placeholder = { Text(stringResource(Res.string.model_custom_hint), color = Tok.muted, fontSize = 12.5.sp) },
                singleLine = true, enabled = switchingTo == null,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Tok.tx),
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                val t = custom.trim()
                val isSwitchingCustom = switchingTo != null && switchingTo.equals(t, ignoreCase = true) && !presetActive
                when {
                    isSwitchingCustom -> CircularProgressIndicator(Modifier.size(17.dp), color = Tok.accent, strokeWidth = 2.dp)
                    customActive && t.equals(repo.model.value, ignoreCase = true) && switchingTo == null ->
                        Text("✓", color = Tok.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    t.isNotEmpty() && switchingTo == null -> Text(
                        "→", color = Tok.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { switchingTo = t; repo.switchModel(t) }.padding(6.dp),
                    )
                }
            }
        }
    }
    Column(Modifier.padding(top = 14.dp)) {
        Hairline()
        Box(Modifier.padding(top = 12.dp)) {
            if (switchingTo != null) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(13.dp), color = Tok.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.model_switching), color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
            } else Text(stringResource(Res.string.model_switch_hint), color = Tok.muted, fontSize = 12.5.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Background tasks: composer strip + expandable list
// ════════════════════════════════════════════════════════════════════
/** Compact strip above the composer; shown only while ≥1 job is RUNNING. Tap to expand. */
@Composable
fun BackgroundJobsStrip(jobs: List<BackgroundJob>, onClick: () -> Unit) {
    val running = jobs.count { it.status == JobStatus.RUNNING }
    if (running == 0) return
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(12.dp), color = Tok.accent, strokeWidth = 1.5.dp)
        Text(stringResource(Res.string.bg_running, running), color = Tok.tx2, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("⌃", color = Tok.muted, fontSize = 13.sp)
    }
}

@Composable
fun BackgroundJobsSheet(jobs: List<BackgroundJob>, onStop: (BackgroundJob) -> Unit, onDismiss: () -> Unit) {
    PocketSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp, top = 4.dp)) {
            Text(stringResource(Res.string.bg_title), color = Tok.tx, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            // running first, then most-recently-updated. Sorted straight in composition — [jobs] is one
            // SnapshotStateList instance mutated in place, so an instance-keyed remember would compute once
            // per sheet-open and freeze the rows (a stopped job kept showing RUNNING until reopen).
            val sorted = jobs.sortedWith(compareByDescending<BackgroundJob> { it.status == JobStatus.RUNNING }.thenByDescending { it.lastUpdate })
            Column(Modifier.padding(top = 12.dp).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sorted.forEach { JobRow(it, onStop) }
            }
            // discoverability for the long-press stop (issue #80): a quiet hint, only while something can be
            // stopped — keeps the affordance off an always-visible per-row button as the issue asks
            if (jobs.any { it.status == JobStatus.RUNNING }) {
                Text(
                    stringResource(Res.string.job_stop_hint), color = Tok.muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // combinedClickable (long-press)
@Composable
private fun JobRow(job: BackgroundJob, onStop: (BackgroundJob) -> Unit) {
    val running = job.status == JobStatus.RUNNING
    var confirmStop by remember(job.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface)
            .border(1.dp, Tok.hair, RoundedCornerShape(10.dp))
            // stop lives in a long-press (not an always-visible button, per issue #80) and only on a RUNNING
            // row — a settled job has nothing to stop. The confirm guards a costly real build.
            .then(if (running) Modifier.combinedClickable(onClick = {}, onLongClick = { confirmStop = true }) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (running) CircularProgressIndicator(Modifier.size(13.dp), color = Tok.accent, strokeWidth = 1.5.dp)
        else Box(Modifier.size(8.dp).clip(CircleShape).background(jobStatusColor(job.status)))
        Column(Modifier.weight(1f)) {
            Text(job.label, color = Tok.tx, fontSize = 13.sp, maxLines = 2)
            Text(jobKindLabel(job.kind), color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        // Option (a): a RUNNING job's right-side label is its ticking elapsed time (a moving "3h12m" already
        // implies running), so the status word never fights the truncated command for width; past ~1h it
        // turns warn-coloured to flag a possibly-stuck task. A settled job keeps its status word.
        if (running) {
            val (elapsed, warn) = rememberJobElapsed(job.startedAt)
            Text(elapsed, color = if (warn) Tok.warn else Tok.accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(jobStatusLabel(job.status), color = jobStatusColor(job.status), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
    if (confirmStop) {
        JobStopConfirm(job, onConfirm = { confirmStop = false; onStop(job) }, onDismiss = { confirmStop = false })
    }
}

/** Confirm stopping a running task — costly to lose a real build, so guard it (issue #80). */
@Composable
private fun JobStopConfirm(job: BackgroundJob, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.raised,
        titleContentColor = Tok.tx,
        textContentColor = Tok.tx2,
        title = { Text(stringResource(Res.string.job_stop_title)) },
        text = { Text(stringResource(Res.string.job_stop_confirm, job.label), color = Tok.tx2, fontSize = 14.sp, lineHeight = 21.sp) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(Res.string.job_stop_action), color = Tok.danger) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel), color = Tok.muted) } },
    )
}

/** Compact elapsed since [startedAt] ("42s" / "12m" / "3h12m"), ticking each second while composed, plus
 *  whether it has crossed the ~1h warn threshold. Reads the daemon's wall-clock [BackgroundJob.startedAt]
 *  (matches [epochMillis] on every platform), so it stays accurate even when the phone attaches mid-run. */
@Composable
private fun rememberJobElapsed(startedAt: Long): Pair<String, Boolean> {
    var now by remember(startedAt) { mutableStateOf(epochMillis()) }
    LaunchedEffect(startedAt) {
        while (true) { delay(1000); now = epochMillis() }
    }
    val elapsedMs = (now - startedAt).coerceAtLeast(0)
    return fmtJobDuration(elapsedMs) to (elapsedMs >= JOB_WARN_MS)
}

/** "42s" under a minute, "12m" under an hour, "3h12m" beyond — a compact "is it stuck?" readout. */
internal fun fmtJobDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h${m}m"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

/** Past ~1h a still-running task is likely stuck (the issue #80 gcloud-auth case) — its duration turns warn. */
private const val JOB_WARN_MS = 60 * 60 * 1000L

@Composable
private fun jobKindLabel(kind: JobKind): String = stringResource(
    when (kind) {
        JobKind.BASH_BACKGROUND -> Res.string.job_kind_bash
        JobKind.SUBAGENT -> Res.string.job_kind_subagent
        JobKind.MONITOR -> Res.string.job_kind_monitor
    },
)

@Composable
private fun jobStatusLabel(status: JobStatus): String = stringResource(
    when (status) {
        JobStatus.RUNNING -> Res.string.job_running
        JobStatus.DONE -> Res.string.job_done
        JobStatus.FAILED -> Res.string.job_failed
        JobStatus.KILLED -> Res.string.job_killed
    },
)

private fun jobStatusColor(status: JobStatus): Color = when (status) {
    JobStatus.RUNNING -> Tok.accent
    JobStatus.DONE -> Tok.ok
    JobStatus.FAILED -> Tok.danger
    JobStatus.KILLED -> Tok.muted
}

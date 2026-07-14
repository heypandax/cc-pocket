package dev.ccpocket.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ccpocket.app.data.ConnPhase
import dev.ccpocket.app.data.PocketRepository
import dev.ccpocket.app.resources.Res
import dev.ccpocket.app.resources.usage_by_model
import dev.ccpocket.app.resources.usage_cache
import dev.ccpocket.app.resources.usage_claude_dashboard_hint
import dev.ccpocket.app.resources.usage_claude_limits
import dev.ccpocket.app.resources.usage_claude_no_snapshot
import dev.ccpocket.app.resources.usage_claude_plan
import dev.ccpocket.app.resources.usage_claude_rate_limited
import dev.ccpocket.app.resources.usage_claude_session
import dev.ccpocket.app.resources.usage_claude_snapshot
import dev.ccpocket.app.resources.usage_claude_weekly
import dev.ccpocket.app.resources.usage_claude_weekly_opus
import dev.ccpocket.app.resources.usage_codex_credits
import dev.ccpocket.app.resources.usage_codex_credits_unlimited
import dev.ccpocket.app.resources.usage_codex_limits
import dev.ccpocket.app.resources.usage_codex_plan
import dev.ccpocket.app.resources.usage_codex_primary
import dev.ccpocket.app.resources.usage_codex_rate_limited
import dev.ccpocket.app.resources.usage_codex_resets_in
import dev.ccpocket.app.resources.usage_codex_resets_on
import dev.ccpocket.app.resources.usage_codex_secondary
import dev.ccpocket.app.resources.usage_codex_weekly_unavailable
import dev.ccpocket.app.resources.usage_codex_snapshot
import dev.ccpocket.app.resources.usage_codex_dashboard_hint
import dev.ccpocket.app.resources.usage_codex_no_snapshot
import dev.ccpocket.app.resources.usage_codex_remaining
import dev.ccpocket.app.resources.usage_codex_reset_credits
import dev.ccpocket.app.resources.usage_codex_reset_action
import dev.ccpocket.app.resources.usage_codex_reset_confirm_title
import dev.ccpocket.app.resources.usage_codex_reset_confirm_body
import dev.ccpocket.app.resources.usage_codex_reset_cancel
import dev.ccpocket.app.resources.usage_codex_reset_confirm
import dev.ccpocket.app.resources.usage_codex_reset_working
import dev.ccpocket.app.resources.usage_codex_reset_success
import dev.ccpocket.app.resources.usage_codex_reset_nothing
import dev.ccpocket.app.resources.usage_codex_reset_no_credit
import dev.ccpocket.app.resources.usage_codex_reset_already
import dev.ccpocket.app.resources.usage_codex_reset_error
import dev.ccpocket.app.resources.usage_cost
import dev.ccpocket.app.resources.usage_empty
import dev.ccpocket.app.resources.usage_empty_hint
import dev.ccpocket.app.resources.usage_empty_range
import dev.ccpocket.app.resources.usage_less
import dev.ccpocket.app.resources.usage_more
import dev.ccpocket.app.resources.usage_na
import dev.ccpocket.app.resources.usage_offline
import dev.ccpocket.app.resources.usage_peak
import dev.ccpocket.app.resources.usage_per_day
import dev.ccpocket.app.resources.usage_per_hour
import dev.ccpocket.app.resources.usage_period_range
import dev.ccpocket.app.resources.usage_period_today
import dev.ccpocket.app.resources.usage_requests
import dev.ccpocket.app.resources.usage_live_refresh
import dev.ccpocket.app.resources.usage_refresh
import dev.ccpocket.app.resources.usage_title
import dev.ccpocket.app.resources.usage_tokens
import dev.ccpocket.app.resources.usage_trend
import dev.ccpocket.app.resources.weekday_sun
import dev.ccpocket.app.resources.weekday_mon
import dev.ccpocket.app.resources.weekday_tue
import dev.ccpocket.app.resources.weekday_wed
import dev.ccpocket.app.resources.weekday_thu
import dev.ccpocket.app.resources.weekday_fri
import dev.ccpocket.app.resources.weekday_sat
import dev.ccpocket.app.resources.usage_vs_yesterday
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.ClaudeLimits
import dev.ccpocket.protocol.CodexLimits
import dev.ccpocket.protocol.Usage
import dev.ccpocket.protocol.UsageDay
import dev.ccpocket.protocol.UsageModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/** Two-decimal money for the cost card ("$4.20"), no platform locale formatter needed. */
private fun money(v: Double): String {
    val cents = round(v * 100).toLong()
    return "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/**
 * Usage — a calm token dashboard (design issue #26, redesigned #89). Reached from Settings. Four states:
 * loading / offline / empty / populated. The range switch (Today/7d/30d) re-fetches and re-scopes the whole
 * summary: the Tokens hero is the WINDOW total (sum of the trend), with its range baked into the label and a
 * period sub-caption, so the headline and the trend tell one story. The three sub-metrics the daemon only
 * knows for today (requests / est. cost / cache hit) stay explicitly "· today"-labeled — never ambiguous.
 * All views branch on the reply's own shape (u.days.size), so a stale reply keeps rendering coherently while
 * the next fetch is in flight. Data is aggregated by the daemon from Claude/Codex transcripts.
 */
@Composable
fun UsageScreen(repo: PocketRepository, onBack: () -> Unit) {
    dev.ccpocket.app.SystemBackHandler(enabled = true) { onBack() }
    var days by remember { mutableStateOf(1) } // default to "Today"
    var timedOut by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(days, refreshGeneration) {
        timedOut = false
        var first = true
        while (true) {
            repo.fetchUsage(days)
            kotlinx.coroutines.delay(10_000)
            // Only the first request can turn the screen into the explicit timeout state. Once data has
            // landed, a later slow refresh keeps the last honest snapshot instead of flashing Offline.
            if (first && repo.usage.value == null) timedOut = true
            first = false
        }
    }

    val u = repo.usage.value
    val connected = repo.phase.value == ConnPhase.Ready

    Column(Modifier.fillMaxSize().background(Tok.base)) {
        // header
        Column(Modifier.fillMaxWidth().background(Tok.base)) {
            val refreshLabel = stringResource(Res.string.usage_refresh)
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ onBack() }) { Text("←", color = Tok.tx2, fontSize = 18.sp) }
                Text(stringResource(Res.string.usage_title), color = Tok.tx, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "↻",
                    color = Tok.codex,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = refreshLabel }
                        .clip(RoundedCornerShape(999.dp)).clickable {
                        refreshGeneration++
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(999.dp)).padding(2.dp)) {
                    for ((label, d) in listOf("Today" to 1, "7d" to 7, "30d" to 30)) {
                        val on = d == days
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).then(if (on) Modifier.background(Tok.accent) else Modifier)
                                .clickable { days = d }.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text(label, color = if (on) Tok.base else Tok.tx2, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.hair))
        }

        when {
            u != null && (u.tokensToday > 0 || u.models.isNotEmpty() || u.days.any { it.tokens > 0 } || u.codexLimits != null || u.claudeLimits != null) ->
                Populated(u, repo.codexResetting.value, repo.codexResetOutcome.value, repo.codexResetError.value) { showResetConfirm = true }
            u != null -> Empty(u.days.size)
            !connected || timedOut -> Offline()
            else -> Loading()
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(Res.string.usage_codex_reset_confirm_title)) },
            text = { Text(stringResource(Res.string.usage_codex_reset_confirm_body)) },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(Res.string.usage_codex_reset_cancel)) }
            },
            confirmButton = {
                TextButton(onClick = { showResetConfirm = false; repo.consumeCodexLimitReset() }) {
                    Text(stringResource(Res.string.usage_codex_reset_confirm), color = Tok.warn)
                }
            },
        )
    }
}

@Composable
private fun Populated(u: Usage, resetBusy: Boolean, resetOutcome: String?, resetError: String?, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Range-scoped headline: the Tokens hero is the WINDOW total (== the sum of the trend it sits above),
        // so the top number and the chart never contradict, and switching Today/7d/30d visibly changes it.
        // Derive the scope tag from the reply's own span (u.days.size), mirroring the header toggle's tokens —
        // this keeps a stale reply coherent mid-fetch. Today's reply is span 1; 7d/30d fill the whole window.
        val n = u.days.size
        val windowTokens = u.days.sumOf { it.tokens }
        val scope = if (n <= 1) "today" else "${n}d"

        // Neutral day-over-day momentum, only when both days are real activity: guarding today>0 && yesterday>0
        // avoids the old "quiet/partial today → ↓100%" trap (which the previous card mis-colored green). A
        // window-over-window delta ("vs prev 7d") would need the daemon to also send the prior window — not
        // available today, so we show the honest day-over-day note and flag the aggregate as a follow-up.
        val today = u.days.lastOrNull()?.tokens ?: 0L
        val yesterday = u.days.getOrNull(n - 2)?.tokens
        val deltaPct = if (n >= 2 && today > 0 && yesterday != null && yesterday > 0)
            (((today - yesterday) * 100) / yesterday).toInt() else null
        // Window has history but nothing today yet — say so calmly instead of a scary ↓ delta.
        val zeroToday = n >= 2 && today == 0L

        HeroCard(windowTokens, scope, periodCaption(u), deltaPct, zeroToday)

        Spacer(Modifier.height(10.dp))
        LimitsPager(u.codexLimits, u.claudeLimits, resetBusy, resetOutcome, resetError, onReset)

        // The three metrics the daemon only knows for TODAY.
        // is unmistakable even while the hero above reads a wider window. A missing sub-metric shows a labeled
        // "not available yet" placeholder — never a bare "—".
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), todayLabel(stringResource(Res.string.usage_requests)), u.requestsToday.toString())
            StatCard(Modifier.weight(1f), todayLabel(stringResource(Res.string.usage_cost)), u.costUsdToday?.let { money(it) })
        }
        Spacer(Modifier.height(10.dp))
        StatCard(Modifier.fillMaxWidth(), todayLabel(stringResource(Res.string.usage_cache)), u.cacheHitPct?.let { "$it%" }, arcPct = u.cacheHitPct)

        // Each range answers its own question: Today → hourly bars, 7d → daily bars, 30d → a calendar heatmap.
        // Branch on the reply's own shape (span == days.size), not the selected tab — while a fetch is in flight
        // the stale reply keeps rendering as its own coherent view. A span-1 reply without hours (older daemon)
        // shows no trend at all: one flat day-block would only repeat the stat cards.
        val hrs = if (u.days.size == 1) u.hours else null
        if (hrs != null || u.days.size >= 2) {
            SLabel(stringResource(Res.string.usage_trend), right = stringResource(if (hrs != null) Res.string.usage_per_hour else Res.string.usage_per_day))
            when {
                hrs != null -> { HourlyBars(hrs); PeakRow(hrs.maxByOrNull { it.tokens }) }
                u.days.size >= 30 -> Heatmap(u.days) // draws its own peak caption + less→more legend under the grid
                else -> { Bars(u.days); PeakRow(u.days.maxByOrNull { it.tokens }) }
            }
        }

        if (u.models.isNotEmpty()) {
            SLabel(stringResource(Res.string.usage_by_model))
            val max = u.models.maxOf { it.tokens }.coerceAtLeast(1)
            for (m in u.models) ModelRow(m, max)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** The limits area: Codex on the first page (unchanged look), swipe right-to-left for the Claude page.
 *  Two indicator dots below make the second page discoverable. Each page keeps its own unavailable pane. */
@Composable
private fun LimitsPager(
    codex: CodexLimits?,
    claude: ClaudeLimits?,
    resetBusy: Boolean = false,
    resetOutcome: String? = null,
    resetError: String? = null,
    onReset: () -> Unit = {},
) {
    val pager = rememberPagerState { 2 }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(pager, pageSpacing = 10.dp, verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> codex?.let { CodexLimitsCard(it, resetBusy, resetOutcome, resetError, onReset) } ?: CodexLimitsUnavailableCard()
                else -> claude?.let { ClaudeLimitsCard(it) } ?: ClaudeLimitsUnavailableCard()
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(2) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(6.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (pager.currentPage == i) Tok.tx2 else Tok.raised),
                )
            }
        }
    }
}

@Composable
private fun ClaudeLimitsUnavailableCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tok.surface)
            .border(1.dp, Tok.hair, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.usage_claude_limits), color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.usage_claude_no_snapshot), color = Tok.tx2, fontSize = 12.sp, lineHeight = 18.sp)
        Text(stringResource(Res.string.usage_claude_dashboard_hint), color = Tok.muted, fontSize = 11.5.sp, lineHeight = 17.sp)
    }
}

/** The Claude twin of [CodexLimitsCard]: same frame, Claude accent, the account's 5-hour + weekly
 *  windows (+ the Opus weekly window Max plans report) straight from the daemon's live snapshot. */
@Composable
private fun ClaudeLimitsCard(limits: ClaudeLimits) {
    val windows = listOfNotNull(
        limits.session?.let { Res.string.usage_claude_session to it },
        limits.weekly?.let { Res.string.usage_claude_weekly to it },
        limits.weeklyOpus?.let { Res.string.usage_claude_weekly_opus to it },
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.usage_claude_limits), color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            limits.planType?.takeIf { it.isNotBlank() }?.let { plan ->
                Text(
                    stringResource(Res.string.usage_claude_plan, plan.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }),
                    color = Tok.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Tok.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(stringResource(Res.string.usage_live_refresh), color = Tok.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        for ((label, w) in windows) {
            LimitRow(stringResource(label), w.usedPercent, w.windowMinutes, w.resetsAt, Tok.accent)
        }
        if (windows.any { it.second.usedPercent >= 100.0 }) {
            Text(stringResource(Res.string.usage_claude_rate_limited), color = Tok.warn, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        limits.capturedAt?.let { at ->
            Text(stringResource(Res.string.usage_claude_snapshot, relativeTime(at)), color = Tok.muted, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.hair))
        Text(stringResource(Res.string.usage_claude_dashboard_hint), color = Tok.muted, fontSize = 11.5.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun CodexLimitsUnavailableCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tok.surface)
            .border(1.dp, Tok.hair, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.usage_codex_limits), color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.usage_codex_no_snapshot), color = Tok.tx2, fontSize = 12.sp, lineHeight = 18.sp)
        Text(stringResource(Res.string.usage_codex_dashboard_hint), color = Tok.muted, fontSize = 11.5.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun CodexLimitsCard(
    limits: CodexLimits, resetBusy: Boolean, resetOutcome: String?, resetError: String?, onReset: () -> Unit,
) {
    // Codex may return a weekly-only account as `primary` (not `secondary`). Classify by duration;
    // primary/secondary describe ordering, not a permanent 5h/week meaning.
    val weekly = listOfNotNull(limits.primary, limits.secondary)
        .filter { it.windowMinutes >= 24 * 60 }
        .maxByOrNull { it.windowMinutes }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.usage_codex_limits), color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            limits.planType?.takeIf { it.isNotBlank() }?.let { plan ->
                Text(
                    stringResource(Res.string.usage_codex_plan, plan.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }),
                    color = Tok.codex,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Tok.codex.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(stringResource(Res.string.usage_live_refresh), color = Tok.codex, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        // The ChatGPT usage surfaces currently expose the account's weekly allowance. Codex rollout
        // files may also contain a `primary` 300-minute window, but that is a CLI-local rate-limit
        // snapshot and must not be presented as the account's official allowance: it can outlive a
        // reset, belong to an older login, or simply be omitted by the official account UI.
        weekly?.let {
            LimitRow(stringResource(Res.string.usage_codex_secondary), it.usedPercent, it.windowMinutes, it.resetsAt, Tok.codex)
        } ?: Text(
            stringResource(Res.string.usage_codex_weekly_unavailable),
            color = Tok.tx2,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        limits.credits?.takeIf { it.hasCredits || it.unlimited || !it.balance.isNullOrBlank() && it.balance != "0" }?.let { c ->
            val text = when {
                c.unlimited -> stringResource(Res.string.usage_codex_credits_unlimited)
                c.hasCredits && !c.balance.isNullOrBlank() -> stringResource(Res.string.usage_codex_credits, c.balance!!)
                else -> null
            }
            text?.let { Text(it, color = Tok.tx2, fontSize = 12.sp) }
        }
        limits.resetCreditsAvailable?.let { count ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Tok.raised.copy(alpha = 0.55f))
                    .border(1.dp, Tok.hair, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.usage_codex_reset_credits, count), color = Tok.tx, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(if (resetBusy) Res.string.usage_codex_reset_working else Res.string.usage_codex_reset_action),
                        color = if (count > 0 && !resetBusy) Tok.codex else Tok.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (count > 0 && !resetBusy) Tok.codex.copy(alpha = 0.13f) else Tok.surface)
                            .clickable(enabled = count > 0 && !resetBusy, onClick = onReset)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
                val resultText = when (resetOutcome) {
                    "reset" -> stringResource(Res.string.usage_codex_reset_success)
                    "nothingToReset" -> stringResource(Res.string.usage_codex_reset_nothing)
                    "noCredit" -> stringResource(Res.string.usage_codex_reset_no_credit)
                    "alreadyRedeemed" -> stringResource(Res.string.usage_codex_reset_already)
                    "error" -> stringResource(Res.string.usage_codex_reset_error, resetError ?: "")
                    else -> null
                }
                resultText?.let { Text(it, color = if (resetOutcome == "reset") Tok.codex else Tok.tx2, fontSize = 11.5.sp, lineHeight = 17.sp) }
            }
        }
        if ((weekly?.usedPercent ?: 0.0) >= 100.0) {
            Text(stringResource(Res.string.usage_codex_rate_limited), color = Tok.warn, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        limits.capturedAt?.let { at ->
            Text(stringResource(Res.string.usage_codex_snapshot, relativeTime(at)), color = Tok.muted, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.hair))
        Text(stringResource(Res.string.usage_codex_dashboard_hint), color = Tok.muted, fontSize = 11.5.sp, lineHeight = 17.sp)
    }
}

/** One allowance-window row (remaining % + bar + reset caption), shared by the Codex and Claude cards;
 *  [accent] is the healthy bar color (each agent's identity color), warn/danger tints stay shared. */
@Composable
private fun LimitRow(label: String, usedPercent: Double, windowMinutes: Int, resetsAt: Long, accent: Color) {
    val used = usedPercent.coerceIn(0.0, 100.0)
    val remaining = (100.0 - used).coerceIn(0.0, 100.0)
    val pct = remaining.roundToInt()
    val barColor = when {
        remaining <= 0.0 -> Tok.danger
        remaining <= 20.0 -> Tok.warn
        else -> accent
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Tok.tx2, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.usage_codex_remaining, pct) + "%", color = Tok.tx, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Tok.raised)) {
            if (remaining > 0.0) Box(
                Modifier.fillMaxWidth((remaining / 100.0).toFloat()).height(6.dp)
                    .clip(RoundedCornerShape(999.dp)).background(barColor),
            )
        }
        Text(
            if (windowMinutes >= 24 * 60) {
                stringResource(Res.string.usage_codex_resets_on, beijingWeekdayTime(resetsAt))
            } else {
                stringResource(Res.string.usage_codex_resets_in, resetsInCaption(resetsAt))
            },
            color = Tok.muted,
            fontSize = 11.sp,
        )
    }
}

/** Fixed UTC+8 without platform date APIs: 1970-01-01 was Thursday (weekday index 4). */
@Composable
private fun beijingWeekdayTime(epochSeconds: Long): String {
    val local = epochSeconds + 8 * 3600L
    val days = kotlin.math.floor(local.toDouble() / 86_400.0).toLong()
    val secondOfDay = ((local % 86_400L) + 86_400L) % 86_400L
    val weekday = (((days + 4) % 7 + 7) % 7).toInt()
    val weekdayLabel = stringResource(
        when (weekday) {
            0 -> Res.string.weekday_sun
            1 -> Res.string.weekday_mon
            2 -> Res.string.weekday_tue
            3 -> Res.string.weekday_wed
            4 -> Res.string.weekday_thu
            5 -> Res.string.weekday_fri
            else -> Res.string.weekday_sat
        },
    )
    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60
    return "$weekdayLabel ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/** Future reset time from a unix-seconds epoch — "2h 15m", "45m", or "soon". */
private fun resetsInCaption(resetsAtSec: Long): String {
    val remain = (resetsAtSec - dev.ccpocket.app.epochMillis() / 1000).coerceAtLeast(0)
    val hours = remain / 3600
    val mins = (remain % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "soon"
    }
}

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

/**
 * The primary card: the selected range's total tokens, big and mono. The [scope] tag ("today"/"7d"/"30d")
 * bakes the range into the label; [period] is the muted-mono date sub-caption. The bottom line is either a
 * calm "no usage today yet" note ([zeroToday]) or a NEUTRAL day-over-day delta with its baseline inline —
 * never the old good/bad color inversion.
 */
@Composable
private fun HeroCard(tokens: Long, scope: String, period: String, deltaPct: Int?, zeroToday: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.usage_tokens), color = Tok.tx2, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("· $scope", color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
        }
        Text(formatTokens(tokens), color = Tok.tx, fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
        Text(period, color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
        when {
            zeroToday -> Text(stringResource(Res.string.usage_empty), color = Tok.muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 1.dp))
            deltaPct != null -> {
                val down = deltaPct < 0
                Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (down) "▼" else "▲", color = Tok.muted, fontSize = 9.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("${if (down) -deltaPct else deltaPct}% ${stringResource(Res.string.usage_vs_yesterday)}", color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
                }
            }
        }
    }
}

/**
 * A secondary "· today"-scoped metric. [value] null → a labeled "not available yet" placeholder (never a bare
 * dash), so a missing cost / cache-hit reads as calm-not-broken. Optional [arcPct] draws the cache-hit gauge.
 */
@Composable
private fun StatCard(modifier: Modifier, label: String, value: String?, arcPct: Int? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Tok.tx2, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            if (value != null) Text(value, color = Tok.tx, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            else Text(stringResource(Res.string.usage_na), color = Tok.muted, fontSize = 12.5.sp, modifier = Modifier.weight(1f, fill = false).padding(bottom = 3.dp))
            if (arcPct != null) Arc(arcPct)
        }
    }
}

/** "Requests" -> "Requests · today": pins a today-only sub-metric's baseline right in its label. */
private fun todayLabel(metric: String) = "$metric · today"

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** ISO "2026-07-09" -> (2026, 7, 9); null if absent/garbage (older daemon may not fill [UsageDay.date]). */
private fun isoParts(iso: String?): Triple<Int, Int, Int>? =
    iso?.split('-')?.mapNotNull(String::toIntOrNull)?.takeIf { it.size == 3 && it[1] in 1..12 }?.let { Triple(it[0], it[1], it[2]) }

/** ISO date -> "Jul 9" (English months, matching the daemon's English weekday labels); null when unparseable. */
private fun monthDay(iso: String?): String? = isoParts(iso)?.let { (_, m, d) -> "${MONTHS[m - 1]} $d" }

/** The hero's period sub-caption: "Today · Jul 9" for a span-1 reply, else "Jul 3 – 9 · 7 days" (the trailing
 *  month is dropped when both ends share it). Falls back to weekday labels if a daemon left [UsageDay.date] null. */
@Composable
private fun periodCaption(u: Usage): String {
    val ds = u.days
    if (ds.size <= 1) {
        val d = ds.firstOrNull()
        return stringResource(Res.string.usage_period_today, monthDay(d?.date) ?: d?.label ?: "")
    }
    val first = ds.first()
    val last = ds.last()
    val fp = isoParts(first.date)
    val lp = isoParts(last.date)
    val left = monthDay(first.date) ?: first.label
    val right = if (fp != null && lp != null && fp.second == lp.second) lp.third.toString() else (monthDay(last.date) ?: last.label)
    return stringResource(Res.string.usage_period_range, left, right, ds.size)
}

/** Terracotta progress arc for cache-hit. */
@Composable
private fun Arc(pct: Int) {
    Canvas(Modifier.size(40.dp).rotate(-90f)) {
        val stroke = 3.5.dp.toPx()
        val inset = stroke / 2
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(Tok.raised, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
        drawArc(Tok.accent, 0f, 360f * (pct.coerceIn(0, 100) / 100f), false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun Bars(days: List<UsageDay>) {
    val max = days.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1
    val peakIdx = days.indexOfFirst { it.tokens == days.maxOfOrNull { d -> d.tokens } }
    Row(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, d ->
            val frac = (d.tokens.toFloat() / max).coerceIn(0.03f, 1f)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                // label row measured first, bar scales inside the REMAINING band (weight) — a fixed
                // frac*100dp bar overflowed the row at the peak, squeezing the peak's own weekday label
                // to nothing and sinking its bar ~7dp below every other bar's baseline
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (i == peakIdx) Tok.accent else Tok.accent.copy(alpha = 0.42f)))
                }
                if (days.size <= 10) {
                    Spacer(Modifier.height(6.dp))
                    Text(d.label, color = if (i == peakIdx) Tok.tx2 else Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.hair))
}

/** Today's 24 hourly buckets. Same 120dp band as [Bars]; the peak hour is full accent, the rest faded; a 3%
 *  stub keeps empty hours on the axis. Only 0/6/12/18 carry a label so the 24-wide row never crowds. */
@Composable
private fun HourlyBars(hours: List<UsageDay>) {
    val maxTok = hours.maxOfOrNull { it.tokens } ?: 0L
    val max = maxTok.coerceAtLeast(1L)
    val peakIdx = hours.indexOfFirst { it.tokens == maxTok }
    Row(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        hours.forEachIndexed { i, h ->
            val frac = (h.tokens.toFloat() / max).coerceIn(0.03f, 1f)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                // same weighted bar band as [Bars]: the peak's fixed-height bar used to squeeze its label
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (i == peakIdx) Tok.accent else Tok.accent.copy(alpha = 0.42f)))
                }
                Spacer(Modifier.height(6.dp))
                // sparse axis: label 0/6/12/18 only; the rest keep an empty placeholder so bars stay aligned
                Text(if (i % 6 == 0) i.toString() else "", color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.hair))
}

/** 30d calendar heatmap (GitHub-contribution style): 7 columns Mon→Sun, oldest week on top, today on the
 *  bottom row, first day offset by its weekday. Cell intensity = quartile of the window's max; today gets a
 *  hairline ring. Colors are read from [Tok] at draw time (reactive palette — never hoist them to a top val). */
@Composable
private fun Heatmap(days: List<UsageDay>) {
    if (days.isEmpty()) return
    val lead = weekdayMon0(days.first()) // leading blanks so day 0 sits under its weekday column
    val rows = (lead + days.size + 6) / 7
    val max = days.maxOf { it.tokens } // non-empty guarded above; quartile handles max == 0
    val alphas = listOf(0.25f, 0.45f, 0.70f, 1.0f)
    val todayIdx = days.lastIndex
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in COL_LETTERS) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(c, color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    val d = days.getOrNull(idx)
                    val bg = when {
                        d == null -> Tok.raised.copy(alpha = 0.4f) // out-of-window filler
                        d.tokens == 0L -> Tok.raised
                        else -> Tok.accent.copy(alpha = alphas[quartile(d.tokens, max) - 1])
                    }
                    val ring = if (idx == todayIdx) Modifier.border(1.dp, Tok.hair, RoundedCornerShape(3.dp)) else Modifier
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(bg).then(ring))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PeakRow(days.maxByOrNull { it.tokens }, labelOf = ::peakLabel) }
            Text(stringResource(Res.string.usage_less), color = Tok.muted, fontSize = 10.sp)
            Spacer(Modifier.width(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Tok.raised))
                for (a in alphas) Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Tok.accent.copy(alpha = a)))
            }
            Spacer(Modifier.width(5.dp))
            Text(stringResource(Res.string.usage_more), color = Tok.muted, fontSize = 10.sp)
        }
    }
}

/** The "Peak Wed · 12.7M" caption under a trend chart; nothing when the window is all zeros. */
@Composable
private fun PeakRow(peak: UsageDay?, labelOf: (UsageDay) -> String = { it.label }) {
    val p = peak?.takeIf { it.tokens > 0 } ?: return
    Text(stringResource(Res.string.usage_peak, labelOf(p), formatTokens(p.tokens)), color = Tok.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
}

private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val COL_LETTERS = WEEKDAY_LABELS.map { it.take(1) }

/** Monday=0 … Sunday=6 for a trend bucket. Prefer the ISO [UsageDay.date] (Sakamoto's algorithm — commonMain
 *  has no date library); fall back to the daemon's English short label ("Mon".."Sun") when date is null. */
internal fun weekdayMon0(day: UsageDay): Int {
    isoParts(day.date)?.let { (y, m, d) -> return sakamotoMon0(y, m, d) }
    return WEEKDAY_LABELS.indexOfFirst { day.label.startsWith(it, ignoreCase = true) }.coerceAtLeast(0)
}

// Sakamoto's day-of-week (0=Sunday..6=Saturday), remapped so Monday=0..Sunday=6.
private fun sakamotoMon0(y: Int, m: Int, d: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val yy = if (m < 3) y - 1 else y
    val dow = (yy + yy / 4 - yy / 100 + yy / 400 + t[m - 1] + d) % 7
    return (dow + 6) % 7
}

/** Which of four intensity buckets [tokens] falls in (1..4), by fraction of the window [max]. */
internal fun quartile(tokens: Long, max: Long): Int {
    val f = tokens.toFloat() / max.coerceAtLeast(1L)
    return when {
        f <= 0.25f -> 1
        f <= 0.50f -> 2
        f <= 0.75f -> 3
        else -> 4
    }
}

/** The peak day's caption label: ISO [UsageDay.date] "2026-07-05" → "07-05"; falls back to the weekday [UsageDay.label]. */
internal fun peakLabel(day: UsageDay): String = day.date?.substringAfter('-') ?: day.label

@Composable
private fun ModelRow(m: UsageModel, max: Long) {
    val color = when (m.agent) {
        AgentKind.CODEX -> Tok.codex
        AgentKind.CURSOR -> Tok.info
        else -> Tok.accent
    }
    Column(Modifier.padding(vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(m.model, color = Tok.tx, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(formatTokens(m.tokens), color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)).background(Tok.raised)) {
            Box(Modifier.fillMaxWidth((m.tokens.toFloat() / max).coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(999.dp)).background(color))
        }
    }
}

@Composable
private fun SLabel(text: String, right: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text.uppercase(), color = Tok.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        right?.let { Text(it, color = Tok.muted, fontSize = 11.5.sp) }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = Tok.accent, strokeWidth = 2.dp)
    }
}

/** Whole window is empty. Range-aware headline ("No usage yet today" vs "No usage in the last 7 days") so a
 *  quiet 7d/30d reads as calmly explained, not broken — no bare dashes anywhere. */
@Composable
private fun Empty(span: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val title = if (span <= 1) stringResource(Res.string.usage_empty) else stringResource(Res.string.usage_empty_range, span)
            Text(title, color = Tok.tx2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.usage_empty_hint), color = Tok.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
        }
        LimitsPager(codex = null, claude = null)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Offline() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text(stringResource(Res.string.usage_offline), color = Tok.tx2, fontSize = 12.5.sp) }
    }
}

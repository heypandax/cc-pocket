package dev.ccpocket.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.protocol.AgentKind

/**
 * Agent identity — the consistent visual language for "which backend drives this session".
 * Claude keeps the app accent (terracotta); Codex gets a calm teal. Per the design, the common Claude
 * case stays unmarked and ONLY Codex is tagged in lists/headers; the agent is always explicit in session info.
 */
fun agentColor(agent: AgentKind): Color = when (agent) {
    AgentKind.CLAUDE -> Tok.accent
    AgentKind.CODEX -> Tok.codex
    AgentKind.CURSOR -> Tok.info
}
fun agentName(agent: AgentKind): String = when (agent) {
    AgentKind.CLAUDE -> "Claude"
    AgentKind.CODEX -> "Codex"
    AgentKind.CURSOR -> "Cursor"
}
fun agentTagline(agent: AgentKind): String = when (agent) {
    AgentKind.CLAUDE -> "Claude Code · Anthropic"
    AgentKind.CODEX -> "Codex · OpenAI"
    AgentKind.CURSOR -> "Cursor Agent · Ultra"
}

/** The two standard agent-color tints — a 12% fill + a 42% border — shared by the chip and the selection cards. */
internal fun Color.agentTintFill(): Color = copy(alpha = 0.12f)
internal fun Color.agentTintBorder(): Color = copy(alpha = 0.42f)

/** A line glyph per agent, drawn in a 20×20 grid — shaped after each product's real mark so the
 *  identity is recognizable even at badge sizes: Claude = Anthropic's radiant starburst ✻;
 *  Codex = the in-app orbit mark; Cursor = the isometric cube from Cursor's logo. */
@Composable
fun AgentGlyph(agent: AgentKind, color: Color = agentColor(agent), size: Int = 18) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.minDimension / 20f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        if (agent == AgentKind.CODEX) {
            val w = 1.6f * s
            // central node
            drawCircle(color, radius = 2.3f * s, center = p(10f, 10f), style = Stroke(width = w))
            // two orbit arcs (top-right + bottom-left), radius 6.8 around center
            val box = Offset((10f - 6.8f) * s, (10f - 6.8f) * s)
            val d = Size(13.6f * s, 13.6f * s)
            drawArc(color, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = box, size = d, style = Stroke(width = w, cap = StrokeCap.Round))
            drawArc(color, startAngle = 90f, sweepAngle = 90f, useCenter = false, topLeft = box, size = d, style = Stroke(width = w, cap = StrokeCap.Round))
        } else if (agent == AgentKind.CURSOR) {
            // isometric cube: pointy-top hexagon outline + the three inner edges that make the top face pop
            val w = 1.5f * s
            val cx = 10f
            val cy = 10f
            val r = 7f
            fun vertex(deg: Int): Offset {
                val rad = deg * (kotlin.math.PI / 180.0)
                return p(cx + r * kotlin.math.cos(rad).toFloat(), cy - r * kotlin.math.sin(rad).toFloat())
            }
            val hex = listOf(90, 150, 210, 270, 330, 30).map(::vertex)
            for (i in hex.indices) drawLine(color, hex[i], hex[(i + 1) % hex.size], strokeWidth = w, cap = StrokeCap.Round)
            val center = p(cx, cy)
            intArrayOf(150, 270, 30).forEach { drawLine(color, center, vertex(it), strokeWidth = w, cap = StrokeCap.Round) }
        } else {
            // Claude: Anthropic's radiant starburst — 8 rays with a slightly longer horizontal pair
            val w = 1.9f * s
            val center = p(10f, 10f)
            for (i in 0 until 8) {
                val rad = i * (kotlin.math.PI / 4.0)
                val reach = (if (i % 4 == 0) 7.4f else 6.2f) * s
                val inner = 1.1f * s
                val dx = kotlin.math.cos(rad).toFloat()
                val dy = kotlin.math.sin(rad).toFloat()
                drawLine(
                    color,
                    Offset(center.x + dx * inner, center.y + dy * inner),
                    Offset(center.x + dx * reach, center.y + dy * reach),
                    strokeWidth = w, cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** The agent chip used app-wide: a rounded tinted pill with the glyph + name in the agent's color. */
@Composable
fun AgentTag(agent: AgentKind, small: Boolean = true) {
    val c = agentColor(agent)
    Row(
        Modifier
            .background(c.agentTintFill(), RoundedCornerShape(999.dp))
            .border(1.dp, c.agentTintBorder(), RoundedCornerShape(999.dp))
            .padding(horizontal = if (small) 7.dp else 9.dp, vertical = if (small) 2.dp else 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentGlyph(agent, c, if (small) 12 else 14)
        // trim the text's asymmetric ascent/descent so the glyph optically centers against the letters
        Text(agentName(agent), color = c, fontSize = if (small) 10.5.sp else 12.sp, fontWeight = FontWeight.SemiBold, style = TightCenter)
    }
}

/** Header / list-row badge: shows the tag ONLY for the non-default Codex (Claude stays unmarked), with a leading [gap]. */
@Composable
fun AgentBadge(agent: AgentKind?, gap: Dp = 6.dp) {
    if (agent != null && agent != AgentKind.CLAUDE) {
        Spacer(Modifier.width(gap))
        AgentTag(agent)
    }
}

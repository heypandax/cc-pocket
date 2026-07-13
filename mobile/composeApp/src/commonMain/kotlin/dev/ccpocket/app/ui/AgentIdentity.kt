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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.app.resources.Res
import dev.ccpocket.app.resources.openai_blossom
import org.jetbrains.compose.resources.painterResource

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

/** A glyph per agent, drawn in a 20×20 grid — shaped after each product's real mark so the
 *  identity is recognizable even at badge sizes: Claude = Anthropic's radiant starburst ✻;
 *  Codex = OpenAI's official Blossom; Cursor = the isometric cube from Cursor's logo. */
@Composable
fun AgentGlyph(agent: AgentKind, color: Color = agentColor(agent), size: Int = 18) {
    if (agent == AgentKind.CODEX) {
        Icon(
            painter = painterResource(Res.drawable.openai_blossom),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size.dp),
        )
        return
    }
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.minDimension / 20f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        if (agent == AgentKind.CURSOR) {
            // Cursor's official 2D Cube path (466.73 × 532.09), scaled without changing its geometry.
            // Source: Cursor brand assets / General Logos / Cube / CUBE_2D_*.svg.
            val k = this.size.minDimension / 532.09f
            val ox = (this.size.width - 466.73f * k) / 2f
            fun x(v: Float) = ox + v * k
            fun y(v: Float) = v * k
            val logo = Path().apply {
                moveTo(x(457.43f), y(125.94f))
                lineTo(x(244.42f), y(2.96f))
                cubicTo(x(237.58f), y(-0.99f), x(229.14f), y(-0.99f), x(222.30f), y(2.96f))
                lineTo(x(9.30f), y(125.94f))
                cubicTo(x(3.55f), y(129.26f), x(0f), y(135.40f), x(0f), y(142.05f))
                lineTo(x(0f), y(390.04f))
                cubicTo(x(0f), y(396.69f), x(3.55f), y(402.83f), x(9.30f), y(406.15f))
                lineTo(x(222.31f), y(529.13f))
                cubicTo(x(229.15f), y(533.08f), x(237.59f), y(533.08f), x(244.43f), y(529.13f))
                lineTo(x(457.44f), y(406.15f))
                cubicTo(x(463.19f), y(402.83f), x(466.74f), y(396.69f), x(466.74f), y(390.04f))
                lineTo(x(466.74f), y(142.05f))
                cubicTo(x(466.74f), y(135.40f), x(463.19f), y(129.26f), x(457.44f), y(125.94f))
                close()
                moveTo(x(444.05f), y(151.99f))
                lineTo(x(238.42f), y(508.15f))
                cubicTo(x(237.03f), y(510.55f), x(233.36f), y(509.57f), x(233.36f), y(506.79f))
                lineTo(x(233.36f), y(273.58f))
                cubicTo(x(233.36f), y(268.92f), x(230.87f), y(264.61f), x(226.83f), y(262.27f))
                lineTo(x(24.87f), y(145.67f))
                cubicTo(x(22.47f), y(144.28f), x(23.45f), y(140.61f), x(26.23f), y(140.61f))
                lineTo(x(437.49f), y(140.61f))
                cubicTo(x(443.33f), y(140.61f), x(446.98f), y(146.94f), x(444.06f), y(152f))
                close()
            }
            drawPath(logo, color)
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

package dev.ccpocket.app.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The app-wide ambient canvas behind every translucent surface.
 *
 * Compose's portable blur modifier blurs a component's own pixels rather than the pixels behind it, so a
 * literal backdrop-filter would either be platform-specific or visually incorrect. This low-cost pair of
 * static radial lights gives the translucent surfaces real depth on every target; unsupported/low-power
 * devices naturally retain the same readable tinted material without a separate fallback path.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val top = Tok.canvasTop
    val bottom = Tok.canvasBottom
    val warm = Tok.glowWarm
    val cool = Tok.glowCool
    val dark = Tok.current.dark
    Box(
        modifier.drawWithCache {
            val canvas = Brush.linearGradient(
                colors = listOf(top, bottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val radius = max(size.width, size.height) * 0.78f
            val warmGlow = Brush.radialGradient(
                colors = listOf(warm.copy(alpha = if (dark) 0.16f else 0.13f), Color.Transparent),
                center = Offset(size.width * 0.04f, size.height * 0.02f),
                radius = radius,
            )
            val coolGlow = Brush.radialGradient(
                colors = listOf(cool.copy(alpha = if (dark) 0.12f else 0.11f), Color.Transparent),
                center = Offset(size.width * 0.96f, size.height * 0.88f),
                radius = radius * 0.9f,
            )
            onDrawBehind {
                drawRect(brush = canvas)
                // Broad, deliberately quiet glows: enough variation for the glass to read, never enough
                // to compete with code, permissions, or status colors layered above it.
                drawRect(brush = warmGlow)
                drawRect(brush = coolGlow)
            }
        },
        content = content,
    )
}

/**
 * Portable glass material used by cards, sheets, popovers, and modal panels.
 *
 * [elevated] increases opacity and depth for content that floats over another interactive layer. The top
 * highlight and neutral border keep edges visible in both themes; text colors remain fully opaque and keep
 * their existing WCAG-AA contrast against the strongest possible material tint.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(PocketRadius.large),
    elevated: Boolean = false,
    elevation: Dp = if (elevated) 20.dp else 0.dp,
): Modifier {
    val fill = if (elevated) Tok.raised else Tok.surface
    val topAlpha = (fill.alpha + if (elevated) 0.04f else 0.08f).coerceAtMost(1f)
    val material = Brush.verticalGradient(
        listOf(
            fill.copy(alpha = topAlpha),
            fill,
        ),
    )
    val edge = Brush.verticalGradient(
        listOf(
            Tok.glassHighlight,
            Tok.glassBorder,
            Tok.glassBorder.copy(alpha = Tok.glassBorder.alpha * 0.72f),
        ),
    )
    return this
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false) else Modifier)
        .clip(shape)
        .background(material, shape)
        .border(1.dp, edge, shape)
}

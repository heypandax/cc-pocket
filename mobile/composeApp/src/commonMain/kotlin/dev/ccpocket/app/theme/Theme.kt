package dev.ccpocket.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The appearance setting (issue #63). SYSTEM follows the OS light/dark, the other two force it.
 *  Absent/garbage → DARK: the app shipped dark-only, so existing users stay dark until they opt into
 *  light or system (dark must not regress). Users pick SYSTEM/LIGHT explicitly in Settings ▸ Appearance. */
enum class ThemeMode { SYSTEM, LIGHT, DARK;
    companion object {
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DARK
    }
}

/** A full semantic color set — one per theme. Reads flow through [Tok], which points at the active one. */
data class Palette(
    val base: Color,
    val canvasTop: Color,
    val canvasBottom: Color,
    val surface: Color,
    val raised: Color,
    val hair: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val glowWarm: Color,
    val glowCool: Color,
    val tx: Color,
    val tx2: Color,
    val muted: Color,
    val accent: Color,
    val accentPressed: Color,
    val codex: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val info: Color,
    val dark: Boolean,
)

/** Calm Terminal Companion — dark glass tuned for opaque, high-contrast text over the ambient canvas. */
val DarkPalette = Palette(
    base = Color(0xFF0E0F11),
    canvasTop = Color(0xFF151925),
    canvasBottom = Color(0xFF090B10),
    surface = Color(0xB8161A21),
    raised = Color(0xDE242832),
    hair = Color(0x3DFFFFFF),
    glassBorder = Color(0x4AFFFFFF),
    glassHighlight = Color(0x70FFFFFF),
    glassShadow = Color(0x99000000),
    glowWarm = Color(0xFFD97757),
    glowCool = Color(0xFF3FB5AC),
    tx = Color(0xFFECEDEE),
    tx2 = Color(0xFF9BA1A6),
    muted = Color(0xFF858B91),
    accent = Color(0xFFD97757),
    accentPressed = Color(0xFFC4633F), // primary pressed/active (desktop hover-press)
    codex = Color(0xFF3FB5AC),         // Codex agent identity — calm teal that sits on the dark palette
    ok = Color(0xFF4FB477),
    warn = Color(0xFFE0A93B),
    danger = Color(0xFFE5604D),
    info = Color(0xFF5B9BD5),           // neutral info / links / "plan" mode
    dark = true,
)

/** Light variant (issue #63): warm off-white surfaces with the same terracotta identity, each hue
 *  darkened enough to stay legible on light backgrounds (accents double as text/borders in places). */
val LightPalette = Palette(
    base = Color(0xFFF4F1EC),
    canvasTop = Color(0xFFF9EDE4),
    canvasBottom = Color(0xFFDDEBEC),
    // Warm translucent material instead of near-opaque white. Most screens are composed from surface
    // cards; an almost-white token turned the light theme into one continuous white mask and hid the
    // ambient canvas that is meant to give the glass hierarchy depth.
    surface = Color(0xA8F5EDE5),
    raised = Color(0xD9FAF4EE),
    hair = Color(0x40717B8A),
    glassBorder = Color(0x526A7482),
    glassHighlight = Color(0x8FFFFFFF),
    glassShadow = Color(0x290E1828),
    glowWarm = Color(0xFFC15F3C),
    glowCool = Color(0xFF1C8B82),
    tx = Color(0xFF1C1D1F),
    tx2 = Color(0xFF5B6066),
    muted = Color(0xFF6F757C),
    accent = Color(0xFFA94E30),
    accentPressed = Color(0xFF8E3F27),
    codex = Color(0xFF14776F),
    ok = Color(0xFF237A46),
    warn = Color(0xFF8A5E0A),
    danger = Color(0xFFB33223),
    info = Color(0xFF2869A8),
    dark = false,
)

/**
 * Calm Terminal Companion tokens. Backed by a reactive [current] palette so switching light/dark
 * recomposes every reader — the ~1200 `Tok.base` / `Tok.tx` / … call sites stay untouched. Getters
 * (not cached vals) so derived objects like DiffTok re-read the live palette too.
 */
object Tok {
    /** The active palette. [PocketTheme] points this at light/dark; equal-value writes are no-ops. */
    var current by mutableStateOf(DarkPalette)
        internal set

    val base: Color get() = current.base
    val canvasTop: Color get() = current.canvasTop
    val canvasBottom: Color get() = current.canvasBottom
    val surface: Color get() = current.surface
    val raised: Color get() = current.raised
    val hair: Color get() = current.hair
    val glassBorder: Color get() = current.glassBorder
    val glassHighlight: Color get() = current.glassHighlight
    val glassShadow: Color get() = current.glassShadow
    val glowWarm: Color get() = current.glowWarm
    val glowCool: Color get() = current.glowCool
    val tx: Color get() = current.tx
    val tx2: Color get() = current.tx2
    val muted: Color get() = current.muted
    val accent: Color get() = current.accent
    val accentPressed: Color get() = current.accentPressed
    val codex: Color get() = current.codex
    val ok: Color get() = current.ok
    val warn: Color get() = current.warn
    val danger: Color get() = current.danger
    val info: Color get() = current.info
}

/** Chat text scale (issue #8), provided once at the app root from PocketRepository.fontScale; 1.0 = design default. */
val LocalFontScale = staticCompositionLocalOf { 1f }

/** Resolve a [ThemeMode] to an effective dark/light against the current OS setting. Pure seam (extracted
 *  from [PocketTheme]) so the polarity that also drives the system-bar icon color (issue #117) is unit-tested
 *  without a composition: LIGHT is never dark, DARK is always dark, SYSTEM follows [systemDark]. */
fun ThemeMode.resolvesToDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}

/** Appearance-aware entry point (issue #63): resolves [mode] against the OS so both app roots just pass their
 *  persisted [ThemeMode] — a live system light/dark flip re-themes, LIGHT/DARK force it. Delegates to the
 *  boolean overload, still the default for the ~dozen test/preview `PocketTheme { }` callers. */
@Composable
fun PocketTheme(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = mode.resolvesToDark(systemDark = isSystemInDarkTheme())
    // issue #117: align the OS status/navigation-bar FOREGROUND (icon/text) color with the resolved theme so
    // the bars stay legible in light mode too — Android tints them, iOS/desktop no-op. On the mode overload
    // only, so the real app roots drive it while the boolean overload used by tests/previews stays inert.
    SystemBarAppearance(darkTheme = dark)
    PocketTheme(dark = dark, fontScale = fontScale, content = content)
}

@Composable
fun PocketTheme(dark: Boolean = true, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    // Point the global tokens at the active palette BEFORE children compose, so their first frame reads
    // the right colors (no dark flash for a light-mode launch). The write is idempotent — DarkPalette /
    // LightPalette are stable singletons, so an unchanged theme is a structural no-op and never loops.
    Tok.current = palette
    // one override list over whichever baseline — the unset M3 fields keep each factory's light/dark
    // defaults (identical to spelling both schemes out), so a token add/rename stays in a single place
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.accent, onPrimary = if (dark) palette.base else Color.White,
        background = palette.base, onBackground = palette.tx,
        surface = palette.surface, onSurface = palette.tx,
        surfaceVariant = palette.raised, onSurfaceVariant = palette.tx2,
        outline = palette.hair, error = palette.danger,
    )
    MaterialTheme(
        colorScheme = scheme,
        content = { CompositionLocalProvider(LocalFontScale provides fontScale, content = content) },
    )
}

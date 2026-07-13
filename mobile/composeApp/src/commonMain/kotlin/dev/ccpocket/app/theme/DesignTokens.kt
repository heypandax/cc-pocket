package dev.ccpocket.app.theme

import androidx.compose.ui.unit.dp

/** Shared 4pt foundations for mobile and desktop surfaces. */
object PocketSpace {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
}

object PocketRadius {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val sheet = 20.dp
    val pill = 999.dp
}

object PocketSize {
    /** WCAG/platform minimum pointer target. */
    val touch = 44.dp
    val primaryAction = 48.dp
}

object PocketMotion {
    const val fastMs = 150
    const val normalMs = 300
    const val slowMs = 500
    const val streamSampleMs = 50L
    const val imeSettleMs = 80L
}

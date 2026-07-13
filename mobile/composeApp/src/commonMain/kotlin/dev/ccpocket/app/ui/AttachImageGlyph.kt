package dev.ccpocket.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Native attachment glyph where the platform provides one (SF Symbols on iOS). */
@Composable
expect fun AttachImageGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
)

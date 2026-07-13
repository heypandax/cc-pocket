package dev.ccpocket.app.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun AttachImageGlyph(tint: Color, modifier: Modifier, contentDescription: String?) {
    Icon(AttachImageIcon, contentDescription, modifier, tint)
}

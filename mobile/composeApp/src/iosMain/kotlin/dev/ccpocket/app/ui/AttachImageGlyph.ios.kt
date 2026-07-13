package dev.ccpocket.app.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Same custom-drawn [AttachImageIcon] as Android/desktop (Skia renders it natively on iOS too —
 *  no UIKit interop needed). The generic system "photo" SF Symbol this replaced didn't match the
 *  app's own rounded-frame/sun/mountain attach glyph, so iOS looked like the odd one out. */
@Composable
actual fun AttachImageGlyph(tint: Color, modifier: Modifier, contentDescription: String?) {
    Icon(AttachImageIcon, contentDescription, modifier, tint)
}

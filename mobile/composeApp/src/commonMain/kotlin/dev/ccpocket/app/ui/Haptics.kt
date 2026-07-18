package dev.ccpocket.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Adds one deliberate tactile tick to a completed tap. Kept at the action boundary instead of on pointer
 * down so disabled controls, cancelled gestures, scrolling and recomposition never vibrate the phone.
 */
@Composable
internal fun rememberHapticClick(action: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics, action) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
}

package dev.ccpocket.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageView

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun AttachImageGlyph(tint: Color, modifier: Modifier, contentDescription: String?) {
    UIKitView(
        factory = {
            UIImageView().apply {
                // SF Symbols arrive as template images, so UIImageView.tintColor controls state.
                image = UIImage.systemImageNamed("photo.badge.plus")
            }
        },
        modifier = modifier.then(
            if (contentDescription == null) Modifier else Modifier.semantics {
                this.contentDescription = contentDescription
            },
        ),
        update = { view ->
            view.tintColor = tint.toUIColor()
        },
    )
}

private fun Color.toUIColor() = UIColor(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble(),
)

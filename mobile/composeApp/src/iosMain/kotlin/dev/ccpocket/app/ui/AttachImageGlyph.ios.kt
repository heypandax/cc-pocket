package dev.ccpocket.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingModeAlwaysTemplate
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentModeScaleAspectFit

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun AttachImageGlyph(tint: Color, modifier: Modifier, contentDescription: String?) {
    UIKitView(
        factory = {
            UIImageView().apply {
                image = UIImage.systemImageNamed("photo.badge.plus")
                    ?.imageWithRenderingMode(UIImageRenderingModeAlwaysTemplate)
                contentMode = UIViewContentModeScaleAspectFit
                isAccessibilityElement = contentDescription != null
                accessibilityLabel = contentDescription
            }
        },
        modifier = modifier,
        update = { view ->
            view.tintColor = tint.toUIColor()
            view.accessibilityLabel = contentDescription
        },
    )
}

private fun Color.toUIColor() = UIColor(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble(),
)

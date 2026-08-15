package dev.ccpocket.app

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCompatibilityMapping

actual fun normalizeNfkc(value: String): String =
    (value as NSString).precomposedStringWithCompatibilityMapping

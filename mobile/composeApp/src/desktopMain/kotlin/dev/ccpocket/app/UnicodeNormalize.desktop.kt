package dev.ccpocket.app

import java.text.Normalizer

actual fun normalizeNfkc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)

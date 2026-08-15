package dev.ccpocket.app

/** Platform Unicode NFKC used to mirror daemon member-name validation before sending. */
expect fun normalizeNfkc(value: String): String

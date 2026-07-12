package dev.ccpocket.daemon.agent

/** A phone approval must remain open long enough for an away-from-keyboard workflow. */
object ApprovalTimeout {
    val ms: Long = System.getenv("CC_POCKET_ASK_TIMEOUT_SEC")
        ?.trim()?.toLongOrNull()?.coerceIn(30, 86_400)?.times(1_000)
        ?: 600_000L
}

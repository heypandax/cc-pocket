package dev.ccpocket.app

import dev.ccpocket.protocol.Usage

/** Publishes the latest daemon usage reply for the native iOS home-screen widget. */
expect fun publishUsageWidgetSnapshot(usage: Usage)

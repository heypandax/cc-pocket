package dev.ccpocket.app

// 10.0.2.2 is the Android emulator's alias for the host machine's loopback.
actual fun defaultDaemonUrl(): String = "ws://10.0.2.2:8765/v1/ws"

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun localWeekdayTime(epochSeconds: Long): String =
    java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochSeconds * 1000))

actual fun isPreviewMode(): Boolean = false

package dev.ccpocket.app

actual fun defaultDaemonUrl(): String = "ws://127.0.0.1:8765/v1/ws"

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun localWeekdayTime(epochSeconds: Long): String =
    java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }.format(java.util.Date(epochSeconds * 1000))

actual fun isPreviewMode(): Boolean = System.getProperty("ccpPreview") == "true"

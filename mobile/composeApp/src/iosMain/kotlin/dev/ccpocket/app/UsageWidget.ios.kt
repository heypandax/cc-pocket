package dev.ccpocket.app

import dev.ccpocket.protocol.Usage
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

private const val WIDGET_GROUP = "group.com.panda.ccpocket"
internal var usageWidgetReloader: (() -> Unit)? = null

actual fun publishUsageWidgetSnapshot(usage: Usage) {
    val defaults = NSUserDefaults(suiteName = WIDGET_GROUP) ?: return
    defaults.setObject(usage.tokensToday.toString(), forKey = "tokensToday")
    defaults.setObject(usage.requestsToday.toString(), forKey = "requestsToday")
    val limit = usage.codexLimits?.secondary ?: usage.codexLimits?.primary
    limit?.let {
        defaults.setDouble((100.0 - it.usedPercent).coerceIn(0.0, 100.0), forKey = "weeklyRemaining")
        defaults.setInteger(it.windowMinutes.toLong(), forKey = "limitWindowMinutes")
    } ?: defaults.removeObjectForKey("weeklyRemaining")
    if (limit == null) defaults.removeObjectForKey("limitWindowMinutes")
    defaults.setObject(usage.codexLimits?.planType ?: "", forKey = "planType")
    defaults.setDouble(NSDate().timeIntervalSince1970, forKey = "updatedAt")
    defaults.synchronize()
    usageWidgetReloader?.invoke()
}

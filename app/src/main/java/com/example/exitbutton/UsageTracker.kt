package com.example.exitbutton

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * מזהה אילו אפליקציות בנק נפתחו מאז זמן נתון - כולל אפליקציות שהמשתמש
 * כבר יצא מהן. משתמש ב-UsageStatsManager (זול, בלי root בכל בדיקה).
 * את הרשאת Usage Access האפליקציה נותנת לעצמה פעם אחת דרך root.
 */
class UsageTracker(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun ensureUsageAccess() {
        if (!hasUsageAccess()) {
            RootUtils.runAsRoot("appops set ${context.packageName} GET_USAGE_STATS allow")
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * מחזיר מפה: אפליקציית בנק -> זמן הפתיחה הראשון שלה בטווח [from, to].
     */
    fun bankAppsOpenedBetween(from: Long, to: Long): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        if (!hasUsageAccess()) {
            // בלי Usage Access עדיין (ההרשאה ניתנת דרך root בהפעלה) - מדלגים על הבדיקה הזו.
            // לחיצה על "יציאה" עדיין תנקה את האפליקציה שבחזית (גיבוי ב-OverlayService).
            return result
        }
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (isForeground && event.packageName in BankConfig.TRACKED_PACKAGES) {
                result.putIfAbsent(event.packageName, event.timeStamp)
            }
        }
        return result
    }
}

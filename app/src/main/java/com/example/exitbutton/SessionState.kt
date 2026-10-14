package com.example.exitbutton

import android.content.Context

/**
 * מצב ה-session הנוכחי, שמור ב-SharedPreferences כדי לשרוד הפעלה מחדש של השירות.
 *  - dirtyPackages: אפליקציות בנק שנפתחו מאז הניקוי האחרון (אותן ננקה).
 *  - deadline: מתי יופעל ניקוי אוטומטי (0 = אין session פעיל).
 *  - snoozesUsed: כמה דחיות כבר נוצלו ב-session הנוכחי.
 *  - lastQueryTime: עד איזה זמן כבר סרקנו אירועי שימוש.
 */
class SessionState(context: Context) {

    private val prefs = context.getSharedPreferences("exit_session", Context.MODE_PRIVATE)

    var dirtyPackages: Set<String>
        get() = prefs.getStringSet("dirty", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("dirty", value).apply()

    var deadline: Long
        get() = prefs.getLong("deadline", 0L)
        set(value) = prefs.edit().putLong("deadline", value).apply()

    var snoozesUsed: Int
        get() = prefs.getInt("snoozes", 0)
        set(value) = prefs.edit().putInt("snoozes", value).apply()

    var lastQueryTime: Long
        get() = prefs.getLong("lastQuery", 0L)
        set(value) = prefs.edit().putLong("lastQuery", value).apply()

    val isActive: Boolean get() = deadline > 0L

    fun reset(now: Long) {
        prefs.edit()
            .putStringSet("dirty", emptySet())
            .putLong("deadline", 0L)
            .putInt("snoozes", 0)
            .putLong("lastQuery", now + 2000) // מונע רישום מחדש של אירועים מלפני הניקוי
            .apply()
    }
}

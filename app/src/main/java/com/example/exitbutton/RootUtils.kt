package com.example.exitbutton

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * עטיפה דקה להרצת פקודות עם הרשאות root דרך su (Magisk).
 * כל קריאה פותחת תהליך su חדש - פשוט ואמין יותר מלהחזיק session פתוח,
 * והעלות (כמה עשרות מילישניות) זניחה לתרחיש שלנו (לחיצת כפתור בודדת).
 */
object RootUtils {

    private const val TAG = "ExitButtonRoot"

    /**
     * מריץ פקודת shell יחידה כ-root ומחזיר את הפלט (stdout).
     * מחזיר null אם ההרצה נכשלה (למשל אין הרשאת root בכלל).
     */
    fun runAsRoot(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            if (exitCode != 0 && errorOutput.isNotBlank()) {
                Log.w(TAG, "Command exited with $exitCode: $command -> $errorOutput")
            }
            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run root command: $command", e)
            null
        }
    }

    /** בדיקה חד-פעמית שההרשאה אכן קיימת (לשימוש אופציונלי במסך ההגדרות). */
    fun hasRootAccess(): Boolean {
        val result = runAsRoot("id")
        return result?.contains("uid=0") == true
    }

    /**
     * מזהה את שם החבילה של האפליקציה שנמצאת כרגע בחזית.
     * מנסה שתי שיטות (dumpsys window ואז dumpsys activity) כי הפורמט
     * המדויק משתנה מעט בין גרסאות/יצרנים של אנדרואיד.
     */
    fun getForegroundPackage(): String? {
        // שיטה 1: mCurrentFocus מתוך dumpsys window
        runAsRoot("dumpsys window windows")?.let { output ->
            val match = Regex("""mCurrentFocus=Window\{[^ ]+ [^ ]+ ([\w.]+)/""").find(output)
                ?: Regex("""mCurrentFocus=Window\{[^ ]+ u\d+ ([\w.]+)/""").find(output)
            if (match != null) return match.groupValues[1]
        }

        // שיטה 2: mResumedActivity / topResumedActivity מתוך dumpsys activity
        runAsRoot("dumpsys activity activities")?.let { output ->
            val match = Regex(
                """(?:mResumedActivity|topResumedActivity)\S*:\s*ActivityRecord\{\S+\s+\S+\s+([\w.]+)/"""
            ).find(output)
            if (match != null) return match.groupValues[1]
        }

        return null
    }

    /** מנקה לגמרי את נתוני האפליקציה הנתונה (שקול להתקנה מחדש). */
    fun clearAppData(packageName: String): Boolean {
        val result = runAsRoot("pm clear $packageName")
        return result?.contains("Success") == true
    }

    /** קובץ סימון: חותמת הזמן שלו = מתי בוצע הניקוי האחרון. */
    private const val MARKER_FILE = "/data/local/tmp/exitbutton_last_clean"

    /**
     * מנקה רק את האפליקציות מהרשימה שנכתבו בהן נתונים מאז הניקוי הקודם.
     *
     * לכל אפליקציה בודקים אם יש בתיקיות הנתונים הפרטיות שלה
     * (/data/data/<pkg> ו-/data/user_de/0/<pkg>) קובץ כלשהו שחדש יותר מקובץ
     * הסימון. אם כן - מישהו השתמש בה, ומריצים עליה pm clear. בסוף מעדכנים
     * את חותמת הזמן של קובץ הסימון.
     *
     * אם קובץ הסימון עדיין לא קיים (הרצה ראשונה) - מנקים את כולן.
     * הכל רץ כסקריפט root יחיד, כך שזה מהיר גם עם 12 אפליקציות.
     *
     * @return רשימת החבילות שנוקו בפועל.
     */
    fun clearChangedApps(packages: List<String>): List<String> {
        if (packages.isEmpty()) return emptyList()

        val script = buildString {
            append("M=$MARKER_FILE; ")
            for (pkg in packages) {
                append("if [ ! -f \$M ] || [ -n \"\$(find /data/data/$pkg /data/user_de/0/$pkg -newer \$M 2>/dev/null | head -n 1)\" ]; then ")
                append("pm clear $pkg >/dev/null 2>&1 && echo CLEARED:$pkg; ")
                append("fi; ")
            }
            append("touch \$M")
        }

        val output = runAsRoot(script) ?: return emptyList()
        return output.lines()
            .filter { it.startsWith("CLEARED:") }
            .map { it.removePrefix("CLEARED:").trim() }
            .also { if (it.isNotEmpty()) Log.i(TAG, "Cleared: $it") }
    }
}

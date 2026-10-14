package com.example.exitbutton

/**
 * כל ההגדרות הניתנות לעריכה במקום אחד.
 */
object BankConfig {

    /** שם החבילה של Fully Kiosk Browser - אליו חוזרים אחרי כל ניקוי. לעולם לא מנוקה. */
    const val KIOSK_PACKAGE = "de.ozerov.fully"

    /** כמה דקות מרגע פתיחת אפליקציה ראשונה עד ניקוי אוטומטי. */
    const val SESSION_MINUTES = 5

    /** בכמה דקות כל לחיצה על "+3" דוחה את הניקוי. */
    const val SNOOZE_MINUTES = 3

    /** מספר דחיות מקסימלי לכל session. */
    const val MAX_SNOOZES = 2

    /**
     * אפליקציות שנמחקים נתוניהן (pm clear) - רק אם נפתחו מאז הניקוי האחרון.
     * הפועלים ומזרחי-טפחות הוחרגו בכוונה (מתנתקים לבד, וניקוי רק יגרום ל-OTP מיותר).
     */
    val CLEAR_DATA_PACKAGES: Set<String> = setOf(
        "com.transferwise.android",      // WISE
        "rates.bankhadoar.co.il",        // בנק הדואר
        "com.leumi.leumiwallet",         // בנק לאומי
        "com.ideomobile.discount",       // בנק דיסקונט
        "com.ideomobile.mercantile",     // בנק מרכנתיל
        "com.fibi.nativeapp",            // הבנק הבינלאומי
        "com.masad.nativeapp",           // בנק מסד
        "com.pagi.nativeapp",            // בנק פאג"י
        "com.bankjerusalem",             // בנק ירושלים
        "com.ideomobile.leumicard",      // מקס
        "com.isracard.hatavot",          // ישראכארט
        "com.onoapps.cal4u"              // כאל
    ) - KIOSK_PACKAGE // הגנה כפולה: הקיוסק לעולם לא ייכלל

    /**
     * אפליקציות שלא מוחקים את הנתונים שלהן (כדי לא לגרום ל-OTP), אלא רק סוגרים
     * בכוח (force-stop). זה מנתק את המשתמש המחובר - הבא בתור יצטרך להזין סיסמה.
     */
    val FORCE_STOP_PACKAGES: Set<String> = setOf(
        "com.ideomobile.hapoalim",       // בנק הפועלים
        "com.MizrahiTefahot.nh"          // בנק מזרחי-טפחות
    ) - KIOSK_PACKAGE

    /** כל האפליקציות שנעקבות (פתיחה שלהן מתחילה טיימר ונרשמת לניקוי/סגירה). */
    val TRACKED_PACKAGES: Set<String> get() = CLEAR_DATA_PACKAGES + FORCE_STOP_PACKAGES
}

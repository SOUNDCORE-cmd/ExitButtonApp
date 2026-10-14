package com.example.exitbutton

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * מסך הגדרה חד-פעמי: מבקש הרשאת "הצגה מעל אפליקציות אחרות" (חובה לכפתור
 * הצף), ואז מפעיל את OverlayService ברקע. אחרי ההגדרה הראשונית אין צורך
 * לפתוח את המסך הזה שוב - השירות ממשיך לרוץ (וחוזר לבד אחרי ריסטארט).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val statusText = TextView(this).apply {
            textSize = 16f
            text = "בודק הרשאות..."
        }
        layout.addView(statusText)
        setContentView(layout)

        checkPermissionsAndStart(statusText)
    }

    override fun onResume() {
        super.onResume()
        // כשחוזרים ממסך ההגדרות (אחרי אישור ההרשאה) - בודקים שוב
        if (hasOverlayPermission()) {
            startOverlayServiceAndFinish()
        }
    }

    private fun checkPermissionsAndStart(statusText: TextView) {
        if (hasOverlayPermission()) {
            startOverlayServiceAndFinish()
            return
        }

        statusText.text =
            "נדרשת הרשאה חד-פעמית: \"הצגה מעל אפליקציות אחרות\".\n" +
            "ייפתח מסך הגדרות - יש להפעיל את המתג עבור האפליקציה הזו, " +
            "ואז לחזור לכאן (או פשוט לסגור את המסך)."

        Toast.makeText(this, "אשר את ההרשאה במסך שנפתח", Toast.LENGTH_LONG).show()

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun startOverlayServiceAndFinish() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "כפתור היציאה פעיל", Toast.LENGTH_SHORT).show()
        finish()
    }
}

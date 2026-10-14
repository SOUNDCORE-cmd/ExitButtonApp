package com.example.exitbutton

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * כפתור צף עם:
 *  - "יציאה": מנקה את כל אפליקציות הבנק שנפתחו מאז הניקוי האחרון (לא רק זו שבחזית)
 *    וחוזר לקיוסק.
 *  - ספירה לאחור: 5 דקות מרגע פתיחת אפליקציית הבנק הראשונה. בסיום - ניקוי אוטומטי.
 *  - "+3 דק'": דחיית הניקוי, עד פעמיים ב-session.
 * חזרה לקיוסק לבד (בלי לחיצה) לא מנקה כלום - רק הלחיצה או הטיימר.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var state: SessionState
    private lateinit var tracker: UsageTracker
    private val handler = Handler(Looper.getMainLooper())

    private var container: LinearLayout? = null
    private lateinit var exitButton: TextView
    private lateinit var snoozeButton: TextView
    private lateinit var params: WindowManager.LayoutParams

    @Volatile private var clearing = false

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        state = SessionState(this)
        tracker = UsageTracker(this)
        if (state.lastQueryTime == 0L) state.lastQueryTime = System.currentTimeMillis()

        Thread { tracker.ensureUsageAccess() }.start()

        addOverlay()
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        container?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    // ---------------- לוגיקת ה-session ----------------

    private fun tick() {
        if (clearing) return
        val now = System.currentTimeMillis()

        // 1. אילו אפליקציות בנק נפתחו מאז הבדיקה הקודמת
        val opened = tracker.bankAppsOpenedBetween(state.lastQueryTime - 2000, now)
        state.lastQueryTime = now

        if (opened.isNotEmpty()) {
            state.dirtyPackages = state.dirtyPackages + opened.keys
            if (!state.isActive) {
                // session חדש מתחיל מזמן הפתיחה הראשון
                val firstOpen = opened.values.minOrNull() ?: now
                state.deadline = firstOpen + BankConfig.SESSION_MINUTES * 60_000L
                state.snoozesUsed = 0
            }
        }

        // 2. האם הגיע זמן הניקוי האוטומטי
        if (state.isActive && now >= state.deadline) {
            performExit()
            return
        }

        updateUi(now)
    }

    private fun snooze() {
        if (!state.isActive || state.snoozesUsed >= BankConfig.MAX_SNOOZES) return
        state.deadline = state.deadline + BankConfig.SNOOZE_MINUTES * 60_000L
        state.snoozesUsed = state.snoozesUsed + 1
        updateUi(System.currentTimeMillis())
    }

    private fun performExit() {
        if (clearing) return
        clearing = true
        exitButton.text = "מנקה..."
        val toClear = state.dirtyPackages.toMutableSet()

        Thread {
            // גיבוי: אם אפליקציית בנק פתוחה עכשיו ועוד לא נרשמה - לנקות גם אותה
            RootUtils.getForegroundPackage()?.let { pkg ->
                if (pkg in BankConfig.CLEAR_DATA_PACKAGES) toClear.add(pkg)
            }
            for (pkg in toClear) {
                if (pkg != BankConfig.KIOSK_PACKAGE) RootUtils.clearAppData(pkg)
            }
            handler.post {
                state.reset(System.currentTimeMillis())
                clearing = false
                returnToKiosk()
                updateUi(System.currentTimeMillis())
            }
        }.start()
    }

    private fun returnToKiosk() {
        packageManager.getLaunchIntentForPackage(BankConfig.KIOSK_PACKAGE)?.let {
            it.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            startActivity(it)
        }
    }

    // ---------------- ממשק ----------------

    private fun updateUi(now: Long) {
        if (clearing) return
        if (!state.isActive) {
            exitButton.text = "יציאה"
            exitButton.setBackgroundResource(R.drawable.circle_button)
            snoozeButton.visibility = View.GONE
        } else {
            val remainingSec = ((state.deadline - now) / 1000).coerceAtLeast(0)
            val mm = remainingSec / 60
            val ss = remainingSec % 60
            exitButton.text = "יציאה\n%d:%02d".format(mm, ss)
            exitButton.setBackgroundResource(
                if (remainingSec <= 60) R.drawable.circle_button_warning else R.drawable.circle_button
            )
            val snoozesLeft = BankConfig.MAX_SNOOZES - state.snoozesUsed
            if (snoozesLeft > 0) {
                snoozeButton.visibility = View.VISIBLE
                snoozeButton.text = "+${BankConfig.SNOOZE_MINUTES} דק' ($snoozesLeft)"
            } else {
                snoozeButton.visibility = View.GONE
            }
        }
        container?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun addOverlay() {
        val density = resources.displayMetrics.density
        val sizePx = (72 * density).toInt()

        exitButton = TextView(this).apply {
            text = "יציאה"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.circle_button)
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }

        snoozeButton = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.snooze_button)
            val pad = (6 * density).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).toInt() }
            setOnClickListener { snooze() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(exitButton)
            addView(snoozeButton)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        // גרירה דרך הכפתור הראשי, והבחנה בין גרירה ללחיצה
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        val threshold = 8 * density

        exitButton.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (abs(dx) > threshold || abs(dy) > threshold) dragging = true
                    if (dragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) v.performClick()
                    true
                }
                else -> false
            }
        }
        exitButton.setOnClickListener { performExit() }

        windowManager.addView(root, params)
        container = root
    }

    private fun buildNotification(): Notification {
        val channelId = "exit_button_channel"
        val channel = NotificationChannel(
            channelId, "כפתור יציאה מהבנק", NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("כפתור היציאה פעיל")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 77
    }
}

package com.reellock.reellock.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

/**
 * Shows a block overlay using TYPE_ACCESSIBILITY_OVERLAY, which an
 * AccessibilityService may add without the SYSTEM_ALERT_WINDOW permission
 * (need.md section 5: "show an accessibility overlay ... when the budget is
 * exhausted"). Built programmatically rather than from a layout resource to
 * keep this self-contained.
 *
 * EnforcementManager.onClassification runs on a background coroutine
 * dispatcher (DB/crypto work shouldn't block the UI thread), but
 * WindowManager.addView/removeView and any View mutation MUST happen on a
 * thread with a Looper - in practice, the main thread. This class owns that
 * requirement itself (via a main-thread Handler) so callers never need to
 * think about which thread they're calling show()/dismiss() from. Missing
 * this originally crashed every overlay attempt with "Can't create handler
 * inside thread ... that has not called Looper.prepare()", silently caught
 * and logged rather than shown - real bug, not an OEM permission issue.
 */
class BlockOverlayManager(private val service: AccessibilityService) {

    private var overlayView: LinearLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager by lazy {
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun isShowing(): Boolean = overlayView != null

    fun show(remainingDays: Long, deadlineText: String) {
        mainHandler.post { showOnMainThread(remainingDays, deadlineText) }
    }

    fun dismiss() {
        mainHandler.post { dismissOnMainThread() }
    }

    private fun showOnMainThread(remainingDays: Long, deadlineText: String) {
        if (overlayView != null) {
            updateText(remainingDays, deadlineText)
            return
        }

        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(235, 10, 10, 10))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(service).apply {
            text = "Reels is locked for now"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val body = TextView(service).apply {
            tag = TAG_BODY
            setTextColor(Color.LTGRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val backButton = Button(service).apply {
            text = "Go to home screen"
            setOnClickListener {
                // GLOBAL_ACTION_BACK was unreliable: Reels' full-screen pager
                // often has no back stack to pop. HOME always leaves Instagram.
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                // Don't wait on the next classification tick to notice the
                // screen changed - dismiss immediately so the button always
                // visibly does something. If the user reopens Instagram
                // straight back into blocked Reels, EnforcementManager shows
                // (and re-homes) it again on the very next event.
                dismissOnMainThread()
            }
        }

        root.addView(title)
        root.addView(body)
        root.addView(backButton)
        overlayView = root
        updateText(remainingDays, deadlineText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0, // no FLAG_NOT_FOCUSABLE / FLAG_NOT_TOUCHABLE: overlay is modal, it captures touches itself
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager.addView(root, params)
            Log.d(TAG, "Overlay window added")
        } catch (e: Exception) {
            // If this still fails after fixing the threading bug, it's a
            // genuine OEM restriction (MIUI and others require a manual
            // "display pop-up windows while running in background"
            // permission for some overlay window types).
            Log.e(TAG, "Failed to add overlay window - check the OEM's background pop-up / overlay permission for this app", e)
            overlayView = null
        }
    }

    private fun dismissOnMainThread() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // Already removed (e.g. service lost its window token) - nothing further to do.
        }
        overlayView = null
    }

    private fun updateText(remainingDays: Long, deadlineText: String) {
        val body = overlayView?.findViewWithTag<TextView>(TAG_BODY) ?: return
        val days = max(remainingDays, 0)
        body.text = "Your Reels allowance for this 5-hour window is used up.\n" +
            "Settings stay locked for $days more day(s), until $deadlineText.\n" +
            "DMs, feed, profile and search are still available.\n" +
            "Need an exception? Ask your trusted person for recovery."
    }

    companion object {
        private const val TAG_BODY = "reellock_overlay_body"
        private const val TAG = "ReelLock"
    }
}

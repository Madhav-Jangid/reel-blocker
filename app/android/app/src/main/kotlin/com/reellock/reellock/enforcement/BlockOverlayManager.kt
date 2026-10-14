package com.reellock.reellock.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.reellock.reellock.R
import kotlin.math.max

/**
 * Shows a block overlay using TYPE_ACCESSIBILITY_OVERLAY, which an
 * AccessibilityService may add without the SYSTEM_ALERT_WINDOW permission
 * (need.md section 5: "show an accessibility overlay ... when the budget is
 * exhausted"). A bottom sheet - anchored to the bottom edge, roughly half
 * the screen tall - rather than a full-screen blackout. Built
 * programmatically (styled via drawable resources) rather than from a
 * layout resource, to keep this self-contained.
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

        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_block_overlay_sheet)
            setPadding(dp(28), dp(16), dp(28), dp(28))
        }

        val handle = android.view.View(service).apply {
            setBackgroundResource(R.drawable.bg_block_overlay_handle)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(20)
            }
        }

        val title = TextView(service).apply {
            text = "Reels is locked for now"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        val body = TextView(service).apply {
            tag = TAG_BODY
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 15f
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(24)
            }
        }
        val dismissButton = Button(service).apply {
            text = "Got it"
            setAllCaps(false)
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = resources.getDrawable(R.drawable.bg_block_overlay_button, service.theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            )
            setOnClickListener {
                // Just closes the sheet - it doesn't navigate anywhere.
                // EnforcementManager already moved the user off Reels (see
                // InstagramNavigation) when this first showed; if they're
                // still on a blocked Reels screen, the very next
                // classification event re-shows it.
                dismissOnMainThread()
            }
        }

        root.addView(handle)
        root.addView(title)
        root.addView(body)
        root.addView(dismissButton)
        overlayView = root
        updateText(remainingDays, deadlineText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            service.resources.displayMetrics.heightPixels / 2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0, // no FLAG_NOT_FOCUSABLE / FLAG_NOT_TOUCHABLE: the sheet is modal within its own bounds
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.BOTTOM

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

package com.reellock.reellock.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.reellock.reellock.classifier.TargetApp

/**
 * Leaves the blocked short-video screen without leaving the app it's in.
 * need.md section 5: "DMs, feed, profile and search remain accessible" -
 * kicking the user all the way to the Android launcher was harsher than
 * necessary and didn't match that framing.
 *
 * Generalized from the original Instagram-only version: instead of
 * remembering exactly which tab (feed/DM/profile/search) the user was on
 * before opening Reels, this now always targets the app's own primary/home
 * tab. That is a deliberate simplification made when YouTube Shorts support
 * was added - ScreenType collapsed from six Instagram-specific screen kinds
 * to just SHORT_VIDEO/OTHER so the same classifier works for any monitored
 * app, which means the specific-previous-tab memory no longer has anything
 * to key off of. GLOBAL_ACTION_BACK (step 2 below) still recovers the exact
 * previous screen in practice for most navigations.
 */
object AppNavigation {

    private fun primaryTabResourceId(app: TargetApp): String? = when (app) {
        TargetApp.INSTAGRAM -> "feed_tab"
        // UNVERIFIED / BEST-EFFORT, same caveat as AppSignatureRegistry's
        // YouTube entry - not confirmed against a real YouTube build yet.
        TargetApp.YOUTUBE -> "pivot_home"
    }

    /**
     * Returns to [app]'s primary tab. Tries, in order:
     *  1. Tap that tab's own bottom-nav button via the standard Accessibility
     *     API (findAccessibilityNodeInfosByViewId + ACTION_CLICK) - the same
     *     way a real tap would.
     *  2. GLOBAL_ACTION_BACK. Needed because real-device logs showed
     *     Instagram hides its bottom-nav chrome entirely during full
     *     immersive Reels playback - the exact moment this runs - so step 1
     *     routinely finds no node to click at all. Back doesn't depend on
     *     finding any particular view, so it isn't affected by that.
     *  3. Relaunching the app - note this is a weak fallback: if it's still
     *     the foreground app, it just resumes exactly where it was (i.e. it
     *     can be a no-op, not a real recovery).
     *  4. The device home screen, as an absolute last resort.
     *
     * Since performGlobalAction's return value only means "the framework
     * accepted the request", not "navigation actually changed" - the caller
     * doesn't gate this to firing once; it's called again on every
     * subsequent tick while still blocked, so an attempt that silently
     * didn't move the screen gets retried rather than giving up after one try.
     */
    fun goToPreviousScreen(service: AccessibilityService, app: TargetApp) {
        val resourceId = primaryTabResourceId(app)
        val tabId = resourceId?.let { "${app.packageName}:id/$it" }
        val root = service.rootInActiveWindow
        val tab = tabId?.let { root?.findAccessibilityNodeInfosByViewId(it)?.firstOrNull() }
        if (tab != null && tab.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) return

        val launchIntent = service.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            service.startActivity(launchIntent)
            return
        }

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}

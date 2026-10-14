package com.reellock.reellock.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.reellock.reellock.classifier.InstagramUiSignatures
import com.reellock.reellock.classifier.ScreenType

/**
 * Leaves Reels without leaving Instagram. need.md section 5: "DMs, feed,
 * profile and search remain accessible" - kicking the user all the way to
 * the Android launcher (the previous GLOBAL_ACTION_HOME behavior) was
 * harsher than necessary and didn't match that framing.
 */
object InstagramNavigation {

    private fun tabResourceId(screen: ScreenType?): String = when (screen) {
        ScreenType.DIRECT_MESSAGES -> "direct_tab"
        ScreenType.PROFILE -> "profile_tab"
        ScreenType.SEARCH -> "search_tab"
        // FEED, OTHER_NON_REELS, or no prior screen recorded yet (first
        // thing the user did this session was open Reels directly).
        else -> "feed_tab"
    }

    /**
     * Returns to whichever Instagram tab the user was actually on right
     * before they opened Reels (tracked by the caller - see
     * EnforcementManager.lastNonReelsScreen).
     *
     * Tries, in order:
     *  1. Tap that tab's own bottom-nav button via the standard Accessibility
     *     API (findAccessibilityNodeInfosByViewId + ACTION_CLICK) - the same
     *     way a real tap would.
     *  2. GLOBAL_ACTION_BACK. Needed because real-device logs showed
     *     Instagram hides its bottom-nav chrome entirely during full
     *     immersive Reels playback - the exact moment this runs - so step 1
     *     routinely finds no node to click at all. Back doesn't depend on
     *     finding any particular view, so it isn't affected by that.
     *  3. Relaunching Instagram - note this is a weak fallback: if Instagram
     *     is still the foreground app, it just resumes exactly where it was
     *     (i.e. it can be a no-op, not a real recovery).
     *  4. The device home screen, as an absolute last resort.
     *
     * Since performGlobalAction's return value only means "the framework
     * accepted the request", not "navigation actually changed" - the caller
     * doesn't gate this to firing once; it's called again on every
     * subsequent tick while still blocked, so an attempt that silently
     * didn't move the screen gets retried rather than giving up after one try.
     */
    fun goToPreviousScreen(service: AccessibilityService, previousScreen: ScreenType?) {
        val tabId = "${InstagramUiSignatures.INSTAGRAM_PACKAGE}:id/${tabResourceId(previousScreen)}"
        val root = service.rootInActiveWindow
        val tab = root?.findAccessibilityNodeInfosByViewId(tabId)?.firstOrNull()
        if (tab != null && tab.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) return

        val launchIntent = service.packageManager.getLaunchIntentForPackage(InstagramUiSignatures.INSTAGRAM_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            service.startActivity(launchIntent)
            return
        }

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}

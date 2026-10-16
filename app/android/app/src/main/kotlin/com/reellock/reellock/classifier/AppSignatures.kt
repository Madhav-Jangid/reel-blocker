package com.reellock.reellock.classifier

/**
 * The short-form-video apps ReelLock can monitor. Adding a new app means
 * adding a case here plus an entry in [AppSignatureRegistry] - everything
 * else (classifier, navigation, enforcement, budget) is already app-agnostic.
 */
enum class TargetApp(val packageName: String, val appName: String, val displayName: String) {
    INSTAGRAM("com.instagram.android", "Instagram", "Instagram Reels"),
    YOUTUBE("com.google.android.youtube", "YouTube", "YouTube Shorts"),
}

/**
 * Best-effort, versioned heuristics for recognizing one app's screens from
 * accessibility node resource-ids. See [AppSignatureRegistry] for the
 * per-app calibration status.
 *
 * Deliberately conservative: a resource-id match is required for HIGH
 * confidence. A text/content-description-only match (e.g. a "Reels" label
 * that can also appear while browsing the feed) is never enough on its own -
 * per need.md: "Do not rely exclusively on labels such as 'Reels'."
 */
data class AppSignatures(
    /** Resource-id substrings strongly associated with the short-video full-screen vertical viewer. */
    val shortVideoResourceIdSignals: List<String>,
    /** Bottom-nav / tab resource ids whose selected state additionally corroborates a short-video classification. */
    val shortVideoTabSignals: List<String>,
    /** Resource-id substrings for other recognized (non-short-video) screens, checked first so a retained/stray short-video id never overrides a clear other-screen signal. */
    val otherKnownResourceIdSignals: List<String>,
)

object AppSignatureRegistry {

    /**
     * Calibrated against a real device across this project's development
     * (see classifier/ScreenClassifierTest.kt and this list's own history for
     * the false positives that were found and removed). Still subject to
     * drift whenever Instagram changes its UI - see need.md section 11.
     */
    private val instagram = AppSignatures(
        shortVideoResourceIdSignals = listOf(
            "clips_viewer",
            "reel_viewer",
            "reels_viewer",
            "clips_swipe_refresh_layout",
            "clips_tab_fragment",
            "reel_viewer_timestamp",
            "clips_player_surface",
            "reel_viewer_view_pager",
            "clips_progress_bar",
        ),
        shortVideoTabSignals = listOf(
            "clips_tab",
            "reels_tab_avatar",
        ),
        // Deliberately excludes "feed_tab"/"profile_tab": those are bottom-nav
        // icons present on every main screen (including Reels), not just
        // their own screen - using them here caused every screen to
        // misclassify. "profile_header" is excluded too: real-device logs
        // showed "row_feed_profile_header" present on Reels screens too
        // (a retained/shared element, not profile-exclusive).
        otherKnownResourceIdSignals = listOf(
            "direct_inbox",
            "direct_thread",
            "direct_text_input",
            "inbox_fragment",
            "feed_timeline",
            "newsfeed",
            "main_feed",
            "profile_grid",
            "user_detail_info",
            "action_bar_search",
            "search_edit_text",
            "explore_fragment",
            "discover_people",
        ),
    )

    /**
     * UNVERIFIED / BEST-EFFORT. Unlike Instagram's list above, none of these
     * substrings have been confirmed against a real, installed YouTube
     * build - there was no device with YouTube available to calibrate
     * against while this was written (same starting position Instagram's
     * list was in before this project's own live-device debugging rounds).
     * They are plausible guesses based on publicly-known YouTube Android
     * view-id naming conventions, nothing more.
     *
     * Expect this to need the exact same calibration cycle Instagram went
     * through: install YouTube, open Shorts, watch the Activity tab's "Live"
     * feed (ScreenClassifier.dumpResourceIds) for the actual ids that appear,
     * and correct this list against real evidence before trusting it to
     * block anything. Until then the safe failure mode is under-detection
     * (classified UNKNOWN, not blocked) rather than false blocking elsewhere,
     * because these ids aren't used anywhere else in this app.
     */
    private val youtube = AppSignatures(
        shortVideoResourceIdSignals = listOf(
            "reel_player",
            "reel_watch_player",
            "reel_recycler",
            "shorts_container",
            "shorts_player",
            "reel_dyn_overlay",
        ),
        shortVideoTabSignals = listOf(
            "pivot_shorts",
            "shorts_tab",
        ),
        otherKnownResourceIdSignals = listOf(
            "pivot_home",
            "home_feed",
            "pivot_subscriptions",
            "pivot_library",
            "search_edit_box",
            "watch_player",
        ),
    )

    private val byApp: Map<TargetApp, AppSignatures> = mapOf(
        TargetApp.INSTAGRAM to instagram,
        TargetApp.YOUTUBE to youtube,
    )

    fun signaturesFor(app: TargetApp): AppSignatures = byApp.getValue(app)

    /** Looks up the monitored app (if any) that an accessibility event's package belongs to. */
    fun forPackage(packageName: String): TargetApp? = TargetApp.entries.find { it.packageName == packageName }
}

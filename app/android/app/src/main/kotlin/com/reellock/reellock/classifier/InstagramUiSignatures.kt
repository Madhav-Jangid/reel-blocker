package com.reellock.reellock.classifier

/**
 * Best-effort, versioned heuristics for recognizing Instagram screens from
 * accessibility node resource-ids / content descriptions.
 *
 * IMPORTANT (need.md section 5 and section 11): these substrings were chosen
 * from publicly documented/observed Instagram view-id naming conventions at
 * the time this was written. Instagram changes its UI without notice and
 * without changing its package name; this list WILL drift and needs
 * revalidation against the actual installed Instagram version before this
 * app is trusted for real enforcement. Ship this with the diagnostic
 * classification screen (P2) so drift is visible instead of silent.
 *
 * Deliberately conservative: a resource-id match is required for HIGH
 * confidence. A text/content-description-only match (e.g. a "Reels" label
 * that can also appear while browsing the feed) is never enough on its own -
 * per need.md: "Do not rely exclusively on labels such as 'Reels'."
 */
object InstagramUiSignatures {

    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** Resource-id substrings strongly associated with the Reels full-screen vertical viewer. */
    val REELS_RESOURCE_ID_SIGNALS: List<String> = listOf(
        "clips_viewer",
        "reel_viewer",
        "reels_viewer",
        "clips_swipe_refresh_layout",
        "clips_tab_fragment",
        "reel_viewer_timestamp",
        "clips_player_surface",
        "reel_viewer_view_pager",
        "clips_progress_bar",
    )

    /** Bottom-nav / tab resource ids whose selected state additionally corroborates a Reels classification. */
    val REELS_TAB_SIGNALS: List<String> = listOf(
        "clips_tab",
        "reels_tab_avatar",
    )

    val DIRECT_MESSAGES_RESOURCE_ID_SIGNALS: List<String> = listOf(
        "direct_inbox",
        "direct_thread",
        "direct_text_input",
        "inbox_fragment",
    )

    // Deliberately excludes "feed_tab": that's the bottom-nav icon, which
    // stays present in the tree on every main screen (including Reels), not
    // just the feed. Using it here caused every screen to misclassify as
    // FEED, since FEED is checked before REELS. Same reasoning excludes
    // "profile_tab" below. Only ids that identify actual feed *content* belong here.
    val FEED_RESOURCE_ID_SIGNALS: List<String> = listOf(
        "feed_timeline",
        "newsfeed",
        "main_feed",
    )

    // "profile_header" deliberately excluded: real-device logs showed
    // "row_feed_profile_header" present on Reels screens too (looks like a
    // retained/shared element, not profile-exclusive), which made Reels
    // misclassify as PROFILE once the feed_tab false-positive was fixed.
    val PROFILE_RESOURCE_ID_SIGNALS: List<String> = listOf(
        "profile_grid",
        "user_detail_info",
    )

    val SEARCH_RESOURCE_ID_SIGNALS: List<String> = listOf(
        "action_bar_search",
        "search_edit_text",
        "explore_fragment",
        "discover_people",
    )

    /** Minimum nodes required in a dumped tree before a classification is trusted; fewer implies the UI is still loading (TRANSITION). */
    const val MIN_NODES_FOR_CONFIDENT_CLASSIFICATION = 4
}

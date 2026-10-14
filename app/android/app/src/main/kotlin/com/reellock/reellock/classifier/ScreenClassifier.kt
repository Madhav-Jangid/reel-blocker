package com.reellock.reellock.classifier

import com.reellock.reellock.commitment.Confidence

enum class ScreenType {
    REELS,
    FEED,
    DIRECT_MESSAGES,
    PROFILE,
    SEARCH,
    OTHER_NON_REELS,
}

sealed class ScreenClassification {
    data class Classified(val screen: ScreenType, val confidence: Confidence) : ScreenClassification()
    object Transition : ScreenClassification()
    object Unknown : ScreenClassification()
}

/**
 * Pure, synchronously-testable classification logic (need.md section 5: four
 * outcomes NON_REELS / REELS / TRANSITION / UNKNOWN - here NON_REELS is
 * expanded into its concrete screen types since enforcement needs to tell
 * DMs/feed/profile/search apart for the diagnostic screen, even though they
 * are all "allow" for blocking purposes).
 *
 * Order of evaluation matters and is deliberately conservative:
 *   1. Too few nodes -> TRANSITION (UI still loading/animating).
 *   2. Any explicit non-Reels structural signal -> that screen, HIGH
 *      confidence. Checked first so a stray "Reels" label inside, say, a
 *      feed post's attached clip never overrides a clear feed/DM/profile
 *      signal (need.md: avoid blocking ordinary feed videos).
 *   3. A Reels structural signal -> REELS, confidence raised to HIGH if a
 *      tab-selection signal also corroborates it, else MEDIUM.
 *   4. Nothing recognized -> UNKNOWN (caller applies blockUnknownScreens policy).
 *
 * There used to be a step 4 here: a label-only ("Reels"/"Original audio")
 * fallback to REELS at LOW confidence. Real-device logs showed Instagram's
 * bottom-nav tab bar icon carries contentDescription "Reels" as its
 * accessibility label, present on literally every main screen (Feed,
 * Profile, Search...) whether or not Reels is actually open - so that
 * fallback classified ordinary feed scrolling as REELS. Removed entirely
 * rather than narrowed, per need.md's own warning: "Do not rely exclusively
 * on labels such as 'Reels'." A screen with no structural match is UNKNOWN,
 * never a guessed REELS.
 *
 * Second, separate issue found the same way: Instagram keeps the Reels
 * viewer fragment attached-but-off-screen after you navigate away from it,
 * so `clips_viewer_view_pager` and friends kept showing up in dumps of
 * Feed/DM/Profile/Search too, for the rest of the session once Reels had
 * been opened once - every screen after that matched `reelsStructural()`.
 * Every id collected for classification now requires
 * `UiNode.isVisibleToUser == true`, so a retained-but-hidden fragment's ids
 * are never counted.
 */
object ScreenClassifier {

    fun classify(root: UiNode?): ScreenClassification {
        if (root == null) return ScreenClassification.Transition

        val dump = StringPool()
        val nodeCount = collect(root, dump, depth = 0)

        if (nodeCount < InstagramUiSignatures.MIN_NODES_FOR_CONFIDENT_CLASSIFICATION) {
            return ScreenClassification.Transition
        }

        dump.directMessages().takeIf { it }?.let {
            return ScreenClassification.Classified(ScreenType.DIRECT_MESSAGES, Confidence.HIGH)
        }
        dump.feed().takeIf { it }?.let {
            return ScreenClassification.Classified(ScreenType.FEED, Confidence.HIGH)
        }
        dump.profile().takeIf { it }?.let {
            return ScreenClassification.Classified(ScreenType.PROFILE, Confidence.HIGH)
        }
        dump.search().takeIf { it }?.let {
            return ScreenClassification.Classified(ScreenType.SEARCH, Confidence.HIGH)
        }

        if (dump.reelsStructural()) {
            val confidence = if (dump.reelsTabSelected()) Confidence.HIGH else Confidence.MEDIUM
            return ScreenClassification.Classified(ScreenType.REELS, confidence)
        }

        return ScreenClassification.Unknown
    }

    /**
     * Diagnostic-only: the raw resource-ids seen on screen, with no
     * filtering/matching applied. Used by the Diagnostics screen so a real
     * device's actual Instagram build can be used to calibrate
     * InstagramUiSignatures, instead of guessing (need.md P2: "a diagnostic
     * screen that records classifications without collecting message
     * contents" - deliberately resource-ids only, never `text` or
     * `contentDescription`, since those can carry captions/usernames/DM
     * content).
     */
    fun dumpResourceIds(root: UiNode?): List<String> {
        if (root == null) return emptyList()
        val ids = mutableListOf<String>()
        collectIds(root, ids, depth = 0)
        return ids.distinct()
    }

    private fun collectIds(node: UiNode, ids: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH || ids.size > MAX_NODES) return
        // Visible-only, same as classify() - otherwise this would show ids
        // from Instagram's retained-off-screen fragments too, which is
        // confusing for calibrating InstagramUiSignatures against what's
        // actually on screen.
        if (node.isVisibleToUser) {
            node.viewIdResourceName?.let { ids += it }
        }
        val childCount = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            val child = node.childAt(i) ?: continue
            collectIds(child, ids, depth + 1)
        }
    }

    private fun collect(node: UiNode, pool: StringPool, depth: Int): Int {
        if (depth > MAX_DEPTH) return 0
        var count = 1
        if (node.isVisibleToUser) {
            pool.add(node.viewIdResourceName)
        }
        val childCount = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            val child = node.childAt(i) ?: continue
            count += collect(child, pool, depth + 1)
            if (count > MAX_NODES) break
        }
        return count
    }

    private const val MAX_DEPTH = 40
    private const val MAX_CHILDREN_PER_NODE = 64
    private const val MAX_NODES = 600

    // Resource-ids only - no contentDescription/text are read for
    // classification at all, not even transiently. Beyond need.md's "don't
    // collect message content" principle, real-device evidence showed label
    // text is actively unreliable here (see the REELS fallback removal above).
    private class StringPool {
        private val resourceIds = mutableListOf<String>()

        fun add(resourceId: String?) {
            resourceId?.let { resourceIds += it.lowercase() }
        }

        private fun resourceIdsContainAny(signals: List<String>) = resourceIds.any { id -> signals.any { id.contains(it) } }

        fun directMessages() = resourceIdsContainAny(InstagramUiSignatures.DIRECT_MESSAGES_RESOURCE_ID_SIGNALS)
        fun feed() = resourceIdsContainAny(InstagramUiSignatures.FEED_RESOURCE_ID_SIGNALS)
        fun profile() = resourceIdsContainAny(InstagramUiSignatures.PROFILE_RESOURCE_ID_SIGNALS)
        fun search() = resourceIdsContainAny(InstagramUiSignatures.SEARCH_RESOURCE_ID_SIGNALS)
        fun reelsStructural() = resourceIdsContainAny(InstagramUiSignatures.REELS_RESOURCE_ID_SIGNALS)
        fun reelsTabSelected() = resourceIdsContainAny(InstagramUiSignatures.REELS_TAB_SIGNALS)
    }
}

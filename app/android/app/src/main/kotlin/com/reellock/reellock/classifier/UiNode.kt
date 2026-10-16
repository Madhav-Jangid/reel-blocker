package com.reellock.reellock.classifier

/**
 * Minimal view-tree abstraction over android.view.accessibility.AccessibilityNodeInfo
 * so ScreenClassifier can be exercised by plain JVM unit tests with fake trees,
 * without touching the Android framework. See AccessibilityNodeInfoUiNode for
 * the real adapter used by ReelLockAccessibilityService.
 */
interface UiNode {
    val className: String?
    val viewIdResourceName: String?
    val contentDescription: String?
    val text: String?
    val childCount: Int
    /**
     * Whether this node is actually visible on screen right now, as opposed
     * to merely present in the tree. Instagram keeps the Reels viewer
     * fragment "warm" (attached but off-screen) after navigating away from
     * it, so its resource-ids keep showing up in later dumps of Feed/DM/
     * Profile/Search - without this check, ScreenClassifier would keep
     * matching REELS on every screen for the rest of the session once Reels
     * had been opened once. See AccessibilityNodeInfo.isVisibleToUser.
     */
    val isVisibleToUser: Boolean
    fun childAt(index: Int): UiNode?
}

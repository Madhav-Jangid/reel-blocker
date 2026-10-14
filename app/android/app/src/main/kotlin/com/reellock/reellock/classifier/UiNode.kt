package com.reellock.reellock.classifier

/**
 * Minimal view-tree abstraction over android.view.accessibility.AccessibilityNodeInfo
 * so ScreenClassifier can be exercised by plain JVM unit tests with fake trees,
 * without touching the Android framework. See AccessibilityNodeInfoUiNode for
 * the real adapter used by InstagramAccessibilityService.
 */
interface UiNode {
    val className: String?
    val viewIdResourceName: String?
    val contentDescription: String?
    val text: String?
    val childCount: Int
    fun childAt(index: Int): UiNode?
}

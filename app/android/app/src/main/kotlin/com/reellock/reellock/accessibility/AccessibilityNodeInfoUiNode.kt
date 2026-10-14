package com.reellock.reellock.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.reellock.reellock.classifier.UiNode

/** Real adapter from the Android accessibility tree to the testable [UiNode] abstraction. */
class AccessibilityNodeInfoUiNode(private val node: AccessibilityNodeInfo) : UiNode {
    override val className: String? get() = node.className?.toString()
    override val viewIdResourceName: String? get() = node.viewIdResourceName
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val text: String? get() = node.text?.toString()
    override val childCount: Int get() = node.childCount

    override fun childAt(index: Int): UiNode? {
        val child = node.getChild(index) ?: return null
        return AccessibilityNodeInfoUiNode(child)
    }
}

package com.reellock.reellock.classifier

/** Plain-JVM test double for [UiNode], so ScreenClassifier can be exercised without AccessibilityNodeInfo. */
class FakeUiNode(
    override val className: String? = null,
    override val viewIdResourceName: String? = null,
    override val contentDescription: String? = null,
    override val text: String? = null,
    override val isVisibleToUser: Boolean = true,
    private val children: List<FakeUiNode> = emptyList(),
) : UiNode {
    override val childCount: Int get() = children.size
    override fun childAt(index: Int): UiNode? = children.getOrNull(index)
}

fun node(
    resourceId: String? = null,
    description: String? = null,
    text: String? = null,
    visible: Boolean = true,
    vararg children: FakeUiNode,
) = FakeUiNode(viewIdResourceName = resourceId, contentDescription = description, text = text, isVisibleToUser = visible, children = children.toList())

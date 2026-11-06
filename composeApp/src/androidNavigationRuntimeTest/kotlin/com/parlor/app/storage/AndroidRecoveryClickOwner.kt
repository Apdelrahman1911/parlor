package com.parlor.app.storage

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Compose's Android bridge puts a merged button's content description on a
 * non-clickable child. Activate its nearest owning action, not the label leaf.
 * Never cross a sibling, foreign-package, hidden or disabled action boundary.
 */
internal fun androidRecoveryClickOwner(
    node: AccessibilityNodeInfo,
    parentAction: AccessibilityNodeInfo?,
    targetPackage: String,
): AccessibilityNodeInfo? {
    if (node.packageName?.toString() != targetPackage || !node.isVisibleToUser || !node.isEnabled) return null
    val hasClickAction = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    return if (node.isClickable || hasClickAction) {
        node.takeIf { it.isClickable && hasClickAction }
    } else {
        parentAction
    }
}

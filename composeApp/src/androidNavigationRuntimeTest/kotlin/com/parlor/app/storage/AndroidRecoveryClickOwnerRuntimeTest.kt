package com.parlor.app.storage

import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo

/** Native node contracts supplement, not replace, the actual Home journeys. */
@Suppress("DEPRECATION")
class AndroidRecoveryClickOwnerRuntimeTest : InstrumentationTestCase() {
    fun testDirectClickableLabelOwnsItsAction() = withNodes { outer, inner, _ ->
        assertSame(inner, androidRecoveryClickOwner(inner, outer, PACKAGE))
    }

    fun testNonClickableDescriptionChildResolvesToNearestOwner() = withNodes { outer, inner, label ->
        label.contentDescription = "synthetic public label"
        assertFalse(label.isClickable)
        val owner = androidRecoveryClickOwner(inner, outer, PACKAGE)
        assertSame(inner, androidRecoveryClickOwner(label, owner, PACKAGE))
    }

    fun testLabelWithoutAncestorDoesNotSelectASiblingAction() = withNodes { _, _, label ->
        assertNull(androidRecoveryClickOwner(label, null, PACKAGE))
    }

    fun testDisabledHiddenOrForeignActionBlocksOuterFallback() = withNodes { outer, inner, label ->
        inner.isEnabled = false
        var owner = androidRecoveryClickOwner(inner, outer, PACKAGE)
        assertNull(owner)
        assertNull(androidRecoveryClickOwner(label, owner, PACKAGE))
        inner.isEnabled = true
        inner.isVisibleToUser = false
        assertNull(androidRecoveryClickOwner(inner, outer, PACKAGE))
        inner.isVisibleToUser = true
        inner.packageName = "synthetic.other"
        owner = androidRecoveryClickOwner(inner, outer, PACKAGE)
        assertNull(owner)
        assertNull(androidRecoveryClickOwner(label, owner, PACKAGE))
    }

    fun testClickableWithoutAdvertisedActionCannotFallbackToOuter() = withNodes { outer, inner, label ->
        inner.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        val owner = androidRecoveryClickOwner(inner, outer, PACKAGE)
        assertNull(owner)
        assertNull(androidRecoveryClickOwner(label, owner, PACKAGE))
    }

    private fun withNodes(assertions: (AccessibilityNodeInfo, AccessibilityNodeInfo, AccessibilityNodeInfo) -> Unit) {
        val outer = node(clickable = true)
        val inner = node(clickable = true)
        val label = node(clickable = false)
        try {
            assertions(outer, inner, label)
        } finally {
            label.recycle()
            inner.recycle()
            outer.recycle()
        }
    }

    private fun node(clickable: Boolean) = AccessibilityNodeInfo.obtain().apply {
        packageName = PACKAGE
        isVisibleToUser = true
        isEnabled = true
        isClickable = clickable
        if (clickable) addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    private companion object {
        const val PACKAGE = "synthetic.parlor"
    }
}

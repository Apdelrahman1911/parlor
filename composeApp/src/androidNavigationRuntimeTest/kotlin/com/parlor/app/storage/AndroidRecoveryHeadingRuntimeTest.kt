package com.parlor.app.storage

import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo

/** Public native role/text controls; actual Settings/Home are exercised by all process cases. */
@Suppress("DEPRECATION")
class AndroidRecoveryHeadingRuntimeTest : InstrumentationTestCase() {
    fun testExactEnglishAndArabicHeadingsDoNotNeedAClickAction() = withNode { node ->
        node.isHeading = true
        for (text in listOf("Settings", "الإعدادات")) {
            node.text = text
            assertTrue(androidRecoveryHeading(node, text))
            assertFalse(node.isClickable)
        }
    }

    fun testNavigationCaptionCannotAcknowledgeDestinationEvenWhenClickable() = withNode { node ->
        node.text = "الإعدادات"
        node.isClickable = true
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        assertFalse(androidRecoveryHeading(node, "الإعدادات"))
    }

    fun testWrongLanguageOrPartialHeadingCannotAcknowledgeDestination() = withNode { node ->
        node.isHeading = true
        node.text = "Settings"
        assertFalse(androidRecoveryHeading(node, "الإعدادات"))
        assertFalse(androidRecoveryHeading(node, "Setting"))
        node.text = "Settings help"
        assertFalse(androidRecoveryHeading(node, "Settings"))
    }

    fun testMatchingDescriptionDoesNotReplaceTheHeadingText() = withNode { node ->
        node.isHeading = true
        node.contentDescription = "Settings"
        assertFalse(androidRecoveryHeading(node, "Settings"))
        node.text = "Another public heading"
        assertFalse(androidRecoveryHeading(node, "Settings"))
    }

    private fun withNode(assertions: (AccessibilityNodeInfo) -> Unit) {
        val node = AccessibilityNodeInfo.obtain()
        try {
            assertions(node)
        } finally {
            node.recycle()
        }
    }
}

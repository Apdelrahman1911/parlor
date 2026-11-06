package com.parlor.app.storage

import android.app.Instrumentation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Bounded observation only; an action is sent once and never retried on failure. */
@Suppress("DEPRECATION")
internal class AndroidPublicRecoveryUi(private val instrumentation: Instrumentation) {
    fun clickDescription(description: String) {
        val node = awaitAction(description)
        try {
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Native recovery action was rejected; it will not be repeated"
            }
        } finally {
            node.recycle()
        }
    }

    fun awaitDescription(description: String) {
        awaitAction(description).recycle()
    }

    private fun awaitAction(description: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + OBSERVATION_TIMEOUT_MILLIS
        do {
            val node = findAction(description)
            if (node != null) return node
            SystemClock.sleep(OBSERVATION_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        // Never dump a native accessibility tree: even a public screen can
        // share a window with input methods or other platform-owned content.
        error("Expected unique, visible, enabled native recovery action was not observed")
    }

    private fun findAction(description: String): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            check(++visited <= MAX_NODES && depth <= MAX_DEPTH) { "Native recovery tree exceeded observation bound" }
            if (node.packageName?.toString() == instrumentation.targetContext.packageName) {
                check(!node.text.orEmpty().contains("SyntheticSeat") &&
                    !node.contentDescription.orEmpty().contains("SyntheticSeat")) {
                    "Home exposed synthetic gameplay state outside its metadata-only recovery UI"
                }
                if (node.isVisibleToUser && node.isEnabled && node.isClickable &&
                    node.contentDescription?.toString() == description
                ) {
                    matches += AccessibilityNodeInfo.obtain(node)
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
        try {
            visit(root, 0)
            check(matches.size <= 1) { "Ambiguous native recovery action; no action sent" }
            return matches.singleOrNull()?.also { matches.clear() }
        } finally {
            root.recycle()
            matches.forEach(AccessibilityNodeInfo::recycle)
        }
    }

    private companion object {
        const val OBSERVATION_TIMEOUT_MILLIS = 10_000L
        const val OBSERVATION_INTERVAL_MILLIS = 100L
        const val MAX_NODES = 4_096
        const val MAX_DEPTH = 64
    }
}

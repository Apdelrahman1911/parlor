package com.parlor.app.storage

import android.app.Instrumentation
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Bounded observation only; an action is sent once and never retried on failure. */
@Suppress("DEPRECATION")
internal class AndroidPublicRecoveryUi(private val instrumentation: Instrumentation) {
    private var observation = AndroidRecoveryObservation()
    private var reportedCompoundAction = false

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

    fun awaitPoliteStatus(text: String) {
        awaitNode(resolveClickOwner = false) {
            it.text?.toString() == text && it.liveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.recycle()
    }

    private fun awaitAction(description: String): AccessibilityNodeInfo = awaitNode(resolveClickOwner = true) {
        it.contentDescription?.toString() == description
    }

    private fun awaitNode(
        resolveClickOwner: Boolean,
        matchesExpected: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + OBSERVATION_TIMEOUT_MILLIS
        do {
            val node = findNode(resolveClickOwner, matchesExpected)
            if (node != null) return node
            SystemClock.sleep(OBSERVATION_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        // Never dump a native accessibility tree: even a public screen can
        // share a window with input methods or other platform-owned content.
        error("Expected unique, visible, enabled native recovery node was not observed: $observation")
    }

    private fun findNode(
        resolveClickOwner: Boolean,
        matchesExpected: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        observation = AndroidRecoveryObservation()
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        observation.rootPresent = true
        val matches = mutableListOf<AccessibilityNodeInfo>()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int, parentAction: AccessibilityNodeInfo?) {
            check(++visited <= MAX_NODES && depth <= MAX_DEPTH) { "Native recovery tree exceeded observation bound" }
            val action = androidRecoveryClickOwner(node, parentAction, instrumentation.targetContext.packageName)
            if (node.packageName?.toString() == instrumentation.targetContext.packageName) {
                observation.targetNodes++
                check(!node.text?.toString().orEmpty().contains("SyntheticSeat") &&
                    !node.contentDescription?.toString().orEmpty().contains("SyntheticSeat")) {
                    "Home exposed synthetic gameplay state outside its metadata-only recovery UI"
                }
                if (node.isVisibleToUser && node.isEnabled && matchesExpected(node)) {
                    observation.exactLabels++
                    if (node.isClickable) observation.sameNodeActions++
                    val selected = if (resolveClickOwner) action else node
                    if (selected != null && matches.none { it == selected }) {
                        matches += AccessibilityNodeInfo.obtain(selected)
                        observation.resolvedNodes++
                    }
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1, action)
                } finally {
                    child.recycle()
                }
            }
        }
        try {
            visit(root, 0, null)
            check(matches.size <= 1) { "Ambiguous native recovery action; no action sent" }
            if (resolveClickOwner && matches.size == 1 && observation.sameNodeActions == 0 && !reportedCompoundAction) {
                // Fixed, non-sensitive evidence of the actual native label/action
                // boundary. Never print descriptions, node trees or player data.
                Log.i("ParlorRecoveryTest", "Exact public label resolved to its unique clickable ancestor")
                reportedCompoundAction = true
            }
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

/** Counts only; failure evidence must not include arbitrary accessibility text or package names. */
private data class AndroidRecoveryObservation(
    var rootPresent: Boolean = false,
    var targetNodes: Int = 0,
    var exactLabels: Int = 0,
    var sameNodeActions: Int = 0,
    var resolvedNodes: Int = 0,
)

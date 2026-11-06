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

    /** A shared navigation caption is not evidence that its destination was composed. */
    fun awaitHeading(text: String) {
        awaitNode(resolveClickOwner = false) { androidRecoveryHeading(it, text) }.recycle()
    }

    /** Fixed public heading probe for failure-only evidence, never a tree/text dump. */
    fun headingPresentNow(text: String): Boolean {
        val node = findNode(resolveClickOwner = false) { androidRecoveryHeading(it, text) }
        return (node != null).also { node?.recycle() }
    }

    /** One read-only probe of a whitelisted public label, only after an assertion failed. */
    fun descriptionPresentNow(description: String): Boolean {
        val node = findNode(resolveClickOwner = true) { it.contentDescription?.toString() == description }
        return (node != null).also { node?.recycle() }
    }

    /** Exact public status text only, after failure; never return arbitrary node content. */
    fun textPresentNow(text: String): Boolean {
        val node = findNode(resolveClickOwner = false) { it.text?.toString() == text }
        return (node != null).also { node?.recycle() }
    }

    /** One known adjacent Home page, never a repeated click or event-dependent scroll loop. */
    fun revealDescription(description: String, forward: Boolean = true) {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        // A deletion can remove the need/ability to scroll while Home is still
        // settling. Keep observing the actual target; one early absent/disabled
        // sample must not commit us to waiting only for a backward action.
        val choice = awaitObservation {
            androidRecoveryRevealObservation(
                target = { findNode(resolveClickOwner = true) { it.contentDescription?.toString() == description } },
                scroll = {
                    findNode(resolveClickOwner = false) { node ->
                        node.isScrollable && node.actionList.any { it.id == action }
                    }
                },
            )
        }
        try {
            if (!choice.needsScroll) return
            check(choice.node.performAction(action)) { "Native Home scroll was rejected; no action will be repeated" }
        } finally {
            choice.node.recycle()
        }
        // UiAutomation is not guaranteed a Compose TYPE_VIEW_SCROLLED event.
        // Observe the original exact target, with the same bounded assertion.
        awaitDescription(description)
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
    ): AccessibilityNodeInfo = awaitObservation { findNode(resolveClickOwner, matchesExpected) }

    private fun <T : Any> awaitObservation(observe: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + OBSERVATION_TIMEOUT_MILLIS
        do {
            val node = observe()
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
                val expected = matchesExpected(node)
                if (expected) {
                    observation.matchingLabels++
                    if (node.isVisibleToUser) observation.visibleLabels++
                    if (node.isEnabled) observation.enabledLabels++
                }
                if (node.isVisibleToUser && node.isEnabled && expected) {
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

/** Visibility, package ownership and uniqueness are checked by the bounded native traversal. */
internal fun androidRecoveryHeading(node: AccessibilityNodeInfo, text: String): Boolean =
    node.isHeading && node.text?.toString() == text

/** Counts only; failure evidence must not include arbitrary accessibility text or package names. */
private data class AndroidRecoveryObservation(
    var rootPresent: Boolean = false,
    var targetNodes: Int = 0,
    var matchingLabels: Int = 0,
    var visibleLabels: Int = 0,
    var enabledLabels: Int = 0,
    var exactLabels: Int = 0,
    var sameNodeActions: Int = 0,
    var resolvedNodes: Int = 0,
)

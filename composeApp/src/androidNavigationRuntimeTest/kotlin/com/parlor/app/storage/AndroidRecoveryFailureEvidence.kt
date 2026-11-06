package com.parlor.app.storage

import android.app.Activity
import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.util.Locale

/** Caller supplies only the two fixed public game-title descriptions, never player/session text. */
internal data class AndroidRecoveryPublicLabels(val englishLocal: String? = null, val arabicLocal: String? = null)

internal data class AndroidRecoveryPublicCounts(
    var englishSettings: Int = 0,
    var arabicSettings: Int = 0,
    var englishHeading: Int = 0,
    var arabicHeading: Int = 0,
    var englishLocal: Int = 0,
    var arabicLocal: Int = 0,
    var homeTitle: Int = 0,
    var checking: Int = 0,
    var unavailable: Int = 0,
) {
    fun observe(labels: AndroidRecoveryPublicLabels, text: String?, descriptions: List<String>, heading: Boolean) {
        if (text == "Settings") { englishSettings++; if (heading) englishHeading++ }
        if (text == "الإعدادات") { arabicSettings++; if (heading) arabicHeading++ }
        if (labels.englishLocal != null && labels.englishLocal in descriptions) englishLocal++
        if (labels.arabicLocal != null && labels.arabicLocal in descriptions) arabicLocal++
        if (text == "Make room for play." || text == "لنجتمع على لعبة.") homeTitle++
        if (text == "Checking saved games and resumable sessions…" ||
            text == "جارٍ التحقق من الألعاب المحفوظة والجلسات القابلة للاستئناف…") checking++
        if (text == "Some recovery information is unavailable." || text == "بعض معلومات الاستعادة غير متاحة.") {
            unavailable++
        }
    }
}

/** Native shown means visible/enabled; Compose shown is in-bounds geometry only, NOT native hittability. */
internal data class AndroidRecoveryPublicState(
    var roots: Int = 0,
    var pendingMeasureRoots: Int = 0,
    val all: AndroidRecoveryPublicCounts = AndroidRecoveryPublicCounts(),
    val shown: AndroidRecoveryPublicCounts = AndroidRecoveryPublicCounts(),
) {
    fun observe(labels: AndroidRecoveryPublicLabels, text: String?, descriptions: List<String>, heading: Boolean,
                isShown: Boolean) {
        all.observe(labels, text, descriptions, heading)
        if (isShown) shown.observe(labels, text, descriptions, heading)
    }
}

/**
 * Failure-only boundary readback. No touch, focus, layout, timing, accessibility
 * enablement or application mutation. clearCache changes only UiAutomation's
 * observer cache AFTER the original assertion has failed; it never supplies a
 * passing result. Before/after are sequential samples, not an atomic UI trace.
 */
@Suppress("DEPRECATION")
internal class AndroidRecoveryFailureEvidence(
    private val instrumentation: Instrumentation,
    private val activity: Activity,
    private val labels: AndroidRecoveryPublicLabels,
) {
    fun capture(): String {
        val start = SystemClock.uptimeMillis()
        val composeBefore = composeState()
        val ui = AndroidPublicRecoveryUi(instrumentation)
        val cached = ui.publicStateNow(labels)
        val cacheCleared = instrumentation.uiAutomation.clearCache()
        val fresh = ui.publicStateNow(labels)
        val composeAfter = composeState()
        var platform = "unobserved"
        instrumentation.runOnMainSync {
            platform = "default=${language(Locale.getDefault().language)}, " +
                "compose=${language(androidx.compose.ui.text.intl.Locale.current.language)}, " +
                "activity=${language(activity.resources.configuration.locales[0].language)}, " +
                "focused=${activity.hasWindowFocus()}, destroyed=${activity.isDestroyed}"
        }
        return "composeBefore=$composeBefore; cached=$cached; cacheCleared=$cacheCleared; " +
            "fresh=$fresh; composeAfter=$composeAfter; $platform; elapsedMs=${SystemClock.uptimeMillis() - start}"
    }

    private fun composeState(): AndroidRecoveryPublicState {
        val state = AndroidRecoveryPublicState()
        instrumentation.runOnMainSync {
            var views = 0
            var nodes = 0
            fun visit(view: View, depth: Int) {
                check(++views <= 4_096 && depth <= 64) { "Public evidence view bound exceeded" }
                if (view is ViewRootForTest) {
                    state.roots++
                    if (view.hasPendingMeasureOrLayout) state.pendingMeasureRoots++
                    val root = view.semanticsOwner.unmergedRootSemanticsNode
                    val bounds = root.boundsInRoot
                    fun node(current: SemanticsNode, level: Int) {
                        check(++nodes <= 4_096 && level <= 64) { "Public evidence semantics bound exceeded" }
                        val config = current.config
                        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty()
                        val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                        check(texts.none { "SyntheticSeat" in it.text } && descriptions.none { "SyntheticSeat" in it }) {
                            "Public recovery evidence encountered private fixture UI"
                        }
                        val currentBounds = current.boundsInRoot
                        val shown = currentBounds.width > 0f && currentBounds.height > 0f &&
                            currentBounds.overlaps(bounds)
                        val heading = config.contains(SemanticsProperties.Heading)
                        // A Text node has one annotated value. Joining would
                        // invent a label and risk exporting arbitrary content.
                        state.observe(labels, texts.singleOrNull()?.text, descriptions, heading, shown)
                        current.children.forEach { node(it, level + 1) }
                    }
                    node(root, 0)
                }
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) visit(view.getChildAt(index), depth + 1)
                }
            }
            visit(activity.window.decorView, 0)
        }
        return state
    }

    private fun language(tag: String): String = if (tag == "en" || tag == "ar") tag else "other"
}

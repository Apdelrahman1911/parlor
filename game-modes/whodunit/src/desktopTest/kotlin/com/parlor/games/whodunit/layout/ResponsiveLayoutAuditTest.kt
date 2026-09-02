package com.parlor.games.whodunit.layout

import assertk.assertThat
import assertk.assertions.isEmpty
import java.io.File
import kotlin.test.Test

/**
 * Layout-rule guard: every Compose screen rooted in `HeroBackdrop { Column { ... } }`
 * across the user-facing source sets must either be inside a scrollable container
 * (`verticalScroll`, `LazyColumn`, `LazyVerticalGrid`) or be marked
 * `// noscroll` if it intentionally fills the viewport without overflow.
 *
 * Mobile (360 × 640 dp) is a release gate per the Phase 8 brief. A screen that
 * lists three sections stacked vertically with no scroll *will* clip on a small
 * phone — and that has happened before. This test scans the source tree on every
 * desktopTest run so the rule stays load-bearing.
 *
 * Scope: scans the production composeApp + game-modes source roots that hold
 * screens. Test files, design-system primitives, and overlay/dialog composables
 * (which are intentionally fullscreen modals) are excluded.
 */
class ResponsiveLayoutAuditTest {

    /**
     * Directories scanned, expressed relative to the project root. The
     * working dir at test runtime is `game-modes/whodunit/`, so we walk
     * up two levels to `D:/game/`.
     */
    private val scanRoots: List<String> = listOf(
        "composeApp/src/commonMain/kotlin",
        "game-modes/whodunit/src/commonMain/kotlin",
    )

    /** Files exempt from the scroll rule — overlays/modals/full-screen waiting. */
    private val exemptions: Set<String> = setOf(
        // Modal dialogs render in a centered card with a darkened scrim — they
        // are sized for their content and never expected to scroll.
        "EndGameDialog.kt",
        "PauseOverlay.kt",
        "PrivacyConcernDialog.kt",
        "PrivacyConcernOverlay.kt",
        // Pure cover screens / brief states — content is one line + a tap target.
        "CharacterRevealScreens.kt",   // waiting/hide/cover screens; dossier card scrolls internally
        // Backdrops and full-screen game segments handled inside the segment.
        "WhodunitGameFlow.kt",
    )

    @Test
    fun every_screen_either_scrolls_or_is_explicitly_exempt() {
        val projectRoot = findProjectRoot()
        val violations = mutableListOf<String>()
        for (rootRel in scanRoots) {
            val dir = File(projectRoot, rootRel)
            if (!dir.isDirectory) {
                violations += "[scan-config] root missing: $rootRel"
                continue
            }
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.name in exemptions }
                .forEach { file ->
                    val source = file.readText()
                    if (!source.contains("HeroBackdrop(")) return@forEach
                    val hasScroll = source.contains("verticalScroll(") ||
                        source.contains("LazyColumn") ||
                        source.contains("LazyVerticalGrid")
                    val hasFillButCentered = source.contains("Arrangement.Center") &&
                        source.contains("contentAlignment = Alignment.Center")
                    if (!hasScroll && !hasFillButCentered) {
                        val rel = file.absolutePath.removePrefix(projectRoot.absolutePath)
                            .trimStart('\\', '/')
                        violations += "$rel: " +
                            "HeroBackdrop screen has neither a scroll container nor an explicit centered single-shot layout"
                    }
                }
        }
        assertThat(violations).isEmpty()
    }

    private fun findProjectRoot(): File {
        // Tests run with cwd = the test module's project dir. Walk up until we
        // see the top-level settings.gradle.kts.
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate project root from ${System.getProperty("user.dir")}")
    }
}

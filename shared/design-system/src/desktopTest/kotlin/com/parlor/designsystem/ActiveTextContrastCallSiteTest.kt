package com.parlor.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

/**
 * Keeps the palette contrast contract tied to the components that render the
 * active text pairs. [com.parlor.designsystem.tokens.PaletteCoverageTest]
 * verifies the ratios; these checks prevent a component from silently
 * switching to an unreviewed foreground or surface token.
 */
class ActiveTextContrastCallSiteTest {
    private val root: File by lazy(::findProjectRoot)

    @Test
    fun button_banner_tab_and_toast_call_sites_use_reviewed_token_pairs() {
        val button = read(
            "src/commonMain/kotlin/com/parlor/designsystem/components/ParlorButton.kt",
        )
        assertContains(button, "background = colors.accentEmber")
        assertContains(button, "background = colors.semanticDanger")
        assertContains(button, "foreground = colors.textOnAccent")
        assertContains(button, "colors.accentEmberGlow.copy(alpha = 0.35f)")

        val offlineBanner = read(
            "src/commonMain/kotlin/com/parlor/designsystem/components/OfflineBanner.kt",
        )
        assertContains(offlineBanner, ".background(colors.accentBrass)")
        assertContains(offlineBanner, "color = colors.textOnAccent")

        val tabs = read(
            "src/commonMain/kotlin/com/parlor/designsystem/components/ParlorBottomTabBar.kt",
        )
        assertContains(tabs, ".background(colors.surfaceCanvas)")
        assertContains(tabs, "else colors.textTertiary")

        val toasts = read(
            "src/commonMain/kotlin/com/parlor/designsystem/components/ParlorToastHost.kt",
        )
        assertContains(toasts, ".background(ParlorTheme.colors.surfaceElevated)")
        assertContains(toasts, "ParlorToastSeverity.Info -> ParlorTheme.colors.accentBrass")
        assertContains(toasts, "ParlorToastSeverity.Success -> ParlorTheme.colors.semanticSuccess")
        assertContains(toasts, "ParlorToastSeverity.Warning -> ParlorTheme.colors.accentEmber")
        assertContains(toasts, "ParlorToastSeverity.Danger -> ParlorTheme.colors.semanticDanger")
        assertContains(toasts, "color = accent")
    }

    @Test
    fun material_color_scheme_uses_the_same_reviewed_on_accent_token() {
        val theme = read(
            "src/commonMain/kotlin/com/parlor/designsystem/theme/ParlorTheme.kt",
        )
        assertContains(theme, "onPrimary = colors.textOnAccent")
        assertContains(theme, "onSecondary = colors.textOnAccent")
        assertContains(theme, "onError = colors.textOnAccent")
    }

    private fun read(path: String): String = File(root, "shared/design-system/$path").readText()

    private fun findProjectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }
}

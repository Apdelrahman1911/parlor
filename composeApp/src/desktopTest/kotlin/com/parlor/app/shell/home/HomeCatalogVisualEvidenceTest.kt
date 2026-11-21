package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.app.shell.fullTextLayout
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Render the shipping composables, not screenshot-only replicas or production-private state. */
@OptIn(ExperimentalTestApi::class)
class HomeCatalogVisualEvidenceTest {
    @Test
    fun phone_screens_render_in_both_languages_and_themes_at_normal_and_enlarged_text() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (fontScale in listOf(1f, 2f)) {
                    renderScreen(language, theme, fontScale, catalog = true)
                    renderScreen(language, theme, fontScale, catalog = false)
                }
                renderScreen(language, theme, 2f, catalog = true, width = 320)
            }
        }
    }

    @Test
    fun six_game_setup_pickers_expose_supported_actions_at_compact_large_text() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (gameIndex in 0..5) {
                    renderScreen(language, theme, 2f, catalog = false, width = 320, setupGameIndex = gameIndex)
                }
            }
        }
    }

    private fun renderScreen(
        language: AppLanguage, theme: ThemeMode, fontScale: Float, catalog: Boolean, width: Int = 390,
        setupGameIndex: Int? = null,
    ) = runComposeUiTest {
        val games = listOf(
            WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
            MafiaGameShellBinding(MafiaDefinition(Json.Default)),
            LastLightGameShellBinding(LastLightDefinition()),
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        val setupGame = games[setupGameIndex ?: 0]
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        Box(Modifier.size(width.dp, 740.dp).testTag("shell-preview")) {
                            if (catalog) {
                                HomeScreen(games = games, onGameSelected = {})
                            } else {
                                PlayModePickerScreen(
                                    onModeSelected = {}, onHost = {}, onJoin = {}, onBack = {},
                                    capabilities = setupGame.capabilities,
                                    supportedPlayerCounts = setupGame.multiplayerContract
                                        ?.supportedPlayerCounts ?: setupGame.definition.supportedPlayerCounts,
                                )
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("shell-preview").assertIsDisplayed()
        if (catalog && fontScale == 2f) {
            assertFirstGameChoicesVisible(language)
        }
        if (setupGameIndex != null) {
            assertSetupActions(language, supportsPassAndPlay = setupGameIndex < 3)
        }
        // Public fixtures only. Stay below the existing **/build/reports/tests/
        // artifact glob, but outside desktopTest so Gradle's HTML writer cannot
        // replace the images. The environment override reaches the test worker.
        val directory = System.getProperty("parlor.ux.screenshotDir")
            ?: System.getenv("PARLOR_UI_SCREENSHOT_DIR")
            ?: "build/reports/tests/public-ux"
        if (!directory.isNullOrBlank()) {
            val screen = when {
                catalog -> "home"
                setupGameIndex != null -> "setup-${setupGame.definition.id.raw}"
                else -> "setup"
            }
            val sizeSuffix = if (width == 390) "" else "-${width}dp"
            val output = File(directory, "$screen-${language.tag}-${theme.tag}-$fontScale$sizeSuffix.png")
            check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
            Image.makeFromBitmap(onNodeWithTag("shell-preview").captureToImage().asSkiaBitmap()).use { image ->
                checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
            }
        }
    }

    private fun ComposeUiTest.assertSetupActions(language: AppLanguage, supportsPassAndPlay: Boolean) {
        val arabic = language == AppLanguage.Arabic
        val host = if (arabic) "افتح غرفة وأظهر الرمز لأصدقائك للانضمام."
            else "Open a room and show a code your friends can join."
        val join = if (arabic) "أدخل رمز المضيف للدخول إلى طاولته."
            else "Enter the host's room code to join their table."
        val local = if (arabic) "ابدأ جلسة تمرير ولعب على هذا الجهاز."
            else "Start a pass-and-play session on this device."
        onNodeWithContentDescription(host).performScrollTo().assertIsDisplayed().assertIsEnabled()
        onNodeWithContentDescription(join).performScrollTo().assertIsDisplayed().assertIsEnabled()
        if (supportsPassAndPlay) {
            onNodeWithContentDescription(local).performScrollTo().assertIsDisplayed().assertIsEnabled()
        } else {
            onNodeWithContentDescription(local).assertDoesNotExist()
        }
        onNodeWithContentDescription(host).performScrollTo()
    }

    private fun ComposeUiTest.assertFirstGameChoicesVisible(language: AppLanguage) {
        val arabic = language == AppLanguage.Arabic
        val description = if (arabic) "افتح من القاتل واختر طريقة اللعب."
            else "Open Whodunit and pick how you want to play."
        onNodeWithContentDescription(description).assertIsDisplayed()
        val viewport = onNodeWithTag("shell-preview").getUnclippedBoundsInRoot()
        val labels = if (arabic) listOf("6 لاعبين", "محلي + شبكة Wi-Fi نفسها", "افتح من القاتل")
            else listOf("6 players", "Local + same Wi-Fi", "Open Whodunit")
        for (label in labels) {
            val node = onNode(hasText(label) and hasAnyAncestor(hasContentDescription(description)), useUnmergedTree = true)
            node.assertIsDisplayed().fullTextLayout()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom &&
                bounds.left >= viewport.left && bounds.right <= viewport.right,
                "Actual game requirements/action must be fully visible without scrolling: $label at $bounds in $viewport")
        }
    }
}

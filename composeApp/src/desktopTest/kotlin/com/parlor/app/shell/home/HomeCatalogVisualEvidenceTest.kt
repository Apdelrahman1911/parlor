package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
            }
        }
    }

    private fun renderScreen(language: AppLanguage, theme: ThemeMode, fontScale: Float, catalog: Boolean) = runComposeUiTest {
        val games = listOf(
            WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
            MafiaGameShellBinding(MafiaDefinition(Json.Default)),
            LastLightGameShellBinding(LastLightDefinition()),
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        Box(Modifier.size(390.dp, 740.dp).testTag("shell-preview")) {
                            if (catalog) {
                                HomeScreen(games = games, onGameSelected = {})
                            } else {
                                PlayModePickerScreen(
                                    onModeSelected = {}, onHost = {}, onJoin = {}, onBack = {},
                                    capabilities = games.first().capabilities,
                                    supportedPlayerCounts = games.first().definition.supportedPlayerCounts,
                                )
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("shell-preview").assertIsDisplayed()
        // Public fixtures only. Default reports are collected by the existing CI artifact
        // glob; the environment override is inherited by Gradle's test worker.
        val directory = System.getProperty("parlor.ux.screenshotDir")
            ?: System.getenv("PARLOR_UI_SCREENSHOT_DIR")
            ?: "build/reports/ux"
        if (!directory.isNullOrBlank()) {
            val screen = if (catalog) "home" else "setup"
            val output = File(directory, "$screen-${language.tag}-${theme.tag}-$fontScale.png")
            check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
            Image.makeFromBitmap(onNodeWithTag("shell-preview").captureToImage().asSkiaBitmap()).use { image ->
                checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
            }
        }
    }
}

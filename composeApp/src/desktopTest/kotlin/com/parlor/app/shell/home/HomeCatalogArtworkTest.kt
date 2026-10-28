package com.parlor.app.shell.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.designsystem.icons.ParlorArtwork
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeCatalogArtworkTest {
    @Test
    fun shipping_bindings_supply_six_distinct_language_independent_motifs() {
        val bindings = listOf(
            WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
            MafiaGameShellBinding(MafiaDefinition(Json.Default)),
            LastLightGameShellBinding(LastLightDefinition()),
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        val expected = listOf(ParlorArtwork.CaseFile, ParlorArtwork.Moon, ParlorArtwork.Cards,
            ParlorArtwork.Tiles, ParlorArtwork.Wink, ParlorArtwork.Conversation)
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            runComposeUiTest {
                var actual = emptyList<ParlorArtwork>()
                setContent {
                    ProvideAppLanguage(language) {
                        actual = bindings.map { it.catalogPresentation().artwork }
                    }
                }
                runOnIdle { assertEquals(expected, actual) }
            }
        }
    }

    @Test
    fun actual_home_card_consumes_the_binding_motif_without_inference_from_color_or_game_id() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                checkHomeConsumption(language, theme)
            }
        }
    }

    private fun checkHomeConsumption(language: AppLanguage, theme: ThemeMode) = runComposeUiTest {
        var artwork by mutableStateOf(ParlorArtwork.Tiles)
        var label = ""
        val original = DominoGameShellBinding(DominoDefinition())
        val binding = object : GameShellBinding by original {
            @Composable
            override fun catalogPresentation() = original.catalogPresentation().copy(artwork = artwork)
        }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(language) {
                    label = binding.catalogPresentation().openContentDescription
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        HomeScreen(games = listOf(binding), onGameSelected = {})
                    }
                }
            }
        }
        waitForIdle()
        val card = onNodeWithContentDescription(label)
        assertEquals(1, onAllNodesWithContentDescription(label).fetchSemanticsNodes().size)
        val semantics = card.fetchSemanticsNode().config
        assertEquals(Role.Button, semantics[SemanticsProperties.Role])
        assertTrue(semantics.contains(SemanticsActions.OnClick))
        val before = card.captureToImage()
        val first = IntArray(before.width * before.height).also { before.readPixels(it) }
        runOnIdle { artwork = ParlorArtwork.Wink }
        val after = card.captureToImage()
        val second = IntArray(after.width * after.height).also { after.readPixels(it) }
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        assertFalse(first.contentEquals(second), "Changing only the binding's artwork must change the actual card")
    }
}

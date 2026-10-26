package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.mafia.MafiaDefinition
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeCatalogRenderingTest {
    @Test
    fun one_device_filter_excludes_multiplayer_only_games_and_routes_registered_selection() {
        verifyCatalog(AppLanguage.English, 1f)
        verifyCatalog(AppLanguage.Arabic, 2f)
    }

    @Test
    fun empty_local_filter_offers_a_route_back_to_the_catalog_without_starting_a_game() = runComposeUiTest {
        var starts = 0
        setContent {
            ProvideAppLanguage(AppLanguage.English) {
                ParlorTheme(reducedMotion = true) {
                    HomeScreen(
                        games = listOf(DominoGameShellBinding(DominoDefinition())),
                        onGameSelected = { starts++ },
                    )
                }
            }
        }
        onNodeWithText("One device").performScrollTo().performClick().assertIsSelected()
        onNodeWithText("No games for this setup").performScrollTo().assertExists()
        onNodeWithText("All games").performScrollTo().performClick().assertIsSelected()
        onNodeWithText("No games for this setup").assertDoesNotExist()
        onNodeWithText("1 game").assertExists()
        runOnIdle { assertEquals(0, starts) }
    }

    private fun verifyCatalog(language: AppLanguage, fontScale: Float) = runComposeUiTest {
        val local = MafiaGameShellBinding(MafiaDefinition(Json.Default))
        val nearby = DominoGameShellBinding(DominoDefinition())
        val selections = mutableListOf<GameId>()
        val arabic = language == AppLanguage.Arabic
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(390.dp, 740.dp)) {
                            HomeScreen(games = listOf(local, nearby), onGameSelected = selections::add)
                        }
                    }
                }
            }
        }
        onNodeWithText(if (arabic) "جهاز واحد" else "One device")
            .performScrollTo().performClick().assertIsSelected()
        runOnIdle { assertEquals(emptyList(), selections) }
        onNodeWithText(if (arabic) "لعبة واحدة (1)" else "1 game").assertExists()
        onNodeWithText(if (arabic) "نفس الواي فاي · جهاز لكل لاعب" else "Same Wi-Fi · own devices")
            .assertDoesNotExist()
        onNodeWithContentDescription(if (arabic) "افتح لعبة حرامية وظباط." else "Open the Mafia game.")
            .performScrollTo().assertIsDisplayed().performClick()
        runOnIdle { assertEquals(listOf(local.definition.id), selections) }
        onNodeWithText(if (arabic) "اللعب مع القريبين" else "Nearby play")
            .performScrollTo().performClick().assertIsSelected()
        onNodeWithText(if (arabic) "لعبتان (2)" else "2 games").assertExists()
        runOnIdle { assertEquals(listOf(local.definition.id), selections) }
        onNodeWithText(if (arabic) "كل الألعاب" else "All games")
            .performScrollTo().performClick().assertIsSelected()
        onNodeWithText(if (arabic) "لعبتان (2)" else "2 games").assertExists()
        val nearbyLabel = if (arabic) "نفس الواي فاي · جهاز لكل لاعب" else "Same Wi-Fi · own devices"
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText(nearbyLabel))
        onNodeWithText(nearbyLabel).assertExists()
    }
}

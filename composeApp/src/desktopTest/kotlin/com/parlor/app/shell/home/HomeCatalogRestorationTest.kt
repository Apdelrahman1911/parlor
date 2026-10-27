package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/** Public catalog only; reproduce Nav3's saved Home entry and asynchronous recovery refresh. */
@OptIn(ExperimentalTestApi::class)
class HomeCatalogRestorationTest {
    @Test
    fun returning_while_recovery_loads_preserves_the_selected_catalog_card_position() {
        verifyRestoration(AppLanguage.English, 1f)
    }

    @Test
    fun arabic_enlarged_text_keeps_the_catalog_anchor_across_repeated_return_and_refresh() {
        verifyRestoration(AppLanguage.Arabic, 2f)
    }

    private fun verifyRestoration(language: AppLanguage, fontScale: Float) = runComposeUiTest {
        val visible = mutableStateOf(true)
        val loading = mutableStateOf(false)
        val selections = mutableListOf<GameId>()
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
                    ParlorTheme(reducedMotion = true) {
                        val entries = rememberSaveableStateHolder()
                        val catalogScrollState = rememberLazyListState()
                        Box(Modifier.size(402.dp, 740.dp)) {
                            if (visible.value) {
                                entries.SaveableStateProvider("home") {
                                    HomeScreen(
                                        games = games,
                                        scrollState = catalogScrollState,
                                        recoveryLoading = loading.value,
                                        onGameSelected = { selections += it; visible.value = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val prefix = if (language == AppLanguage.Arabic) "افتح غمزة" else "Open Ghamza"
        onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(prefix, substring = true))
        val card = onNodeWithContentDescription(prefix, substring = true)
        repeat(2) { cycle ->
            card.assertIsDisplayed()
            val before = card.getUnclippedBoundsInRoot()
            card.performClick()
            card.assertDoesNotExist()
            runOnIdle {
                assertEquals(List(cycle + 1) { games[4].definition.id }, selections)
                loading.value = true
                visible.value = true
            }
            waitForIdle()
            runOnIdle { loading.value = false }
            waitForIdle()
            val after = card.getUnclippedBoundsInRoot()
            println("Public catalog restoration: language=${language.tag}, cycle=$cycle, before=$before, after=$after")
            assertEquals(before, after, "Recovery refresh must not restore an earlier catalog index")
            card.assertIsDisplayed()
        }
    }
}

package com.parlor.app.shell.home

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Actual public pointer input, not semantic performClick or native XCTest evidence. */
@OptIn(ExperimentalTestApi::class)
class HomeCatalogPartialPointerTest {
    @Test
    fun english_partially_visible_catalog_card_accepts_one_pointer_action() {
        verifyPartialPointer(AppLanguage.English)
    }

    @Test
    fun arabic_partially_visible_catalog_card_accepts_one_pointer_action() {
        verifyPartialPointer(AppLanguage.Arabic)
    }

    private fun verifyPartialPointer(language: AppLanguage) = runComposeUiTest {
        val games = listOf(
            WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
            MafiaGameShellBinding(MafiaDefinition(Json.Default)),
            LastLightGameShellBinding(LastLightDefinition()),
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        val selected = mutableListOf<GameId>()
        var scrollState: HomeCatalogScrollState? = null
        var scope: CoroutineScope? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        val state = rememberHomeCatalogScrollState()
                        scrollState = state
                        scope = rememberCoroutineScope()
                        Box(Modifier.size(402.dp, 740.dp).clipToBounds().testTag(VIEWPORT)) {
                            HomeScreen(games = games, catalogScrollState = state, onGameSelected = selected::add)
                        }
                    }
                }
            }
        }
        val prefix = if (language == AppLanguage.Arabic) "افتح الدومنة المصرية" else "Open Egyptian Dominoes"
        onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(prefix, substring = true))
        val card = onNodeWithContentDescription(prefix, substring = true)
        val viewport = onNodeWithTag(VIEWPORT)
        val viewportBounds = viewport.getUnclippedBoundsInRoot()
        val cardBounds = card.getUnclippedBoundsInRoot()
        val desiredTop = viewportBounds.bottom - VISIBLE_FRAGMENT
        runOnIdle {
            assertNotNull(scope).launch {
                assertNotNull(scrollState).listState.scrollBy((cardBounds.top - desiredTop).value)
            }
        }
        waitForIdle()
        val clippedCard = card.getUnclippedBoundsInRoot()
        card.assertIsDisplayed()
        assertEquals(VISIBLE_FRAGMENT, viewportBounds.bottom - clippedCard.top)
        assertTrue(clippedCard.bottom > viewportBounds.bottom, "The card must really be clipped")
        val point = Offset(
            ((clippedCard.left + clippedCard.right) / 2 - viewportBounds.left).value,
            (viewportBounds.bottom - VISIBLE_FRAGMENT / 2 - viewportBounds.top).value,
        )
        viewport.performTouchInput { click(point) }
        runOnIdle { assertEquals(listOf(games[3].definition.id), selected) }
    }

    private companion object {
        const val VIEWPORT = "public-catalog-pointer-viewport"
        val VISIBLE_FRAGMENT = 58.dp
    }
}

package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GameShellBinding
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Actual shared pointer dispatch, never SemanticsActions.OnClick or native evidence. */
@OptIn(ExperimentalTestApi::class)
class HomeCatalogPointerTest {
    @Test
    fun a_single_fifty_millisecond_press_dispatches_each_registered_game_once() = verifyMatrix(CatalogGesture.Press)

    @Test
    fun cancelled_catalog_press_does_not_dispatch_a_game() = verifyMatrix(CatalogGesture.Cancel)

    @Test
    fun dragging_a_catalog_card_scrolls_without_dispatching_a_game() = verifyMatrix(CatalogGesture.Scroll)

    @Test
    fun the_first_press_after_home_remount_dispatches_the_preserved_card_once() =
        verifyMatrix(CatalogGesture.Press, remount = true)

    @Test
    fun the_last_card_opens_with_one_press_after_slow_swipes_finish() {
        for (language in AppLanguage.entries) {
            for (fontScale in listOf(1f, 2f)) verifyInput(5, language, fontScale, CatalogGesture.Press, swipe = true)
        }
    }

    private fun verifyMatrix(gesture: CatalogGesture, remount: Boolean = false) {
        for (index in 0..5) {
            for (language in AppLanguage.entries) {
                for (fontScale in listOf(1f, 2f)) verifyInput(index, language, fontScale, gesture, remount)
            }
        }
    }

    private fun verifyInput(
        index: Int,
        language: AppLanguage,
        fontScale: Float,
        gesture: CatalogGesture,
        remount: Boolean = false,
        swipe: Boolean = false,
    ) = runComposeUiTest {
        val games = catalogBindings()
        val visible = mutableStateOf(true)
        val selections = mutableListOf<GameId>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        val entries = rememberSaveableStateHolder()
                        val state = rememberHomeCatalogScrollState()
                        Box(Modifier.size(402.dp, 740.dp)) {
                            if (visible.value) {
                                entries.SaveableStateProvider("home") {
                                    HomeScreen(
                                        games = games,
                                        catalogScrollState = state,
                                        onGameSelected = { selections += it; visible.value = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val label = catalogLabel(index, language)
        val list = onNode(hasScrollToIndexAction())
        val card = onNodeWithContentDescription(label, substring = true)
        if (swipe) {
            // Normal shared swipe input and framework idle, not scroll-to semantics
            // or an artificial callback. Native XCTest readiness is a separate gate.
            repeat(12) { list.performTouchInput { swipeUp(durationMillis = 600) }; waitForIdle() }
        } else {
            list.performScrollToNode(hasContentDescription(label, substring = true))
        }
        card.assertIsDisplayed()
        if (remount) {
            runOnIdle { visible.value = false }
            card.assertDoesNotExist()
            runOnIdle { visible.value = true }
            card.assertIsDisplayed()
        }
        card.performTouchInput {
            down(center)
            advanceEventTime(50)
            when (gesture) {
                CatalogGesture.Press -> up()
                CatalogGesture.Cancel -> cancel()
                CatalogGesture.Scroll -> {
                    // At the last card, move back into scrollable content.
                    moveTo(center + Offset(0f, if (index == 5) 120f else -120f))
                    advanceEventTime(50)
                    up()
                }
            }
        }
        runOnIdle {
            assertEquals(if (gesture == CatalogGesture.Press) listOf(games[index].definition.id) else emptyList(), selections)
            assertEquals(gesture != CatalogGesture.Press, visible.value)
        }
        println("Public catalog pointer: game=${games[index].definition.id.raw}, locale=${language.tag}, scale=$fontScale, gesture=$gesture, remount=$remount, swipe=$swipe")
    }
}

private enum class CatalogGesture { Press, Cancel, Scroll }

private fun catalogBindings(): List<GameShellBinding> = listOf(
    WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
    MafiaGameShellBinding(MafiaDefinition(Json.Default)),
    LastLightGameShellBinding(LastLightDefinition()),
    DominoGameShellBinding(DominoDefinition()),
    GhamzaGameShellBinding(GhamzaDefinition()),
    WordImpostorGameShellBinding(WordImpostorDefinition()),
)

private fun catalogLabel(index: Int, language: AppLanguage): String = if (language == AppLanguage.Arabic) {
    listOf("افتح من القاتل", "افتح لعبة حرامية وظباط", "افتح الضوء الأخير", "افتح الدومنة المصرية", "افتح غمزة", "افتح لعبة الإمبوستر")[index]
} else {
    listOf("Open Whodunit", "Open the Mafia game", "Open Last Light", "Open Egyptian Dominoes", "Open Ghamza", "Open Word Impostor")[index]
}

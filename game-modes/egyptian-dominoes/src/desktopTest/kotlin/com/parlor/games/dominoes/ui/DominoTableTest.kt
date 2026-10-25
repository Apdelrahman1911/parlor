package com.parlor.games.dominoes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DominoTableTest {
    @Test
    fun completed_matches_and_rematches_are_reachable_in_both_languages_at_large_text() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) verifyResultsJourney(language)
    }

    private fun verifyResultsJourney(language: AppLanguage) = runComposeUiTest {
        val reducer = DominoReducer()
        val initial = reducer.initial(uiPlayers(4), DominoSettings(target = 51), 18)
        val completed = completeMatch(reducer, initial)
        var state by mutableStateOf(initial)
        val self = initial.players.first().id
        val arabic = language == AppLanguage.Arabic
        setContent {
            GameUiFrame(language, 320, 740, 2f) {
                DominoTable(DominoProjection.toPlayer(state, self).state, self, true, true, 0,
                    { state = reducer.apply(state, it) }, {})
            }
        }
        capturePublicGameFrame("table-covered-${language.tag}-2.0")
        // Deliberately leave the old screen scrolled far below the header.
        onNodeWithText(if (arabic) "ورّيني قطعي" else "Show my tiles").reachable()
        runOnIdle { state = completed }
        onNodeWithText(if (arabic) "أبطال الترابيزة" else "Table champions").assertIsDisplayed()
        onNodeWithText(if (arabic) "أبطال الترابيزة" else "Table champions", useUnmergedTree = true)
            .assertWordOnSingleLine(if (arabic) "الترابيزة" else "champions")
        state.players.forEach { player ->
            val names = onAllNodesWithText(state.name(player.id), useUnmergedTree = true)
            assertTrue(names.fetchSemanticsNodes().isNotEmpty())
            names.fetchSemanticsNodes().indices.forEach { index -> names[index].assertWordOnSingleLine("Player") }
        }
        capturePublicGameFrame("results-${language.tag}-2.0")
        val rematch = if (arabic) "العبوا تاني · صفّر النقط" else "Play again · reset scores"
        onNodeWithText(rematch).reachable().assertIsEnabled()
        onNodeWithText(rematch, useUnmergedTree = true).assertFullText()
        onNodeWithText(rematch).performClick()
        assertEquals(DominoPhase.Playing, state.phase)
        assertEquals(completed.public.token + 1, state.public.token)
        assertTrue(state.public.scores.values.all { it == 0 })
        onAllNodes(hasContentDescription("${if (arabic) "قطعة" else "Tile"} ", substring = true), true).assertCountEquals(0)
        capturePublicGameFrame("rematch-covered-${language.tag}-2.0")
    }

    private fun completeMatch(reducer: DominoReducer, initial: DominoState): DominoState {
        var state = initial
        repeat(DominoRules.MAX_HANDS * DominoRules.MAX_MOVE) {
            if (state.phase == DominoPhase.MatchResult) return state
            val action = if (state.phase == DominoPhase.RoundResult) DominoAction.NextRound(state.public.token) else {
                val by = checkNotNull(state.public.turn)
                val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull { DominoRules.playableEnds(state, by, it).isNotEmpty() }
                when {
                    tile != null -> DominoAction.Place(by, state.public.token, state.public.move, tile.id,
                        DominoRules.playableEnds(state, by, tile).first())
                    DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
                    else -> DominoAction.Pass(by, state.public.token, state.public.move)
                }
            }
            state = reducer.apply(state, action)
        }
        error("Deterministic match did not terminate within its rules bound")
    }

    @Test
    fun light_theme_rules_start_with_a_compact_guide_and_expand_details_on_request() = runComposeUiTest {
        val state = DominoReducer().initial(uiPlayers(3), DominoSettings(), 42)
        val self = state.players.first().id
        setContent {
            GameUiFrame(width = 320, fontScale = 2f, themeMode = ThemeMode.Light) {
                DominoTable(DominoProjection.toPlayer(state, self).state, self, false, true, 0, {}, {})
            }
        }
        onNodeWithText("Rules").reachable().performClick()
        onNodeWithText("Start in three steps").assertExists()
        onNodeWithText("Start in three steps", useUnmergedTree = true).assertFullText()
        onNodeWithText("Double-six tiles.", substring = true).assertDoesNotExist()
        onNodeWithText("Full rules").reachable().performClick()
        onNodeWithText("Double-six tiles.", substring = true).assertExists()
        onNodeWithText("Hide details").reachable().performClick()
        onNodeWithText("Double-six tiles.", substring = true).assertDoesNotExist()
    }

    @Test
    fun tiles_can_be_hidden_while_a_host_acknowledgement_is_pending() = runComposeUiTest {
        val state = DominoReducer().initial(uiPlayers(3), DominoSettings(), 42)
        val self = checkNotNull(state.public.turn)
        var enabled by mutableStateOf(true)
        var sent: DominoAction? = null
        setContent {
            GameUiFrame { DominoTable(DominoProjection.toPlayer(state, self).state, self, true, enabled, 0, { sent = it }, {}) }
        }
        onNodeWithText("Show my tiles").reachable().performClick()
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(9)
        runOnIdle { enabled = false }
        onNodeWithText("Hide tiles").reachable().assertIsEnabled().performClick()
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(0)
        assertNull(sent)
    }

    @Test
    fun covered_tiles_have_no_private_semantics_and_epoch_change_clears_the_selection() = runComposeUiTest {
        val canonical = DominoReducer().initial(uiPlayers(3), DominoSettings(), 42)
        val self = checkNotNull(canonical.public.turn)
        val own = DominoProjection.toPlayer(canonical, self).state
        val opening = checkNotNull(own.privatePerPlayer.getValue(self).requiredOpening)
        var epoch by mutableStateOf(0L)
        var enabled by mutableStateOf(false)
        var sent: DominoAction? = null
        val back = InPlaceBackOwner()
        setContent {
            GameUiFrame(backOwner = back) {
                DominoTable(own, self, true, enabled, epoch, { sent = it }, {}, Modifier)
            }
        }
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(0)
        onNodeWithText("Show my tiles").reachable().assertIsNotEnabled().performClick()
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(0)
        runOnIdle { enabled = true }
        onNodeWithText("Show my tiles").reachable().performClick()
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(9)
        onNodeWithContentDescription("Tile ${opening.low} – ${opening.high}").performScrollTo().performClick()
        assertNull(sent) // Selection is local; it does not optimistically mutate authority.
        runOnIdle { assertTrue(back.handleBack()) }
        onNodeWithText("Place opening tile").assertDoesNotExist()
        onNodeWithContentDescription("Tile ${opening.low} – ${opening.high}").performScrollTo().performClick()
        onNodeWithText("Place opening tile").reachable().performClick()
        assertEquals(DominoAction.Place(self, own.public.token, own.public.move, opening.id,
            com.parlor.games.dominoes.domain.DominoEnd.Right), sent)
        runOnIdle { epoch++ }
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(0)
        onNodeWithText("Place opening tile").assertDoesNotExist()
        onNodeWithText("Show my tiles").reachable().assertIsEnabled()
    }

    @Test
    fun english_compact_table_controls_are_reachable_at_large_text() = verifyLayout(AppLanguage.English, 320, 740, 2f)

    @Test
    fun arabic_compact_table_controls_are_reachable_at_large_text() = verifyLayout(AppLanguage.Arabic, 320, 740, 2f)

    @Test
    fun english_tablet_and_landscape_keep_physical_tiles_selectable() {
        verifyLayout(AppLanguage.English, 1024, 768, 1f)
        verifyLayout(AppLanguage.English, 844, 390, 1f)
    }

    private fun verifyLayout(language: AppLanguage, width: Int, height: Int, fontScale: Float) = runComposeUiTest {
        val canonical = DominoReducer().initial(uiPlayers(4), DominoSettings(), 18)
        val self = checkNotNull(canonical.public.turn)
        val own = DominoProjection.toPlayer(canonical, self).state
        val opening = checkNotNull(own.privatePerPlayer.getValue(self).requiredOpening)
        setContent { GameUiFrame(language, width, height, fontScale) { DominoTable(own, self, true, true, 0, {}, {}) } }
        val arabic = language == AppLanguage.Arabic
        val reveal = if (arabic) "ورّيني قطعي" else "Show my tiles"
        onNodeWithText(reveal).reachable().performClick()
        own.privatePerPlayer.getValue(self).hand.forEach { piece ->
            onNodeWithContentDescription("${if (arabic) "قطعة" else "Tile"} ${piece.low} – ${piece.high}")
                .performScrollTo().assertIsDisplayed()
        }
        val tile = onNodeWithContentDescription("${if (arabic) "قطعة" else "Tile"} ${opening.low} – ${opening.high}")
        tile.performScrollTo()
        tile.assertIsDisplayed().assertIsEnabled().performClick().assertIsSelected()
        val play = if (arabic) "العب قطعة البداية" else "Place opening tile"
        onNodeWithText(play).reachable().assertIsEnabled()
        onNodeWithText(play, useUnmergedTree = true).assertFullText()
    }
}

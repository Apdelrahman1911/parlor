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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.components.InPlaceBackOwner
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
        onAllNodes(hasContentDescription("Tile ", substring = true), true).assertCountEquals(7)
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

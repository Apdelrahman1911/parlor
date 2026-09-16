package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GameplayPrivacyTest {
    @Test
    fun entering_and_hiding_exclude_private_card_semantics_and_clear_selection() = runComposeUiTest {
        setContent { LastLightTestFrame { TestTable() } }

        onNodeWithTag("game-card-0").assertDoesNotExist()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        onNodeWithTag("game-play").assertIsEnabled()

        onNodeWithTag("game-hide-hand").bringIntoView().performClick()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-play").assertIsNotEnabled()
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").assertIsOff()
    }

    @Test
    fun epoch_change_conceals_even_when_a_brief_background_state_was_not_observed() = runComposeUiTest {
        var epoch by mutableStateOf(0L)
        var visible by mutableStateOf(true)
        setContent {
            LastLightTestFrame { TestTable(privacyEpoch = epoch, privateContentVisible = visible) }
        }

        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        runOnIdle { epoch++ }
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-reveal-hand").performClick()
        onNodeWithTag("game-card-0").assertIsOff()

        runOnIdle { visible = false }
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsNotEnabled().performClick()
        onNodeWithTag("game-card-0").assertDoesNotExist()
        runOnIdle { visible = true }
        onNodeWithTag("game-reveal-hand").assertIsEnabled()
        onNodeWithTag("game-card-0").assertDoesNotExist()
    }

    @Test
    fun viewer_handoff_and_new_session_both_require_a_fresh_reveal() = runComposeUiTest {
        val first = playingView()
        var view by mutableStateOf(first)
        var session by mutableStateOf("first-session")
        setContent {
            LastLightTestFrame { key(session) { TestTable(view) } }
        }

        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        runOnIdle {
            view = first.copy(
                viewerId = "b",
                turnPlayerId = "b",
                yourHand = listOf(Card("different-viewer", CardRank.STAR)),
            )
        }
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
        onAllNodes(hasContentDescription("Moon. Card", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-reveal-hand").performClick()
        onNodeWithTag("game-card-0").assertIsOff()

        runOnIdle { session = "second-session" }
        onNodeWithTag("game-card-0").assertDoesNotExist()
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
    }

    @Test
    fun unknown_and_eliminated_viewers_never_compose_a_private_hand() = runComposeUiTest {
        var view by mutableStateOf(playingView().copy(viewerId = null))
        setContent { LastLightTestFrame { TestTable(view) } }

        onNodeWithTag("game-hand").assertDoesNotExist()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        runOnIdle {
            view = playingView().let { original ->
                original.copy(players = original.players.map { it.copy(eliminated = it.id == original.viewerId) })
            }
        }
        onNodeWithTag("game-hand").assertDoesNotExist()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }
}

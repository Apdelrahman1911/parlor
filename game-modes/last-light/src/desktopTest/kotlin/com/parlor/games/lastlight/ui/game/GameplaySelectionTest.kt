package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.ui.PendingAction
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameplaySelectionTest {
    @Test
    fun duplicate_ranks_select_independently_and_authoritative_limit_allows_deselection() = runComposeUiTest {
        val view = playingView().copy(availableActions = AvailableActions(canPlay = true, maxPlayableCards = 2))
        var submitted = emptyList<CardId>()
        setContent { LastLightTestFrame { TestTable(view, onPlay = { submitted = it }) } }

        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        onNodeWithTag("game-card-1").performClick().assertIsOn()
        onNodeWithTag("game-card-2").performClick().assertIsOff()
        onNodeWithText("Choose up to 2 cards.").assertExists()
        onNodeWithTag("game-card-0").performClick().assertIsOff()
        onNodeWithTag("game-card-1").assertIsOn()
        onNodeWithTag("game-card-2").performClick().assertIsOn()
        onNodeWithTag("game-play").bringIntoView().performClick()
        assertEquals(listOf(view.yourHand[1].id, view.yourHand[2].id), submitted)
    }

    @Test
    fun plays_follow_current_hand_order_and_removed_cards_cannot_stay_selected() = runComposeUiTest {
        val first = playingView()
        var view by mutableStateOf(first)
        var submitted = emptyList<CardId>()
        setContent { LastLightTestFrame { TestTable(view, onPlay = { submitted = it }) } }

        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-4").performScrollTo().performClick().assertIsOn()
        onNodeWithTag("game-card-0").performScrollTo().performClick().assertIsOn()
        onNodeWithTag("game-play").bringIntoView().performClick()
        assertEquals(listOf(first.yourHand[0].id, first.yourHand[4].id), submitted)
        onNodeWithTag("game-card-0").assertIsOn()

        runOnIdle {
            view = first.copy(yourHand = listOf(first.yourHand[4], first.yourHand[2], first.yourHand[1]))
        }
        onNodeWithTag("game-card-0").performScrollTo().assertIsOn()
        onNodeWithTag("game-card-1").assertIsOff()
        onNodeWithTag("game-play").bringIntoView().performClick()
        assertEquals(listOf(first.yourHand[4].id), submitted)

        runOnIdle { view = view.copy(yourHand = view.yourHand.drop(1)) }
        onNodeWithTag("game-card-0").assertIsOff()
        onNodeWithTag("game-play").assertIsNotEnabled()
    }

    @Test
    fun pending_actions_block_repeat_submission_and_rejection_preserves_valid_selection() = runComposeUiTest {
        var view by mutableStateOf(playingView())
        var pending by mutableStateOf<PendingAction?>(null)
        var submissions = 0
        setContent {
            LastLightTestFrame {
                TestTable(
                    view,
                    pendingAction = pending,
                    onPlay = {
                        pending = PendingAction.PLAY_CARDS
                        submissions++
                    },
                )
            }
        }

        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        onNodeWithTag("game-play").bringIntoView().performClick()
        onNodeWithTag("game-play").assertIsNotEnabled().performClick()
        assertEquals(1, submissions)
        onNodeWithTag("game-card-0").assertIsOn().assertIsNotEnabled()
        onNodeWithTag("game-card-1").assertIsOff().assertIsNotEnabled()

        // A rejected command produces no authoritative hand change and permits a deliberate retry.
        runOnIdle { pending = null }
        onNodeWithTag("game-card-0").assertIsOn().assertIsEnabled()
        onNodeWithTag("game-play").assertIsEnabled()

        runOnIdle { view = view.copy(roundNumber = 2) }
        onNodeWithTag("game-card-0").assertIsOff()
        onNodeWithTag("game-play").assertIsNotEnabled()
    }
}

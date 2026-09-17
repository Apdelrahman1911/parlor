package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.games.lastlight.ui.PendingAction
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameplayResultsTest {
    @Test
    fun forced_challenge_is_the_only_action_and_cannot_repeat_while_pending() = runComposeUiTest {
        var pending by mutableStateOf<PendingAction?>(null)
        var challenges = 0
        setContent {
            LastLightTestFrame {
                TestTable(
                    forcedChallengeView(),
                    pendingAction = pending,
                    onChallenge = {
                        pending = PendingAction.CHALLENGE
                        challenges++
                    },
                )
            }
        }
        onNodeWithTag("game-hand").assertDoesNotExist()
        onNodeWithTag("game-play").assertDoesNotExist()
        onNodeWithTag("game-challenge").bringIntoView().assertIsEnabled().performClick()
        onNodeWithTag("game-challenge").assertIsNotEnabled().performClick()
        assertEquals(1, challenges)
    }

    @Test
    fun public_wild_and_mismatch_proof_support_eliminated_host_round_controls() = runComposeUiTest {
        var permission by mutableStateOf(false)
        var host by mutableStateOf(true)
        var pending by mutableStateOf<PendingAction?>(null)
        var nextRounds = 0
        setContent {
            LastLightTestFrame {
                TestTable(
                    roundResultView(),
                    isHost = host,
                    canAdvanceRound = permission,
                    pendingAction = pending,
                    onNextRound = { nextRounds++ },
                )
            }
        }
        onNodeWithText("Bluff caught.").assertExists()
        onAllNodes(hasContentDescription("Wild · always matches", substring = true)).assertCountEquals(1)
        onAllNodes(hasContentDescription("Doesn’t match", substring = true)).assertCountEquals(1)
        onNodeWithTag("game-next-round").bringIntoView().assertIsNotEnabled()
        runOnIdle { permission = true }
        onNodeWithTag("game-next-round").assertIsEnabled().performClick()
        assertEquals(1, nextRounds)

        runOnIdle { pending = PendingAction.NEXT_ROUND }
        onNodeWithTag("game-next-round").assertIsNotEnabled()
        runOnIdle { host = false }
        onNodeWithTag("game-next-round").assertDoesNotExist()
        onNodeWithText("Waiting for the host").assertExists()
    }

    @Test
    fun winner_lobby_controls_are_host_only_and_final_proof_does_not_announce_verdict_again() = runComposeUiTest {
        var host by mutableStateOf(true)
        var permission by mutableStateOf(false)
        var returns = 0
        setContent {
            LastLightTestFrame {
                TestTable(
                    finishedView(),
                    isHost = host,
                    canReturnToLobby = permission,
                    onReturnToLobby = { returns++ },
                )
            }
        }
        onNodeWithText("Guest · seat 2 wins.").assertExists()
        onNodeWithTag("game-rematch").bringIntoView().assertIsNotEnabled()
        runOnIdle { permission = true }
        onNodeWithTag("game-rematch").assertIsEnabled().performClick()
        assertEquals(1, returns)
        onNodeWithTag("game-final-reveal").bringIntoView().performClick()
        onNodeWithText("Bluff caught.").assertExists()
        onAllNodes(hasText("Bluff caught.") and politeLiveRegion()).assertCountEquals(0)

        runOnIdle { host = false }
        onNodeWithTag("game-rematch").assertDoesNotExist()
        onNodeWithText("Waiting for the host").assertExists()
    }

    @Test
    fun previous_round_history_does_not_repeat_automatic_verdict_announcement() = runComposeUiTest {
        val previous = roundResultView().roundOutcome
        setContent {
            LastLightTestFrame {
                TestTable(playingView().copy(roundNumber = 2, roundOutcome = previous))
            }
        }
        onNodeWithText("Round 1 reveal").performScrollTo().performClick()
        onNodeWithText("Bluff caught.").assertExists()
        onAllNodes(hasText("Bluff caught.") and politeLiveRegion()).assertCountEquals(0)
    }

    @Test
    fun a_round_without_authoritative_result_cannot_be_advanced() = runComposeUiTest {
        setContent { LastLightTestFrame { TestTable(roundResultView().copy(roundOutcome = null)) } }
        onNodeWithTag("game-next-round").bringIntoView().assertIsNotEnabled()
        onNodeWithText("Waiting for the round result").assertExists()
    }

    private fun politeLiveRegion(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
}

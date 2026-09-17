package com.parlor.games.lastlight.ui.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.PublicClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LastLightFeedbackEffectTest {
    @Test
    fun acceptedCountersDriveFeedbackWhileRecompositionAndDisposalCannotReplayIt() = runComposeUiTest {
        val output = RecordingLastLightFeedback()
        var mounted by mutableStateOf(true)
        var game by mutableStateOf(initialView())
        setContent {
            CompositionLocalProvider(LocalLastLightFeedbackOutput provides output) {
                Box {
                    if (mounted) LastLightFeedbackEffect("session", game, connected = true, foreground = true)
                }
            }
        }
        runOnIdle {
            assertEquals(1, output.prepareCount)
            assertEquals(listOf(false, true), output.foregroundChanges)
            assertTrue(output.cues.isEmpty())
            game = game.copy(acceptedPlaySequence = 1, latestClaim = PublicClaim("other", 1))
        }
        runOnIdle {
            assertEquals(listOf(LastLightFeedbackCue.CARD_PLAY), output.cues)
            game = game.copy(yourHand = listOf(Card("private-card", CardRank.WILD)))
        }
        runOnIdle {
            assertEquals(listOf(LastLightFeedbackCue.CARD_PLAY), output.cues)
            game = game.copy(acceptedPlaySequence = 2)
        }
        runOnIdle {
            assertEquals(List(2) { LastLightFeedbackCue.CARD_PLAY }, output.cues)
            mounted = false
        }
        runOnIdle {
            assertEquals(1, output.closeCount)
            assertEquals(1, output.prepareCount)
            assertEquals(List(2) { LastLightFeedbackCue.CARD_PLAY }, output.cues)
        }
    }

    private fun initialView() = GameView(
        viewerId = "recipient",
        phase = GamePhase.PLAYING,
        roundNumber = 1,
        tableRank = CardRank.CROWN,
        players = emptyList(),
        yourHand = emptyList(),
        turnPlayerId = "other",
        latestClaim = null,
        forcedChallenge = false,
        availableActions = AvailableActions(),
        roundOutcome = null,
        winnerId = null,
    )
}

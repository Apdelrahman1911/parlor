package com.parlor.games.lastlight.ui.feedback

import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.PublicClaim
import com.parlor.games.lastlight.domain.model.RoundOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastLightFeedbackFrameTest {
    @Test
    fun finishedRecipientGetsWinnerCueOnlyForTheirOwnWin() {
        val winner = view(phase = GamePhase.FINISHED, winner = "winner", viewer = "winner", burnedOut = true)
        assertEquals(LastLightFeedbackCue.WIN, winner.feedbackFrame().resultCue)
        assertEquals(LastLightFeedbackCue.LIGHT_OUT, winner.copy(viewerId = "other").feedbackFrame().resultCue)
        assertEquals(LastLightFeedbackCue.LIGHT_OUT, winner.copy(viewerId = null).feedbackFrame().resultCue)
    }

    @Test
    fun sharedDeviceExplicitlyCelebratesTheTableWinner() {
        val sharedTable = view(phase = GamePhase.FINISHED, winner = "winner", viewer = null, burnedOut = true)
        assertEquals(LastLightFeedbackCue.WIN, sharedTable.feedbackFrame(celebrateAnyWinner = true).resultCue)
    }

    @Test
    fun publicBurnoutSelectsTheResultCue() {
        assertEquals(LastLightFeedbackCue.ROUND_END, view(burnedOut = false).feedbackFrame().resultCue)
        assertEquals(LastLightFeedbackCue.LIGHT_OUT, view(burnedOut = true).feedbackFrame().resultCue)
    }

    @Test
    fun previousRoundProofCannotBecomeFreshFeedback() {
        val playing = view(phase = GamePhase.PLAYING).copy(roundNumber = 2)
        assertNull(playing.feedbackFrame().resultCue)
        assertNull(playing.copy(phase = GamePhase.ROUND_ENDED).feedbackFrame().resultCue)
    }

    @Test
    fun privateHandAndActionChangesDoNotAlterTheFeedbackFrame() {
        val before = view(phase = GamePhase.PLAYING).copy(latestClaim = PublicClaim("other", 1))
        val after = before.copy(
            yourHand = listOf(Card("recipient-only-card", CardRank.WILD)),
            availableActions = AvailableActions(canPlay = true, maxPlayableCards = 3),
        )
        assertEquals(before.feedbackFrame(), after.feedbackFrame())
        assertEquals(7L, after.feedbackFrame().acceptedPlaySequence)
        assertEquals(2L, after.feedbackFrame().outcomeSequence)
    }

    private fun view(
        phase: GamePhase = GamePhase.ROUND_ENDED,
        winner: String? = null,
        viewer: String? = "recipient",
        burnedOut: Boolean = false,
    ) = GameView(
        viewerId = viewer,
        phase = phase,
        roundNumber = 1,
        tableRank = CardRank.CROWN,
        players = emptyList(),
        yourHand = emptyList(),
        turnPlayerId = null,
        latestClaim = null,
        forcedChallenge = false,
        availableActions = AvailableActions(),
        roundOutcome = RoundOutcome(
            roundNumber = 1,
            tableRank = CardRank.CROWN,
            claimantId = "claimant",
            challengerId = "challenger",
            revealedCards = listOf(Card("public-proof", CardRank.CROWN)),
            truthful = true,
            penalizedPlayerId = "challenger",
            penaltyAttempt = 1,
            burnedOut = burnedOut,
        ),
        winnerId = winner,
        acceptedPlaySequence = 7,
        outcomeSequence = 2,
    )
}

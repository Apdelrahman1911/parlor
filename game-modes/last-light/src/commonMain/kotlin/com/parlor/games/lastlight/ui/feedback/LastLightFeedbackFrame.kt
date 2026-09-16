package com.parlor.games.lastlight.ui.feedback

import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView

/** Only public accepted-event counters and the current verdict are retained by the effects service. */
internal data class LastLightFeedbackFrame(
    val acceptedPlaySequence: Long,
    val outcomeSequence: Long,
    val hasLatestClaim: Boolean,
    val resultCue: LastLightFeedbackCue?,
)

internal fun GameView.feedbackFrame(celebrateAnyWinner: Boolean = false): LastLightFeedbackFrame {
    val outcome = roundOutcome?.takeIf { it.roundNumber == roundNumber && phase != GamePhase.PLAYING }
    return LastLightFeedbackFrame(
        acceptedPlaySequence = acceptedPlaySequence,
        outcomeSequence = outcomeSequence,
        hasLatestClaim = latestClaim != null,
        resultCue = when {
            outcome == null -> null
            phase == GamePhase.FINISHED && winnerId != null &&
                (celebrateAnyWinner || viewerId != null && winnerId == viewerId) -> LastLightFeedbackCue.WIN
            outcome.burnedOut -> LastLightFeedbackCue.LIGHT_OUT
            else -> LastLightFeedbackCue.ROUND_END
        },
    )
}

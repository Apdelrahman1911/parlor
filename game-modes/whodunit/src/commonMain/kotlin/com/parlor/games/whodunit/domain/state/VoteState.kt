package com.parlor.games.whodunit.domain.state

import com.parlor.core.ids.PlayerId
import kotlinx.serialization.Serializable

/**
 * Vote-state extracted from WhodunitState for clarity. Models both Classic
 * Vote (one collection at the end) and Elimination Mode (one collection per
 * round) plus the tied-debate revote rule.
 */
@Serializable
sealed interface VoteState {
    @Serializable
    data object Idle : VoteState

    @Serializable
    data class Collecting(
        val isElimination: Boolean,
        val ballotPlayerIds: List<PlayerId>,
        val castSoFar: Map<PlayerId, PlayerId> = emptyMap(),
        val abstained: Set<PlayerId> = emptySet(),
        val currentVoterIndex: Int = 0,
        /**
         * True when this collection is the *revote* opened after a previous
         * tie (i.e., `OpenVote` ran from a [Tied] state). The reducer reads
         * this to decide whether a fresh tie should trigger the tied-twice
         * outcome (killer wins in Classic; advance round in Elimination,
         * per design doc §12 / §13).
         */
        val isSecondRound: Boolean = false,
    ) : VoteState

    @Serializable
    data class Tied(
        val tiedPlayerIds: List<PlayerId>,
        val debateSecondsRemaining: Int,
    ) : VoteState

    @Serializable
    data class Resolved(
        val accusedPlayerId: PlayerId,
        val wasKiller: Boolean,
    ) : VoteState

    @Serializable
    data class NoResolution(val reason: String) : VoteState
}

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
        /** Active players who still have a ballot in this vote. */
        val ballotPlayerIds: List<PlayerId>,
        /**
         * Players who may be accused.
         *
         * This is normally the same active roster as [ballotPlayerIds]. During
         * a tied revote it is deliberately narrower: every active player still
         * votes, but only the suspects tied in the first ballot are eligible.
         *
         * The default preserves decoding of snapshots written before this
         * field was introduced.
         */
        val candidatePlayerIds: List<PlayerId> = ballotPlayerIds,
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
        /**
         * Wire/snapshot compatibility with the former countdown UI.
         *
         * Revotes are now explicitly host-paced and untimed. New state always
         * writes zero; the snapshot codec normalizes legacy 0..60 values to
         * zero before structural validation.
         */
        val debateSecondsRemaining: Int = 0,
    ) : VoteState

    @Serializable
    data class Resolved(
        val accusedPlayerId: PlayerId,
        val wasKiller: Boolean,
    ) : VoteState

    @Serializable
    data class NoResolution(val reason: String) : VoteState
}

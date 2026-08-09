package com.parlor.games.whodunit.ui.flow

import com.parlor.core.ids.PlayerId
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.session.PlayMode
import com.parlor.session.isHost

/**
 * Device-local presentation decision for one authoritative voting turn.
 *
 * A multi-device host is still only one player: it may render a ballot for
 * its own identity, never for a remote player. Pass-and-play is deliberately
 * different because every player shares the same trusted local device.
 */
internal sealed interface VoteTurnPresentation {
    data class LocalBallot(val voterId: PlayerId) : VoteTurnPresentation
    data class WaitingForVoter(val voterId: PlayerId) : VoteTurnPresentation
    data object CloseByHost : VoteTurnPresentation
    data object WaitingForHostTally : VoteTurnPresentation
    data object Unsupported : VoteTurnPresentation
}

/**
 * Resolves which voting surface this device may present without mutating
 * state. The returned [VoteTurnPresentation.LocalBallot.voterId] is the only
 * identity the UI may place in CastVote/RefuseToVote actions.
 */
internal fun voteTurnPresentation(
    playMode: PlayMode,
    vote: VoteState.Collecting,
): VoteTurnPresentation {
    if (vote.currentVoterIndex !in 0..vote.ballotPlayerIds.size) {
        return VoteTurnPresentation.Unsupported
    }
    val nextVoter = vote.ballotPlayerIds.getOrNull(vote.currentVoterIndex)
        ?: return if (playMode.isHost) {
            VoteTurnPresentation.CloseByHost
        } else {
            VoteTurnPresentation.WaitingForHostTally
        }

    return when (playMode) {
        PlayMode.PassAndPlay -> VoteTurnPresentation.LocalBallot(nextVoter)
        is PlayMode.MultiDevice -> if (playMode.selfPlayerId == nextVoter) {
            VoteTurnPresentation.LocalBallot(nextVoter)
        } else {
            VoteTurnPresentation.WaitingForVoter(nextVoter)
        }
        PlayMode.Solo -> VoteTurnPresentation.Unsupported
    }
}

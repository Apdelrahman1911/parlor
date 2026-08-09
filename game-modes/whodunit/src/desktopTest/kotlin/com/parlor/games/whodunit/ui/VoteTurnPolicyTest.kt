package com.parlor.games.whodunit.ui.flow

import com.parlor.core.ids.PlayerId
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.session.PlayMode
import kotlin.test.Test
import kotlin.test.assertEquals

class VoteTurnPolicyTest {
    private val host = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val ballot = listOf(host, alice, bob)

    @Test
    fun pass_and_play_can_sequence_every_local_voter() {
        ballot.forEachIndexed { index, voter ->
            assertEquals(
                VoteTurnPresentation.LocalBallot(voter),
                voteTurnPresentation(PlayMode.PassAndPlay, collecting(index)),
            )
        }
    }

    @Test
    fun multi_device_host_can_open_only_the_hosts_own_ballot() {
        val hostMode = PlayMode.MultiDevice(selfPlayerId = host, isHost = true)

        assertEquals(
            VoteTurnPresentation.LocalBallot(host),
            voteTurnPresentation(hostMode, collecting(0)),
        )
        assertEquals(
            VoteTurnPresentation.WaitingForVoter(alice),
            voteTurnPresentation(hostMode, collecting(1)),
        )
        assertEquals(
            VoteTurnPresentation.WaitingForVoter(bob),
            voteTurnPresentation(hostMode, collecting(2)),
        )
    }

    @Test
    fun multi_device_peer_can_open_only_its_own_ballot() {
        val aliceMode = PlayMode.MultiDevice(selfPlayerId = alice, isHost = false)

        assertEquals(
            VoteTurnPresentation.WaitingForVoter(host),
            voteTurnPresentation(aliceMode, collecting(0)),
        )
        assertEquals(
            VoteTurnPresentation.LocalBallot(alice),
            voteTurnPresentation(aliceMode, collecting(1)),
        )
        assertEquals(
            VoteTurnPresentation.WaitingForVoter(bob),
            voteTurnPresentation(aliceMode, collecting(2)),
        )
    }

    @Test
    fun only_authoritative_devices_close_a_completed_vote() {
        val complete = collecting(ballot.size)

        assertEquals(
            VoteTurnPresentation.CloseByHost,
            voteTurnPresentation(PlayMode.PassAndPlay, complete),
        )
        assertEquals(
            VoteTurnPresentation.CloseByHost,
            voteTurnPresentation(
                PlayMode.MultiDevice(selfPlayerId = host, isHost = true),
                complete,
            ),
        )
        assertEquals(
            VoteTurnPresentation.WaitingForHostTally,
            voteTurnPresentation(
                PlayMode.MultiDevice(selfPlayerId = alice, isHost = false),
                complete,
            ),
        )
    }

    @Test
    fun unsupported_or_malformed_turns_fail_closed() {
        assertEquals(
            VoteTurnPresentation.Unsupported,
            voteTurnPresentation(PlayMode.Solo, collecting(0)),
        )
        assertEquals(
            VoteTurnPresentation.Unsupported,
            voteTurnPresentation(PlayMode.PassAndPlay, collecting(-1)),
        )
        assertEquals(
            VoteTurnPresentation.Unsupported,
            voteTurnPresentation(PlayMode.PassAndPlay, collecting(ballot.size + 1)),
        )
    }

    private fun collecting(currentVoterIndex: Int) = VoteState.Collecting(
        isElimination = true,
        ballotPlayerIds = ballot,
        currentVoterIndex = currentVoterIndex,
    )
}

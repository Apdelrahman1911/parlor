package com.parlor.games.whodunit.ui.flow

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.session.PlayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * Integrates the UI ownership policy with the real elimination-vote reducer
 * cursor. This guards the Round-phase path that previously gave the host
 * pass-and-play defaults and therefore exposed remote players' ballots.
 */
class EliminationVoteOwnershipIntegrationTest {
    private val players = listOf("host", "alice", "bob", "carol", "dina")
        .mapIndexed { seat, id -> Player(PlayerId(id), id, seat) }
    private val hostId = players.first().id
    private val deviceModes = players.associate { player ->
        player.id to PlayMode.MultiDevice(
            selfPlayerId = player.id,
            isHost = player.id == hostId,
        )
    }
    private val reducerContext = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(17L),
        case = WhodunitCase(
            publicIntro = "",
            bedrockClues = emptyList(),
            characters = emptyList(),
            cluePools = CluePools(
                publicUniversal = emptyList(),
                killerPointing = emptyMap(),
                redHerring = emptyMap(),
                contradiction = emptyMap(),
                finalStrong = emptyMap(),
            ),
            revealNarratives = emptyMap(),
        ),
    )

    @Test
    fun elimination_round_exposes_each_ballot_to_exactly_its_own_device() {
        var state = eliminationVoteState()

        players.forEach { expectedVoter ->
            val vote = assertIs<VoteState.Collecting>(state.public.voteState)
            assertEquals(expectedVoter.id, vote.ballotPlayerIds[vote.currentVoterIndex])

            val localDevices = deviceModes.mapNotNull { (devicePlayerId, mode) ->
                when (val presentation = voteTurnPresentation(mode, vote)) {
                    is VoteTurnPresentation.LocalBallot -> devicePlayerId to presentation.voterId
                    else -> null
                }
            }
            assertEquals(
                listOf(expectedVoter.id to expectedVoter.id),
                localDevices,
                "exactly the current voter's device may render a ballot",
            )

            val hostPresentation = voteTurnPresentation(deviceModes.getValue(hostId), vote)
            if (expectedVoter.id == hostId) {
                assertEquals(VoteTurnPresentation.LocalBallot(hostId), hostPresentation)
            } else {
                assertEquals(
                    VoteTurnPresentation.WaitingForVoter(expectedVoter.id),
                    hostPresentation,
                    "the multi-device host must not vote for a remote player",
                )
            }

            val target = players.first { it.id != expectedVoter.id }.id
            state = WhodunitReducer.reduce(
                state,
                WhodunitAction.CastVote(expectedVoter.id, target),
                reducerContext,
            ).newState
        }

        val completeVote = assertIs<VoteState.Collecting>(state.public.voteState)
        assertEquals(
            VoteTurnPresentation.CloseByHost,
            voteTurnPresentation(deviceModes.getValue(hostId), completeVote),
        )
        deviceModes
            .filterKeys { it != hostId }
            .values
            .forEach { peerMode ->
                assertEquals(
                    VoteTurnPresentation.WaitingForHostTally,
                    voteTurnPresentation(peerMode, completeVote),
                )
            }
    }

    @Test
    fun pass_and_play_still_sequences_every_ballot_on_the_shared_device() {
        var state = eliminationVoteState()

        players.forEach { expectedVoter ->
            val vote = assertIs<VoteState.Collecting>(state.public.voteState)
            assertEquals(
                VoteTurnPresentation.LocalBallot(expectedVoter.id),
                voteTurnPresentation(PlayMode.PassAndPlay, vote),
            )
            val target = players.first { it.id != expectedVoter.id }.id
            state = WhodunitReducer.reduce(
                state,
                WhodunitAction.CastVote(expectedVoter.id, target),
                reducerContext,
            ).newState
        }

        assertEquals(
            VoteTurnPresentation.CloseByHost,
            voteTurnPresentation(
                PlayMode.PassAndPlay,
                assertIs<VoteState.Collecting>(state.public.voteState),
            ),
        )
    }

    private fun eliminationVoteState(): WhodunitState {
        val ballot = players.map(Player::id)
        return WhodunitState(
            public = WhodunitPublic(
                caseId = CaseId("vote-ownership"),
                modeId = WhodunitIds.EliminationModeId,
                playersAtTable = players,
                currentRound = 1,
                voteState = VoteState.Collecting(
                    isElimination = true,
                    ballotPlayerIds = ballot,
                ),
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = WhodunitHostOnly(
                killerId = hostId,
                killerCharacterId = CharacterId("killer"),
                randomSeed = 17L,
                seatToCharacter = players.associate { player ->
                    player.id to CharacterId("character-${player.seat}")
                },
                redHerringTargets = emptyList(),
            ),
            phase = WhodunitPhase.Round(1),
            players = players,
        )
    }
}

package com.parlor.games.whodunit.ui.flow

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.GuiltyBrief
import com.parlor.games.whodunit.content.InnocentBrief
import com.parlor.games.whodunit.content.TimelineEntry
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.session.PlayMode
import kotlinx.serialization.json.Json
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
    private val json = Json { encodeDefaults = true }
    private val definition = WhodunitDefinition(json)
    private val players = listOf("host", "alice", "bob", "carol", "dina")
        .mapIndexed { seat, id -> Player(PlayerId(id), id, seat) }
    private val hostId = players.first().id
    private val deviceModes = players.associate { player ->
        player.id to PlayMode.MultiDevice(
            selfPlayerId = player.id,
            isHost = player.id == hostId,
        )
    }
    private val validatedCase = validatedWhodunitCaseForTest(
        payload = case(),
        caseId = CASE_ID,
        supportedPlayerCounts = players.size..players.size,
        supportedModes = listOf(WhodunitIds.EliminationModeId.raw),
    )
    private val reducerContext = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(SEED),
        case = validatedCase,
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
            state = step(state, WhodunitAction.CastVote(expectedVoter.id, target))
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
            state = step(state, WhodunitAction.CastVote(expectedVoter.id, target))
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
        var state = definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("vote-ownership-session"),
                caseId = CaseId(CASE_ID),
                modeId = WhodunitIds.EliminationModeId,
                players = players,
                randomSeed = SEED,
            ),
        )
        WhodunitStateValidator.requireValidForCase(state, validatedCase)
        state = step(state, WhodunitAction.AssignRoles(SEED))
        players.forEach { state = step(state, WhodunitAction.AcknowledgeIntro(it.id)) }
        state = step(state, WhodunitAction.AdvanceFromIntro)
        players.forEach { state = step(state, WhodunitAction.AcknowledgeBriefing(it.id)) }
        for (card in 1..4) {
            state = step(state, WhodunitAction.AdvanceBriefingCard(card))
        }
        val generation = state.public.roleAssignmentGeneration
        players.forEach { player ->
            state = step(state, WhodunitAction.StartCharacterReveal(player.id, generation))
            state = step(state, WhodunitAction.CompleteCharacterReveal(player.id, generation))
        }
        state = step(state, WhodunitAction.AdvanceFromCharacterReveal)
        state = step(state, WhodunitAction.RevealNextClue)
        state = step(state, WhodunitAction.StartDiscussionTimer(180))
        state = step(state, WhodunitAction.AdvanceFromDiscussion)
        assertIs<VoteState.Collecting>(state.public.voteState)
        return state
    }

    private fun step(state: WhodunitState, action: WhodunitAction): WhodunitState =
        WhodunitReducer.reduce(state, action, reducerContext).newState.also {
            WhodunitStateValidator.requireValidForCase(it, validatedCase)
        }

    private fun case(): WhodunitCase {
        val ids = players.indices.map { "character-$it" }
        val characters = ids.map { id -> character(id, ids) }
        return WhodunitCase(
            publicIntro = "Intro",
            bedrockClues = listOf("Bedrock"),
            characters = characters,
            cluePools = CluePools(
                publicUniversal = listOf(Clue("public", "Public")),
                killerPointing = ids.associateWith { id ->
                    (1..3).map { index -> Clue("pointing-$id-$index", "Pointing $index") }
                },
                redHerring = ids.associateWith { id ->
                    listOf(Clue("red-$id", "Red herring"))
                },
                contradiction = ids.associateWith { id ->
                    listOf(Clue("contradiction-$id", "Contradiction"))
                },
                finalStrong = ids.associateWith { id ->
                    (1..2).map { index -> Clue("final-$id-$index", "Final $index") }
                },
            ),
            revealNarratives = ids.associateWith { "Reveal $it" },
        )
    }

    private fun character(id: String, ids: List<String>) = Character(
        id = id,
        displayName = id,
        relationshipToVictim = "Friend",
        publicIdentity = "Identity",
        publicMotive = "Motive",
        privateSecret = "Secret",
        innocentBrief = InnocentBrief("Innocent", "Alibi", "Goal", "Say", "Hide"),
        guiltyBrief = GuiltyBrief(
            verdictLine = "Guilty",
            method = "Method",
            timeline = listOf(TimelineEntry("10:00", "Action")),
            fakeAlibi = "Alibi",
            deflectionTargets = ids.filterNot { it == id },
            panicMove = "Panic",
        ),
    )

    private companion object {
        const val CASE_ID = "vote-ownership"
        const val SEED = 17L
    }
}

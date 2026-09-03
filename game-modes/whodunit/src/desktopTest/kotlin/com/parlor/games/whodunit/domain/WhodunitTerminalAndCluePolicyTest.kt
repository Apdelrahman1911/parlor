package com.parlor.games.whodunit.domain

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ClueId
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
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WhodunitTerminalAndCluePolicyTest {
    private val json = Json { encodeDefaults = true }
    private val definition = WhodunitDefinition(json)
    private val players = (0 until PLAYER_COUNT).map { index ->
        Player(PlayerId("p${index + 1}"), "Player ${index + 1}", index)
    }
    private val characters = players.mapIndexed { index, _ -> character("c${index + 1}") }
    private val defaultCase = case()

    @Test
    fun modeRestrictedCluesAreNeverDrawnForAnotherMode() {
        val currentCase = case().let { base ->
            base.copy(
                cluePools = base.cluePools.copy(
                    publicUniversal = listOf(
                        Clue(
                            "classic-only",
                            "Classic",
                            appliesToModes = listOf(WhodunitIds.ClassicVoteModeId.raw),
                        ),
                    ),
                    killerPointing = characters.associate { character ->
                        character.id to (1..3).map { index ->
                            Clue(
                                "elimination-${character.id}-$index",
                                "Elimination $index",
                                appliesToModes = listOf(WhodunitIds.EliminationModeId.raw),
                            )
                        }
                    },
                ),
            )
        }
        val validated = validate(currentCase)
        for (seed in 0L..100L) {
            val context = context(validated, seed)
            val state = stateAtRound(round = 1, seed = seed, context = context)
            val result = step(state, WhodunitAction.RevealNextClue, context)
            assertTrue(result.public.revealedClues.single().id != ClueId("classic-only"))
        }
    }

    @Test
    fun fivePlayerEliminationTreatsRoundThreeAsFinalEvidence() {
        val validated = validate(defaultCase)
        val finalIds = defaultCase.cluePools.finalStrong.values
            .flatten()
            .map { ClueId(it.id) }
            .toSet()
        for (seed in 0L..100L) {
            val context = context(validated, seed)
            val state = stateAtRound(round = 3, seed = seed, context = context)
            val result = step(state, WhodunitAction.RevealNextClue, context)
            assertTrue(result.public.revealedClues.last().id in finalIds)
        }
    }

    @Test
    fun eliminationAllAbstainCannotAdvanceBeyondFiniteInvestigation() {
        val validated = validate(defaultCase)
        val context = context(validated)
        var state = stateAtRound(round = 3, context = context)
        state = openVote(state, context)
        val ballot = assertIs<VoteState.Collecting>(state.public.voteState).ballotPlayerIds
        ballot.forEach { voter ->
            state = step(state, WhodunitAction.AbstainVote(voter), context)
        }

        state = step(state, WhodunitAction.CloseVote, context)

        assertEquals(WhodunitPhase.Reveal, state.phase)
        val verdict = assertIs<Verdict.KillerWins>(state.public.verdict)
        assertEquals(KillerWinCause.TieUnresolved, verdict.cause)
    }

    @Test
    fun innocentEliminationOnFinalRoundCannotCreateAnUnauthoredRound() {
        val validated = validate(defaultCase)
        val context = context(validated)
        var state = stateAtRound(round = 2, context = context)
        state = eliminateAnInnocent(state, context)
        state = step(state, WhodunitAction.AcknowledgeRevealCard, context)
        state = eliminateAnInnocent(state, context)
        assertEquals(WhodunitPhase.Round(3), state.phase)

        state = step(state, WhodunitAction.AcknowledgeRevealCard, context)

        assertEquals(WhodunitPhase.Reveal, state.phase)
        val verdict = assertIs<Verdict.KillerWins>(state.public.verdict)
        assertEquals(KillerWinCause.TieUnresolved, verdict.cause)
    }

    @Test
    fun disconnectBeforeRoleAssignmentCancelsWithoutInventingAKiller() {
        val validated = validate(defaultCase)
        val context = context(validated)
        var state = initialState(seed = 1L)
        WhodunitStateValidator.requireValidForCase(state, validated)
        state = step(state, WhodunitAction.MarkPlayerDisconnected(players[1].id), context)
        state = step(state, WhodunitAction.ContinueWithoutPlayer(players[1].id), context)

        assertEquals(WhodunitPhase.PostGame, state.phase)
        assertNull(state.public.verdict)
        assertEquals(emptySet(), state.public.disconnectedPlayers)
        assertEquals("unassigned", state.hostOnly.killerId.raw)
    }

    @Test
    fun revealCannotFinishAroundADisconnectedPlayerRace() {
        val validated = validate(defaultCase)
        val context = context(validated)
        var state = stateAtRound(round = 1, context = context)
        state = openVote(state, context)
        state = resolveVoteFor(state, state.hostOnly.killerId, context)
        assertEquals(WhodunitPhase.Reveal, state.phase)
        val disconnectedPlayer = state.players.first { it.id != state.hostOnly.killerId }.id
        state = step(
            state,
            WhodunitAction.MarkPlayerDisconnected(disconnectedPlayer),
            context,
        )

        val premature = step(state, WhodunitAction.AcknowledgeReveal, context)
        assertEquals(state, premature)

        val expired = step(
            state,
            WhodunitAction.ContinueWithoutPlayer(disconnectedPlayer),
            context,
        )
        assertEquals(WhodunitPhase.PostGame, expired.phase)
        assertEquals(emptySet(), expired.public.disconnectedPlayers)
    }

    private fun stateAtRound(
        round: Int,
        seed: Long = 1L,
        context: WhodunitReducerContext,
    ): WhodunitState {
        var state = initialState(seed)
        WhodunitStateValidator.requireValidForCase(state, context.case)
        state = step(state, WhodunitAction.AssignRoles(seed), context)
        players.forEach { player ->
            state = step(state, WhodunitAction.AcknowledgeIntro(player.id), context)
        }
        state = step(state, WhodunitAction.AdvanceFromIntro, context)
        players.forEach { player ->
            state = step(state, WhodunitAction.AcknowledgeBriefing(player.id), context)
        }
        for (card in 1..4) {
            state = step(state, WhodunitAction.AdvanceBriefingCard(card), context)
        }
        val generation = state.public.roleAssignmentGeneration
        players.forEach { player ->
            state = step(
                state,
                WhodunitAction.StartCharacterReveal(player.id, generation),
                context,
            )
            state = step(
                state,
                WhodunitAction.CompleteCharacterReveal(player.id, generation),
                context,
            )
        }
        state = step(state, WhodunitAction.AdvanceFromCharacterReveal, context)
        repeat(round - 1) {
            state = openVote(state, context)
            val ballot = assertIs<VoteState.Collecting>(state.public.voteState).ballotPlayerIds
            ballot.forEach { voter ->
                state = step(state, WhodunitAction.AbstainVote(voter), context)
            }
            state = step(state, WhodunitAction.CloseVote, context)
        }
        return state
    }

    private fun openVote(
        initial: WhodunitState,
        context: WhodunitReducerContext,
    ): WhodunitState {
        var state = step(initial, WhodunitAction.RevealNextClue, context)
        state = step(state, WhodunitAction.StartDiscussionTimer(180), context)
        return step(state, WhodunitAction.AdvanceFromDiscussion, context)
    }

    private fun eliminateAnInnocent(
        initial: WhodunitState,
        context: WhodunitReducerContext,
    ): WhodunitState {
        val state = openVote(initial, context)
        val target = state.players.first { player ->
            player.id != state.hostOnly.killerId &&
                player.id !in state.public.eliminatedPlayers
        }.id
        return resolveVoteFor(state, target, context)
    }

    private fun resolveVoteFor(
        initial: WhodunitState,
        target: PlayerId,
        context: WhodunitReducerContext,
    ): WhodunitState {
        var state = initial
        val ballot = assertIs<VoteState.Collecting>(state.public.voteState).ballotPlayerIds
        ballot.forEach { voter ->
            state = step(
                state,
                if (voter == target) {
                    WhodunitAction.AbstainVote(voter)
                } else {
                    WhodunitAction.CastVote(voter, target)
                },
                context,
            )
        }
        return step(state, WhodunitAction.CloseVote, context)
    }

    private fun initialState(seed: Long): WhodunitState = definition.createInitialState(
        SessionConfig(
            sessionId = SessionId("terminal-clue-$seed"),
            caseId = CaseId(CASE_ID),
            modeId = WhodunitIds.EliminationModeId,
            players = players,
            randomSeed = seed,
        ),
    )

    private fun step(
        state: WhodunitState,
        action: WhodunitAction,
        context: WhodunitReducerContext,
    ): WhodunitState = WhodunitReducer.reduce(state, action, context).newState.also {
        WhodunitStateValidator.requireValidForCase(it, context.case)
    }

    private fun context(
        validated: ValidatedCase<WhodunitCase>,
        seed: Long = 1L,
    ) = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(seed),
        case = validated,
    )

    private fun validate(payload: WhodunitCase): ValidatedCase<WhodunitCase> =
        validatedWhodunitCaseForTest(
            payload = payload,
            caseId = CASE_ID,
            supportedPlayerCounts = PLAYER_COUNT..PLAYER_COUNT,
        )

    private fun case(): WhodunitCase = WhodunitCase(
        publicIntro = "Intro",
        bedrockClues = listOf("Bedrock"),
        characters = characters,
        cluePools = CluePools(
            publicUniversal = listOf(Clue("public", "Public")),
            killerPointing = characters.associate { character ->
                character.id to (1..3).map { index ->
                    Clue("pointing-${character.id}-$index", "Pointing $index")
                }
            },
            redHerring = characters.associate { character ->
                character.id to (1..2).map { index ->
                    Clue("red-${character.id}-$index", "Red herring $index")
                }
            },
            contradiction = characters.associate { character ->
                character.id to (1..2).map { index ->
                    Clue("contradiction-${character.id}-$index", "Contradiction $index")
                }
            },
            finalStrong = characters.associate { character ->
                character.id to (1..2).map { index ->
                    Clue("final-${character.id}-$index", "Final $index")
                }
            },
        ),
        revealNarratives = characters.associate { it.id to "Reveal ${it.id}" },
    )

    private fun character(id: String) = Character(
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
            deflectionTargets = players.indices.map { "c${it + 1}" }.filterNot { it == id },
            panicMove = "Panic",
        ),
    )

    private companion object {
        const val CASE_ID = "test"
        const val PLAYER_COUNT = 5
    }
}

package com.parlor.games.whodunit.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.parlor.content.validation.ValidatedCase
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
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** Deterministic clue-selection checks driven only through admitted content and legal states. */
class CluePolicyTest {

    private val json = Json { encodeDefaults = true }
    private val definition = WhodunitDefinition(json)
    private val players = (0 until PLAYER_COUNT).map { index ->
        Player(PlayerId("p-${index + 1}"), "Player ${index + 1}", seat = index)
    }
    private val characterIds = (1..PLAYER_COUNT).map { "c-$it" }
    private val finalStrongClues = characterIds.associateWith { characterId ->
        (1..3).map { index -> clue("fs-$characterId-$index", "Final $index for $characterId") }
    }
    private val case = WhodunitCase(
        publicIntro = "Intro",
        bedrockClues = listOf("Bedrock"),
        characters = characterIds.map(::character),
        cluePools = CluePools(
            publicUniversal = (1..2).map { clue("public-$it", "Public $it") },
            killerPointing = characterIds.associateWith { characterId ->
                (1..3).map { index ->
                    clue("pointing-$characterId-$index", "Pointing $index for $characterId")
                }
            },
            contradiction = characterIds.associateWith { characterId ->
                listOf(clue("contradiction-$characterId", "Contradiction for $characterId"))
            },
            redHerring = characterIds.associateWith { characterId ->
                listOf(clue("red-$characterId", "Red herring for $characterId"))
            },
            finalStrong = finalStrongClues,
        ),
        revealNarratives = characterIds.associateWith { "Reveal $it" },
    )
    private val validatedCase = validate(case)

    @Test
    fun lastRoundAlwaysUsesFinalStrongWhenAvailable() {
        val finalStrongIds = finalStrongClues.values.flatten().map { it.id }.toSet()
        for (seed in 1L..50L) {
            assertThat(pickLateRoundClueId(seed) in finalStrongIds).isTrue()
        }
    }

    @Test
    fun sameSeedProducesSameClueSequence() {
        for (seed in 1L..50L) {
            assertThat(driveCluesThroughFinalRound(seed))
                .isEqualTo(driveCluesThroughFinalRound(seed))
        }
    }

    @Test
    fun differentSeedsProduceAtLeastOneDifferentSequence() {
        val sequences = (1L..50L).map(::driveCluesThroughFinalRound)
        assertThat(sequences.toSet().size > 1).isTrue()
    }

    @Test
    fun admittedCaseNeverDrawsTheSameClueTwice() {
        for (seed in 1L..50L) {
            val sequence = driveCluesThroughFinalRound(seed)
            assertThat(sequence.size).isEqualTo(sequence.toSet().size)
        }
    }

    @Test
    fun exhaustedCluePoolsAreRejectedBeforeReducerUse() {
        val emptyPools = case.copy(
            cluePools = CluePools(
                publicUniversal = emptyList(),
                killerPointing = characterIds.associateWith { emptyList() },
                contradiction = characterIds.associateWith { emptyList() },
                redHerring = characterIds.associateWith { emptyList() },
                finalStrong = characterIds.associateWith { emptyList() },
            ),
        )

        assertFailsWith<IllegalStateException> { validate(emptyPools) }
    }

    private fun pickLateRoundClueId(seed: Long): String {
        val context = context()
        val state = stateAtRound(roundIndex = FINAL_ROUND, seed = seed, context = context)
        return step(state, WhodunitAction.RevealNextClue, context)
            .public
            .revealedClues
            .last()
            .id
            .raw
    }

    private fun driveCluesThroughFinalRound(seed: Long): List<String> {
        val context = context()
        var state = stateAtRound(roundIndex = 1, seed = seed, context = context)
        val picks = mutableListOf<String>()
        repeat(FINAL_ROUND) { index ->
            state = step(state, WhodunitAction.RevealNextClue, context)
            picks += state.public.revealedClues.last().id.raw
            if (index < FINAL_ROUND - 1) {
                state = step(state, WhodunitAction.StartDiscussionTimer(180), context)
                state = step(state, WhodunitAction.AdvanceFromDiscussion, context)
            }
        }
        return picks
    }

    private fun stateAtRound(
        roundIndex: Int,
        seed: Long,
        context: WhodunitReducerContext,
    ): WhodunitState {
        var state = definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("clue-policy-$seed"),
                caseId = CaseId(CASE_ID),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = seed,
            ),
        ).also { WhodunitStateValidator.requireValidForCase(it, validatedCase) }
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
        repeat(roundIndex - 1) {
            state = step(state, WhodunitAction.RevealNextClue, context)
            state = step(state, WhodunitAction.StartDiscussionTimer(180), context)
            state = step(state, WhodunitAction.AdvanceFromDiscussion, context)
        }
        return state
    }

    private fun step(
        state: WhodunitState,
        action: WhodunitAction,
        context: WhodunitReducerContext,
    ): WhodunitState = WhodunitReducer.reduce(state, action, context).newState.also {
        WhodunitStateValidator.requireValidForCase(it, validatedCase)
    }

    private fun context() = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(0L),
        case = validatedCase,
    )

    private fun validate(payload: WhodunitCase): ValidatedCase<WhodunitCase> =
        validatedWhodunitCaseForTest(
            payload = payload,
            caseId = CASE_ID,
            supportedPlayerCounts = PLAYER_COUNT..PLAYER_COUNT,
            supportedModes = listOf(WhodunitIds.ClassicVoteModeId.raw),
        )

    private fun clue(id: String, text: String): Clue = Clue(id = id, text = text)

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
            deflectionTargets = characterIds.filterNot { it == id },
            panicMove = "Panic",
        ),
    )

    private companion object {
        const val CASE_ID = "test-case"
        const val PLAYER_COUNT = 6
        const val FINAL_ROUND = 4
    }
}

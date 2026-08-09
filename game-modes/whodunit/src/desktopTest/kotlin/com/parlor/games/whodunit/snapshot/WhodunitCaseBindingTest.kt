package com.parlor.games.whodunit.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.rules.WhodunitRoundPolicy
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.ui.flow.ResumedSession
import com.parlor.games.whodunit.ui.flow.validateResumedSessionForCase
import com.parlor.session.PlayMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.time.Instant

/**
 * Recovery trust-boundary tests deliberately use the shipping case through
 * both DefaultCaseValidator and WhodunitPayloadValidator. Defensive reducer
 * fixtures are useful elsewhere, but cannot prove envelope/content binding.
 */
@OptIn(ExperimentalResourceApi::class)
class WhodunitCaseBindingTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val seed = 91L
    private val players = (1..6).map { index ->
        Player(PlayerId("p$index"), "Player $index", index - 1)
    }

    @Test
    fun shippingCaseRejectsFourPlayersAndAcceptsAValidSixPlayerAssignment() = runTest {
        val case = loadValidatedCase()
        val unsupported = initialState(case, players.take(4))
        assertRejected(unsupported, case)

        val assigned = assignedState(case)
        assertThat(assigned).isNotEqualTo(initialState(case, players))
        assertAccepted(assigned, case)
        assertAccepted(assigned, case, withoutContentIdentity = true)
    }

    @Test
    fun validatedEnvelopeBindsCaseIdModeAndEffectivePlayerCount() = runTest {
        val case = loadValidatedCase()
        val assigned = assignedState(case)
        assertRejected(
            assigned.copy(public = assigned.public.copy(caseId = CaseId("another-case"))),
            case,
        )

        val classicOnly = loadValidatedCase { envelope ->
            envelope.copy(supportedModes = listOf(WhodunitIds.ClassicVoteModeId.raw))
        }
        val elimination = assignedState(case, modeId = WhodunitIds.EliminationModeId)
        assertRejected(elimination, classicOnly)

        assertRejected(initialState(case, players.take(4)), case)
    }

    @Test
    fun authoredFilteredDeflectionTargetsMustMatchExactly() = runTest {
        val case = loadValidatedCase()
        val assigned = assignedState(case)
        val original = assigned.hostOnly.redHerringTargets
        check(original.isNotEmpty()) { "Shipping case must exercise filtered deflection targets" }
        val changed = listOf(
            assigned.hostOnly.seatToCharacter.values.first {
                it != assigned.hostOnly.killerCharacterId && it !in original
            },
        )
        assertThat(changed).isNotEqualTo(original)
        val killerId = assigned.hostOnly.killerId
        val forged = assigned.copy(
            hostOnly = assigned.hostOnly.copy(redHerringTargets = changed),
            privatePerPlayer = assigned.privatePerPlayer + (
                killerId to assigned.privatePerPlayer.getValue(killerId).copy(
                    deflectionTargets = changed,
                )
            ),
        )

        assertRejected(forged, case)
        assertRejected(forged, case, withoutContentIdentity = true)
    }

    @Test
    fun deterministicCluePrefixRejectsAnotherSeedChoiceAndWrongRoundCategory() = runTest {
        val case = loadValidatedCase()
        val roundOne = stateThroughRound(case, throughRound = 1)
        assertAccepted(roundOne, case)

        val killer = roundOne.hostOnly.killerCharacterId.raw
        val expected = roundOne.public.revealedClues.single()
        val samePoolAlternative = (
            case.payload.cluePools.publicUniversal +
                case.payload.cluePools.killerPointing.getValue(killer)
            ).first { it.id != expected.id.raw }
        assertRejected(roundOne.withLastClue(samePoolAlternative), case)

        val wrongCategory = case.payload.cluePools.contradiction.getValue(killer)
            .first { it.id != expected.id.raw }
        assertRejected(roundOne.withLastClue(wrongCategory), case)
        assertRejected(
            roundOne.withLastClue(samePoolAlternative),
            case,
            withoutContentIdentity = true,
        )
    }

    @Test
    fun deterministicCluePrefixRejectsASeedMismatchAndNonFinalEvidenceInFinalRound() = runTest {
        val case = loadValidatedCase()
        val alternateSeedState = stateThroughRound(case, throughRound = 1, stateSeed = seed + 1)
        val forgedSeed = alternateSeedState.copy(
            hostOnly = alternateSeedState.hostOnly.copy(randomSeed = seed),
        )
        assertRejected(forgedSeed, case)

        val finalRound = stateThroughRound(case, throughRound = 4)
        assertAccepted(finalRound, case)
        val killer = finalRound.hostOnly.killerCharacterId.raw
        val drawnBeforeFinal = finalRound.public.revealedClues.dropLast(1).map { it.id }.toSet()
        val nonFinal = (
            case.payload.cluePools.publicUniversal +
                case.payload.cluePools.killerPointing.getValue(killer) +
                case.payload.cluePools.contradiction.getValue(killer)
            ).first { ClueId(it.id) !in drawnBeforeFinal }
        assertRejected(finalRound.withLastClue(nonFinal), case)
    }

    @Test
    fun drawnClueSetMustEqualTheReconstructedPrefixExactly() = runTest {
        val case = loadValidatedCase()
        val valid = stateThroughRound(case, throughRound = 2)
        val forged = valid.copy(
            hostOnly = valid.hostOnly.copy(
                drawnClueIds = valid.hostOnly.drawnClueIds + ClueId("forged-extra-clue"),
            ),
        )
        assertRejected(forged, case)
    }

    @Test
    fun reducerAcceptsOnlyTheExactAuthoredDiscussionDuration() = runTest {
        val case = loadValidatedCase()
        val revealed = stateThroughRound(case, throughRound = 1)
        val authored = WhodunitRoundPolicy.discussionSeconds(case.payload, 1, players.size)
        val rejected = reduce(
            revealed,
            WhodunitAction.StartDiscussionTimer(authored - 1),
            case,
        )
        assertThat(rejected).isEqualTo(revealed)

        val accepted = reduce(
            revealed,
            WhodunitAction.StartDiscussionTimer(authored),
            case,
        )
        assertThat(accepted.public.timer?.totalSeconds).isEqualTo(authored)
        assertAccepted(accepted, case)
    }

    @Test
    fun recoveredTimerMustMatchAuthoredIdDurationAndRemainderPolicy() = runTest {
        val case = loadValidatedCase()
        val revealed = stateThroughRound(case, throughRound = 1)
        val authored = WhodunitRoundPolicy.discussionSeconds(case.payload, 1, players.size)
        val valid = reduce(
            revealed,
            WhodunitAction.StartDiscussionTimer(authored),
            case,
        )
        assertAccepted(valid, case)

        val timer = requireNotNull(valid.public.timer)
        assertRejected(
            valid.copy(
                public = valid.public.copy(
                    timer = timer.copy(
                        totalSeconds = authored - 1,
                        remainingSeconds = authored - 1,
                    ),
                ),
            ),
            case,
        )
        assertRejected(
            valid.copy(public = valid.public.copy(timer = timer.copy(timerId = "discussion-2"))),
            case,
        )
        assertRejected(
            valid.copy(
                public = valid.public.copy(
                    timer = timer.copy(remainingSeconds = authored + 1),
                ),
            ),
            case,
        )
    }

    private suspend fun loadValidatedCase(
        transform: (CaseEnvelope) -> CaseEnvelope = { it },
    ): ValidatedCase<WhodunitCase> {
        val raw = Res.readBytes("files/cases/last-dinner.json").decodeToString()
        val envelope = transform(json.decodeFromString(CaseEnvelope.serializer(), raw))
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
        )
        return when (
            val result = validator.validate(
                json.encodeToString(CaseEnvelope.serializer(), envelope),
                WhodunitPayloadValidator(json),
            )
        ) {
            is Result.Success -> result.data
            is Result.Failure -> error("Shipping case failed validation: ${result.error}")
        }
    }

    private fun initialState(
        case: ValidatedCase<WhodunitCase>,
        roster: List<Player> = players,
        modeId: com.parlor.core.ids.ModeId = WhodunitIds.ClassicVoteModeId,
        stateSeed: Long = seed,
    ): WhodunitState = WhodunitDefinition(json).createInitialState(
        SessionConfig(
            sessionId = SessionId("case-binding-${modeId.raw}-${roster.size}-$stateSeed"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = roster,
            randomSeed = stateSeed,
        ),
    )

    private fun assignedState(
        case: ValidatedCase<WhodunitCase>,
        modeId: com.parlor.core.ids.ModeId = WhodunitIds.ClassicVoteModeId,
        stateSeed: Long = seed,
    ): WhodunitState = reduce(
        initialState(case, modeId = modeId, stateSeed = stateSeed),
        WhodunitAction.AssignRoles(stateSeed),
        case,
    )

    private fun stateThroughRound(
        case: ValidatedCase<WhodunitCase>,
        throughRound: Int,
        stateSeed: Long = seed,
    ): WhodunitState {
        var state = assignedState(case, stateSeed = stateSeed)
        for (round in 1..throughRound) {
            state = state.copy(
                phase = WhodunitPhase.Round(round),
                public = state.public.copy(
                    currentRound = round,
                    timer = null,
                    voteState = VoteState.Idle,
                ),
            )
            state = reduce(state, WhodunitAction.RevealNextClue, case)
            check(state.public.revealedClues.size == round) {
                "Shipping case could not reveal deterministic round $round"
            }
        }
        return state
    }

    private fun reduce(
        state: WhodunitState,
        action: WhodunitAction,
        case: ValidatedCase<WhodunitCase>,
    ): WhodunitState = WhodunitReducer.reduce(
        state,
        action,
        WhodunitReducerContext(
            clock = FakeClock(Instant.fromEpochMilliseconds(0)),
            random = RandomSource.seeded(seed),
            case = case,
        ),
    ).newState

    private fun WhodunitState.withLastClue(clue: Clue): WhodunitState {
        val replacement = RevealedClue(
            id = ClueId(clue.id),
            text = clue.text,
            roundIndex = public.revealedClues.last().roundIndex,
        )
        val clues = public.revealedClues.dropLast(1) + replacement
        return copy(
            public = public.copy(revealedClues = clues),
            hostOnly = hostOnly.copy(drawnClueIds = clues.map { it.id }.toSet()),
        )
    }

    private fun assertAccepted(
        state: WhodunitState,
        case: ValidatedCase<WhodunitCase>,
        withoutContentIdentity: Boolean = false,
    ) {
        val resumed = resumed(state, case).let {
            if (withoutContentIdentity) it.copy(contentIdentity = null) else it
        }
        assertThat(validateResumedSessionForCase(resumed, case))
            .isInstanceOf(Result.Success::class)
    }

    private fun assertRejected(
        state: WhodunitState,
        case: ValidatedCase<WhodunitCase>,
        withoutContentIdentity: Boolean = false,
    ) {
        val resumed = resumed(state, case).let {
            if (withoutContentIdentity) it.copy(contentIdentity = null) else it
        }
        assertThat(validateResumedSessionForCase(resumed, case)).isEqualTo(
            Result.Failure(DataError.CorruptedData),
        )
    }

    private fun resumed(
        state: WhodunitState,
        case: ValidatedCase<WhodunitCase>,
    ) = ResumedSession(
        sessionId = SessionId("case-binding-resume"),
        state = state,
        contentIdentity = case.envelope.contentIdentity(),
        playMode = PlayMode.PassAndPlay,
    )
}

package com.parlor.games.whodunit.snapshot

import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.rules.WhodunitRoundPolicy
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.ui.flow.ResumedSession
import com.parlor.games.whodunit.ui.flow.loadResumedSession
import com.parlor.games.whodunit.ui.flow.validateResumedSessionForCase
import com.parlor.session.PlayMode
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Uses the shipping six-seat case; malformed inputs model an erroneous trusted producer. */
@OptIn(ExperimentalResourceApi::class)
class WhodunitFinalTwoRecoveryTest {
    private val json = Json { encodeDefaults = true }
    private val definition = WhodunitDefinition(json)
    private val codec = WhodunitSnapshotCodec(json)
    private val players = (1..6).map { Player(PlayerId("p$it"), "Player $it", it - 1) }
    private val sessionId = SessionId("final-two-recovery")

    @Test
    fun currentEncoderRejectsEveryReopenedFinalTwoSubstate() = runTest {
        reopenedStates(legalFinalTwo(loadCase())).forEach { malformed ->
            assertFinalTwoRejected { codec.encode(malformed) }
        }
    }

    @Test
    fun currentDecoderRejectsCanonicalJsonThatReopensTheFinalTwo() = runTest {
        reopenedStates(legalFinalTwo(loadCase())).forEach { malformed ->
            // Bypass encode's guard so this independently exercises the decoder.
            assertFinalTwoRejected { codec.decode(currentPayload(malformed)) }
        }
    }

    @Test
    fun bareLegacyPayloadCannotRepairAnImpossibleActiveFinalTwo() = runTest {
        reopenedStates(legalFinalTwo(loadCase())).forEach { malformed ->
            val bare = json.encodeToString(WhodunitState.serializer(), malformed).encodeToByteArray()
            assertFinalTwoRejected { codec.decode(bare) }
        }
    }

    @Test
    fun caseBoundRecoveryRejectsFinalTwoEvenWithTheCorrectContentIdentity() = runTest {
        val case = loadCase()
        reopenedStates(legalFinalTwo(case)).forEach { malformed ->
            assertFinalTwoRejected {
                WhodunitStateValidator.requireValidForCase(malformed, case)
            }
            assertEquals(
                Result.Failure(DataError.CorruptedData),
                validateResumedSessionForCase(resumed(malformed, case), case),
            )
        }
    }

    @Test
    fun productionLocalResumeLoaderRejectsAnImpossibleTrustedPayload() = runTest {
        val case = loadCase()
        reopenedStates(legalFinalTwo(case)).forEach { malformed ->
            assertEquals(
                Result.Failure(DataError.CorruptedData),
                loadResumedSession(store(malformed, case), definition, sessionId),
            )
        }
    }

    @Test
    fun everyOwnPrivatePeerBoundaryRejectsAReopenedFinalTwo() = runTest {
        val case = loadCase()
        reopenedStates(legalFinalTwo(case)).forEach { malformed ->
            assertPeerValidity(malformed, case, expected = false)
        }
    }

    @Test
    fun legalFinalTwoKillerVictoryStillRecoversThroughPostGameAndReplay() = runTest {
        val case = loadCase()
        val terminal = legalFinalTwo(case)
        assertEquals(
            KillerWinCause.SurvivedToFinalTwo,
            assertIs<Verdict.KillerWins>(terminal.public.verdict).cause,
        )
        assertValidRecovery(terminal, case)

        val postGame = step(terminal, WhodunitAction.AcknowledgeReveal, case)
        assertEquals(WhodunitPhase.PostGame, postGame.phase)
        assertValidRecovery(postGame, case)

        val replay = step(postGame, WhodunitAction.BeginReplay, case)
        assertEquals(WhodunitPhase.PublicIntro, replay.phase)
        assertEquals(terminal.public.roleAssignmentGeneration + 1, replay.public.roleAssignmentGeneration)
        assertTrue(replay.public.eliminatedPlayers.isEmpty())
        assertValidRecovery(replay, case)
    }

    @Test
    fun aLegalLastRoundKillerEliminationStillAllowsTwoInnocentSurvivors() = runTest {
        val case = loadCase()
        val ballot = legalLastRoundBallot(case)
        assertValidRecovery(ballot, case)
        val terminal = accuse(ballot, ballot.hostOnly.killerId, case)
        assertEquals(WhodunitPhase.Reveal, terminal.phase)
        assertIs<Verdict.PlayersWin>(terminal.public.verdict)
        assertEquals(players.size - 2, terminal.public.eliminatedPlayers.size)
        assertValidRecovery(terminal, case)
    }

    @Test
    fun earlyEndWithAndWithoutDisclosureRemainRecoverable() = runTest {
        val case = loadCase()
        val ballot = legalLastRoundBallot(case)
        listOf(false, true).forEach { withReveal ->
            val terminal = step(ballot, WhodunitAction.EndGameEarly(withReveal), case)
            assertEquals(
                if (withReveal) WhodunitPhase.Reveal else WhodunitPhase.PostGame,
                terminal.phase,
            )
            assertValidRecovery(terminal, case)
        }
    }

    private suspend fun loadCase(): ValidatedCase<WhodunitCase> {
        val validator = DefaultCaseValidator(
            json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(definition)),
        )
        return assertIs<Result.Success<ValidatedCase<WhodunitCase>>>(
            validator.validate(
                Res.readBytes("files/cases/last-dinner.json").decodeToString(throwOnInvalidSequence = true),
                WhodunitPayloadValidator(json),
            ),
        ).data
    }

    private fun legalLastRoundBallot(case: ValidatedCase<WhodunitCase>): WhodunitState {
        var state = definition.createInitialState(
            SessionConfig(sessionId, CaseId(case.envelope.caseId), WhodunitIds.EliminationModeId, players, SEED),
        )
        state = step(state, WhodunitAction.AssignRoles(SEED), case)
        players.forEach { state = step(state, WhodunitAction.AcknowledgeIntro(it.id), case) }
        state = step(state, WhodunitAction.AdvanceFromIntro, case)
        players.forEach { state = step(state, WhodunitAction.AcknowledgeBriefing(it.id), case) }
        (1..4).forEach { state = step(state, WhodunitAction.AdvanceBriefingCard(it), case) }
        val generation = state.public.roleAssignmentGeneration
        players.forEach { player ->
            state = step(state, WhodunitAction.StartCharacterReveal(player.id, generation), case)
            state = step(state, WhodunitAction.CompleteCharacterReveal(player.id, generation), case)
        }
        state = step(state, WhodunitAction.AdvanceFromCharacterReveal, case)
        (1..4).forEach { round ->
            assertEquals(WhodunitPhase.Round(round), state.phase)
            state = step(state, WhodunitAction.RevealNextClue, case)
            state = step(
                state,
                WhodunitAction.StartDiscussionTimer(
                    WhodunitRoundPolicy.discussionSeconds(case.payload, round, players.size),
                ),
                case,
            )
            state = step(state, WhodunitAction.AdvanceFromDiscussion, case)
            if (round < 4) {
                state = accuse(state, survivingInnocent(state), case)
                state = step(state, WhodunitAction.AcknowledgeRevealCard, case)
            }
        }
        assertEquals(3, assertIs<VoteState.Collecting>(state.public.voteState).ballotPlayerIds.size)
        return state
    }

    private fun legalFinalTwo(case: ValidatedCase<WhodunitCase>): WhodunitState {
        val ballot = legalLastRoundBallot(case)
        return accuse(ballot, survivingInnocent(ballot), case).also { terminal ->
            assertEquals(WhodunitPhase.Reveal, terminal.phase)
            assertEquals(players.size - 2, terminal.public.eliminatedPlayers.size)
            WhodunitStateValidator.requireValidForCase(terminal, case)
        }
    }

    private fun survivingInnocent(state: WhodunitState): PlayerId = players.first {
        it.id != state.hostOnly.killerId && it.id !in state.public.eliminatedPlayers
    }.id

    private fun accuse(
        state: WhodunitState,
        accused: PlayerId,
        case: ValidatedCase<WhodunitCase>,
    ): WhodunitState {
        var voting = state
        assertIs<VoteState.Collecting>(state.public.voteState).ballotPlayerIds.forEach { voter ->
            voting = step(
                voting,
                if (voter == accused) WhodunitAction.AbstainVote(voter)
                else WhodunitAction.CastVote(voter, accused),
                case,
            )
        }
        return step(voting, WhodunitAction.CloseVote, case)
    }

    private fun reopenedStates(terminal: WhodunitState): List<WhodunitState> {
        val survivors = players.map { it.id }.filterNot(terminal.public.eliminatedPlayers::contains)
        val ballot = VoteState.Collecting(isElimination = true, ballotPlayerIds = survivors)
        val round = WhodunitPhase.Round(terminal.public.currentRound)
        return listOf(
            round to VoteState.Idle,
            round to ballot,
            round to terminal.public.voteState,
            WhodunitPhase.TiedRevote to VoteState.Tied(survivors),
            WhodunitPhase.TiedRevote to ballot.copy(isSecondRound = true),
        ).map { (phase, vote) ->
            terminal.copy(phase = phase, public = terminal.public.copy(verdict = null, voteState = vote))
        }
    }

    private fun currentPayload(state: WhodunitState): ByteArray = json.encodeToString(
        WhodunitSnapshotPayload.serializer(),
        WhodunitSnapshotPayload(
            kind = WHODUNIT_SNAPSHOT_KIND,
            schemaVersion = WHODUNIT_SNAPSHOT_SCHEMA_VERSION,
            state = json.encodeToJsonElement(WhodunitState.serializer(), state).jsonObject,
        ),
    ).encodeToByteArray()

    private fun resumed(state: WhodunitState, case: ValidatedCase<WhodunitCase>) = ResumedSession(
        sessionId = sessionId,
        state = state,
        contentIdentity = case.envelope.contentIdentity(),
        playMode = PlayMode.PassAndPlay,
    )

    private fun store(state: WhodunitState, case: ValidatedCase<WhodunitCase>): SnapshotStore {
        val identity = case.envelope.contentIdentity()
        val snapshot = GameSnapshot(
            sessionId = sessionId,
            gameId = WhodunitIds.GameId,
            engineVersion = WHODUNIT_SNAPSHOT_ENGINE_VERSION,
            createdAt = Instant.fromEpochMilliseconds(0),
            phaseId = state.phase.id,
            payload = currentPayload(state),
            metadata = mapOf(
                "playMode" to "PassAndPlay",
                "caseVersion" to identity.version,
                "caseDigest" to identity.digest,
            ),
        )
        return object : SnapshotStore {
            override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> = error("Unexpected write")
            override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> = Result.Success(snapshot)
            override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = error("Unexpected delete")
            override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = error("Unexpected list")
        }
    }

    private suspend fun assertValidRecovery(state: WhodunitState, case: ValidatedCase<WhodunitCase>) {
        WhodunitStateValidator.requireValidForCase(state, case)
        assertEquals(state, codec.decode(codec.encode(state)))
        // Keep the internal production loader's suspending contract explicit
        // for compiler-backed test analysis as well as the runtime compiler.
        val load: suspend (SnapshotStore, WhodunitDefinition, SessionId) -> Result<ResumedSession, DataError> =
            ::loadResumedSession
        val recovered = assertIs<Result.Success<ResumedSession>>(
            load(store(state, case), definition, sessionId),
        ).data
        assertEquals(state, recovered.state)
        assertEquals(Result.Success(Unit), validateResumedSessionForCase(recovered, case))
        assertPeerValidity(state, case, expected = true)
    }

    private fun assertPeerValidity(
        state: WhodunitState,
        case: ValidatedCase<WhodunitCase>,
        expected: Boolean,
    ) {
        val public = WhodunitProjectionPolicy.toPublic(state).state
        assertTrue(public.privatePerPlayer.isEmpty())
        assertTrue(public.hostOnly.seatToCharacter.isEmpty())
        players.forEach { player ->
            val ownPrivate = state.privatePerPlayer.getValue(player.id)
            assertEquals(expected, WhodunitStateValidator.isValidPeerProjection(public, ownPrivate, player.id))
            assertEquals(
                expected,
                WhodunitStateValidator.isValidPeerProjectionForCase(public, ownPrivate, player.id, case),
            )
        }
    }

    private fun step(
        state: WhodunitState,
        action: WhodunitAction,
        case: ValidatedCase<WhodunitCase>,
    ): WhodunitState = WhodunitReducer.reduce(
        state,
        action,
        WhodunitReducerContext(FakeClock(Instant.fromEpochMilliseconds(0)), RandomSource.seeded(SEED), case),
    ).newState.also { WhodunitStateValidator.requireValidForCase(it, case) }

    private fun assertFinalTwoRejected(block: () -> Unit) {
        val failure = assertFailsWith<IllegalArgumentException>(block = block)
        assertEquals("Active elimination phase has reached the final two", failure.message)
    }

    private companion object {
        const val SEED = 73L
    }
}

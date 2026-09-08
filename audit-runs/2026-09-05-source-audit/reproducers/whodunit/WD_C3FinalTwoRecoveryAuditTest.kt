package com.parlor.games.whodunit.audit

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
import com.parlor.games.whodunit.snapshot.WHODUNIT_SNAPSHOT_ENGINE_VERSION
import com.parlor.games.whodunit.snapshot.WhodunitSnapshotCodec
import com.parlor.games.whodunit.ui.flow.loadResumedSession
import com.parlor.games.whodunit.ui.flow.validateResumedSessionForCase
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Isolated audit source. Uses synthetic names/state, never real saves or peers. */
@OptIn(ExperimentalResourceApi::class)
class WD_C3FinalTwoRecoveryAuditTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
    private val definition = WhodunitDefinition(json)
    private val codec = WhodunitSnapshotCodec(json)
    private val players = (1..6).map { Player(PlayerId("audit-seat-$it"), "Audit Player $it", it - 1) }
    private val sessionId = SessionId("wd-c3-synthetic-session")
    private val seed = 73L

    @Test
    fun witness_current_recovery_accepts_state_then_extra_ballot_changes_winner_and_breaks_save() {
        runTest {
            val case = loadCase()
            val terminal = legalFinalTwo(case)
            val malformed = reopenTerminal(terminal)
            val identity = case.envelope.contentIdentity()
            val snapshot = GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = WHODUNIT_SNAPSHOT_ENGINE_VERSION,
                createdAt = Instant.fromEpochMilliseconds(0),
                phaseId = malformed.phase.id,
                payload = codec.encode(malformed),
                metadata = mapOf("playMode" to "PassAndPlay", "caseVersion" to identity.version,
                    "caseDigest" to identity.digest),
            )
            // This models an already-authenticated trusted producer, not a storage-authentication bypass.
            val syntheticStore = object : SnapshotStore {
                override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> = error("not used")
                override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> = Result.Success(snapshot)
                override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = error("not used")
                override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = Result.Success(listOf(sessionId))
            }
            val loaded = assertIs<Result.Success<com.parlor.games.whodunit.ui.flow.ResumedSession>>(
                loadResumedSession(syntheticStore, definition, sessionId),
            ).data
            assertEquals(malformed, loaded.state)
            assertEquals(Result.Success(Unit), validateResumedSessionForCase(loaded, case))
            val publicProjection = WhodunitProjectionPolicy.toPublic(malformed).state
            players.forEach { player ->
                assertTrue(WhodunitStateValidator.isValidPeerProjectionForCase(
                    publicProjection, malformed.privatePerPlayer[player.id], player.id, case,
                ))
            }
            var after = loaded.state
            val ballot = (after.public.voteState as VoteState.Collecting).ballotPlayerIds
            assertEquals(2, ballot.size)
            ballot.forEach { voter ->
                after = step(after, if (voter == after.hostOnly.killerId) {
                    WhodunitAction.AbstainVote(voter)
                } else {
                    WhodunitAction.CastVote(voter, after.hostOnly.killerId)
                }, case)
            }
            after = step(after, WhodunitAction.CloseVote, case, requireValid = false)
            assertIs<Verdict.PlayersWin>(after.public.verdict)
            assertEquals(5, after.public.eliminatedPlayers.size)
            val failure = assertFailsWith<IllegalArgumentException> { codec.encode(after) }
            assertEquals("Elimination history exceeds completed rounds", failure.message)
            println("WD-C3 synthetic witness: legal final-two killer win; current recovery accepted active two-seat ballot; extra vote became players win; fifth elimination rejected by codec")
        }
    }

    @Test
    fun current_snapshot_codec_should_reject_an_active_final_two_ballot() {
        runTest {
            val malformed = reopenTerminal(legalFinalTwo(loadCase()))
            assertFailsWith<IllegalArgumentException>("Current-schema recovery must reject reducer-impossible active final-two state") {
                codec.decode(codec.encode(malformed))
            }
        }
    }

    private suspend fun loadCase(): ValidatedCase<WhodunitCase> {
        val validator = DefaultCaseValidator(json, 1, SemVer(1, 0, 0), DefaultGameRegistry(listOf(definition)))
        return when (val result = validator.validate(
            Res.readBytes("files/cases/last-dinner.json").decodeToString(throwOnInvalidSequence = true),
            WhodunitPayloadValidator(json),
        )) {
            is Result.Success -> result.data
            is Result.Failure -> error("Bundled-case validation failed: ${result.error}")
        }
    }

    private fun legalFinalTwo(case: ValidatedCase<WhodunitCase>): WhodunitState {
        var state = definition.createInitialState(SessionConfig(
            sessionId, CaseId(case.envelope.caseId), WhodunitIds.EliminationModeId, players, seed,
        ))
        state = step(state, WhodunitAction.AssignRoles(seed), case)
        players.forEach { state = step(state, WhodunitAction.AcknowledgeIntro(it.id), case) }
        state = step(state, WhodunitAction.AdvanceFromIntro, case)
        players.forEach { state = step(state, WhodunitAction.AcknowledgeBriefing(it.id), case) }
        (1..4).forEach { state = step(state, WhodunitAction.AdvanceBriefingCard(it), case) }
        val generation = state.public.roleAssignmentGeneration
        players.forEach {
            state = step(state, WhodunitAction.StartCharacterReveal(it.id, generation), case)
            state = step(state, WhodunitAction.CompleteCharacterReveal(it.id, generation), case)
        }
        state = step(state, WhodunitAction.AdvanceFromCharacterReveal, case)
        (1..4).forEach { round ->
            assertEquals(WhodunitPhase.Round(round), state.phase)
            state = step(state, WhodunitAction.RevealNextClue, case)
            state = step(state, WhodunitAction.StartDiscussionTimer(
                WhodunitRoundPolicy.discussionSeconds(case.payload, round, players.size)), case)
            state = step(state, WhodunitAction.AdvanceFromDiscussion, case)
            val innocent = players.first {
                it.id != state.hostOnly.killerId && it.id !in state.public.eliminatedPlayers
            }.id
            (state.public.voteState as VoteState.Collecting).ballotPlayerIds.forEach { voter ->
                state = step(state, if (voter == innocent) WhodunitAction.AbstainVote(voter)
                    else WhodunitAction.CastVote(voter, innocent), case)
            }
            state = step(state, WhodunitAction.CloseVote, case)
            if (round < 4) state = step(state, WhodunitAction.AcknowledgeRevealCard, case)
        }
        assertEquals(WhodunitPhase.Reveal, state.phase)
        assertEquals(KillerWinCause.SurvivedToFinalTwo, assertIs<Verdict.KillerWins>(state.public.verdict).cause)
        assertEquals(4, state.public.eliminatedPlayers.size)
        assertEquals(state, codec.decode(codec.encode(state)))
        return state
    }

    private fun reopenTerminal(state: WhodunitState): WhodunitState {
        val survivors = players.map { it.id }.filterNot(state.public.eliminatedPlayers::contains)
        return state.copy(phase = WhodunitPhase.Round(4), public = state.public.copy(
            verdict = null,
            voteState = VoteState.Collecting(isElimination = true, ballotPlayerIds = survivors,
                candidatePlayerIds = survivors),
        ))
    }

    private fun step(state: WhodunitState, action: WhodunitAction,
        case: ValidatedCase<WhodunitCase>, requireValid: Boolean = true): WhodunitState {
        val result = WhodunitReducer.reduce(state, action, WhodunitReducerContext(
            FakeClock(Instant.fromEpochMilliseconds(0)), RandomSource.seeded(seed), case,
        )).newState
        if (requireValid) WhodunitStateValidator.requireValidForCase(result, case)
        return result
    }
}

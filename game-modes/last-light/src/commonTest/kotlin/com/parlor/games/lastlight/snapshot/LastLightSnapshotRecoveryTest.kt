package com.parlor.games.lastlight.snapshot

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.LastLightTestFixture
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant

class LastLightSnapshotRecoveryTest {
    private val fixture = LastLightTestFixture()

    @Test
    fun aVerifiedLocalSnapshotRestoresExactlyItsSessionAndCanonicalState() = runTest {
        val state = fixture.initial()
        val snapshot = snapshot(state)
        val result = LastLightSnapshotRecovery.load(Store(snapshot), fixture.definition, snapshot.sessionId)
        val restored = assertIs<Result.Success<ResumedLastLightSession>>(result).data
        assertEquals(snapshot.sessionId, restored.sessionId)
        assertEquals(state, restored.state)
    }

    @Test
    fun aSnapshotCannotChangeIdentityModeVersionOrClaimADifferentPhase() = runTest {
        val original = snapshot(fixture.initial())
        val invalid = listOf(
            original.copy(sessionId = SessionId("other-session")),
            original.copy(gameId = GameId("mafia")),
            original.copy(engineVersion = SemVer(0, 9, 0)),
            original.copy(engineVersion = SemVer(1, 0, 1)),
            original.copy(engineVersion = SemVer(2, 0, 0)),
            original.copy(metadata = emptyMap()),
            original.copy(metadata = mapOf("playMode" to "LocalP2P")),
            original.copy(metadata = original.metadata + ("unexpected" to "value")),
            original.copy(phaseId = "round-ended"),
            original.copy(payload = byteArrayOf()),
        )
        for (snapshot in invalid) {
            assertEquals(
                Result.Failure(DataError.CorruptedData),
                LastLightSnapshotRecovery.load(Store(snapshot), fixture.definition, original.sessionId),
            )
        }
    }

    @Test
    fun localRecoveryRefusesPausedMultiplayerAndAlreadyCompletedMatches() = runTest {
        val initial = fixture.initial()
        val paused = fixture.accepted(initial, LastLightAction.MarkPlayerDisconnected(initial.players[0].id))
        val completed = fixture.accepted(initial, LastLightAction.EndGame)
        val abandoned = fixture.accepted(paused, LastLightAction.ContinueWithoutPlayer(initial.players[0].id))
        for (state in listOf(paused, completed, abandoned)) {
            val snapshot = snapshot(state)
            assertEquals(
                Result.Failure(DataError.CorruptedData),
                LastLightSnapshotRecovery.load(Store(snapshot), fixture.definition, snapshot.sessionId),
            )
        }
    }

    @Test
    fun missingStorageErrorsRemainTypedAndCancellationIsNeverSwallowed() = runTest {
        val snapshot = snapshot(fixture.initial())
        val missing = Store(snapshot).apply { failure = DataError.NotFound }
        assertEquals(
            Result.Failure(DataError.NotFound),
            LastLightSnapshotRecovery.load(missing, fixture.definition, snapshot.sessionId),
        )
        val cancelled = Store(snapshot).apply { cancel = true }
        assertFailsWith<CancellationException> {
            LastLightSnapshotRecovery.load(cancelled, fixture.definition, snapshot.sessionId)
        }
    }

    private fun snapshot(state: LastLightState) = GameSnapshot(
        sessionId = fixture.config().sessionId,
        gameId = LastLightIds.GameId,
        engineVersion = LastLightSnapshotRecovery.VERSION,
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = state.phase.id,
        payload = fixture.definition.snapshotCodec().encode(state),
        metadata = mapOf(LastLightSnapshotRecovery.PLAY_MODE_KEY to LastLightSnapshotRecovery.PASS_AND_PLAY_MODE),
    )

    private class Store(var snapshot: GameSnapshot) : SnapshotStore {
        var failure: DataError? = null
        var cancel = false

        override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> {
            this.snapshot = snapshot
            return EmptyOk
        }

        override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> {
            if (cancel) throw CancellationException("test cancellation")
            return failure?.let { Result.Failure(it) } ?: Result.Success(snapshot)
        }

        override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = EmptyOk
        override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = Result.Success(listOf(snapshot.sessionId))
    }
}

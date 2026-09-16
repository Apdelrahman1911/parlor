package com.parlor.games.lastlight.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CancellationException

data class ResumedLastLightSession(val sessionId: SessionId, val state: LastLightState)

object LastLightSnapshotRecovery {
    val VERSION = SemVer(1, 0, 0)
    const val PLAY_MODE_KEY = "playMode"
    const val PASS_AND_PLAY_MODE = "PassAndPlay"

    suspend fun load(
        store: SnapshotStore,
        definition: LastLightDefinition,
        sessionId: SessionId,
    ): Result<ResumedLastLightSession, DataError> = try {
        when (val loaded = store.load(sessionId)) {
            is Result.Failure -> loaded
            is Result.Success -> recover(loaded.data, definition, sessionId)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        // A storage/serialization boundary: exception text may contain private
        // persisted content, so map failures without exposing that text.
        Result.Failure(DataError.CorruptedData)
    }

    private fun recover(
        snapshot: GameSnapshot,
        definition: LastLightDefinition,
        expectedSession: SessionId,
    ): Result<ResumedLastLightSession, DataError> {
        val metadata = mapOf(PLAY_MODE_KEY to PASS_AND_PLAY_MODE)
        if (snapshot.sessionId != expectedSession || snapshot.gameId != LastLightIds.GameId ||
            snapshot.engineVersion != VERSION || snapshot.metadata != metadata
        ) {
            return Result.Failure(DataError.CorruptedData)
        }
        val state = definition.snapshotCodec().decode(snapshot.payload)
        if (snapshot.phaseId != state.phase.id || state.phase == GamePhase.FINISHED ||
            state.public.disconnectedPlayers.isNotEmpty() || state.public.droppedPlayers.isNotEmpty()
        ) {
            return Result.Failure(DataError.CorruptedData)
        }
        return Result.Success(ResumedLastLightSession(snapshot.sessionId, state))
    }
}

package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot

/**
 * Persistence boundary for complete game-state snapshots, including
 * host-private state. Production implementations must use authenticated
 * encryption at rest plus the strongest practical platform file protection.
 *
 * The interface is fixed; the implementation may evolve without changes to
 * call sites.
 */
interface SnapshotStore {
    suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError>
    suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError>

    /**
     * Reads only the authenticated envelope fields needed for recovery
     * inventory. Implementations should avoid materialising the game payload;
     * the default keeps older test stores source-compatible.
     */
    suspend fun loadMetadata(sessionId: SessionId): Result<SnapshotMetadata, DataError> =
        when (val loaded = load(sessionId)) {
            is Result.Success -> Result.Success(
                SnapshotMetadata(
                    sessionId = loaded.data.sessionId,
                    gameId = loaded.data.gameId,
                ),
            )
            is Result.Failure -> loaded
        }

    suspend fun delete(sessionId: SessionId): EmptyResult<DataError>
    suspend fun listUnfinished(): Result<List<SessionId>, DataError>
}

/** Authenticated, non-private fields needed to render a recovery tile. */
data class SnapshotMetadata(
    val sessionId: SessionId,
    val gameId: GameId,
)

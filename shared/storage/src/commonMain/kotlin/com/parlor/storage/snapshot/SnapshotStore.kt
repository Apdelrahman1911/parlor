package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
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
    suspend fun delete(sessionId: SessionId): EmptyResult<DataError>
    suspend fun listUnfinished(): Result<List<SessionId>, DataError>
}

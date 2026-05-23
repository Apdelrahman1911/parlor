package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot

/**
 * Persistence boundary for game state snapshots. Phase 6 wires this in;
 * MVP backing is a simple per-session file under the platform's
 * documents directory. Production hardens with encrypted-at-rest storage via
 * platform keystore.
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

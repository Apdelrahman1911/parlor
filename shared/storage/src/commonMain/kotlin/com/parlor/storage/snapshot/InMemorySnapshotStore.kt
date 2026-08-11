package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Development/test in-memory snapshot store. Shipping mobile composition uses
 * platform-specific protected file storage; the interface remains identical
 * so deterministic tests exercise the same callers.
 */
class InMemorySnapshotStore : SnapshotStore {
    private val mutex = Mutex()
    private val byId: MutableMap<SessionId, GameSnapshot> = mutableMapOf()

    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> = mutex.withLock {
        byId[snapshot.sessionId] = snapshot
        EmptyOk
    }

    override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> = mutex.withLock {
        val snapshot = byId[sessionId] ?: return@withLock Result.Failure(DataError.NotFound)
        Result.Success(snapshot)
    }

    override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = mutex.withLock {
        byId.remove(sessionId)
        EmptyOk
    }

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = mutex.withLock {
        Result.Success(byId.keys.toList())
    }
}

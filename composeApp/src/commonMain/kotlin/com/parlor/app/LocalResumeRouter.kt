package com.parlor.app

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.storage.snapshot.SnapshotStore

internal enum class LocalResumeDestination {
    Whodunit,
    Mafia,
}

/**
 * Monotonic ownership gate for asynchronous Home-screen resume requests.
 * Cancelling a coroutine is cooperative, so an older request may finish a
 * non-suspending decode after a newer tap. Only the latest generation may
 * navigate or surface an error.
 */
internal class LocalResumeRequestGate {
    private var generation: Long = 0L

    fun begin(): Long = ++generation

    fun invalidate() {
        generation++
    }

    fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}

/** Resolves an authenticated snapshot envelope without decoding game bytes. */
internal suspend fun resolveLocalResumeDestination(
    store: SnapshotStore,
    sessionId: SessionId,
): Result<LocalResumeDestination, DataError> = when (val loaded = store.load(sessionId)) {
    is Result.Failure -> loaded
    is Result.Success -> {
        val snapshot = loaded.data
        if (snapshot.sessionId != sessionId) {
            Result.Failure(DataError.CorruptedData)
        } else {
            when (snapshot.gameId) {
                WhodunitIds.GameId -> Result.Success(LocalResumeDestination.Whodunit)
                MafiaIds.GameId -> Result.Success(LocalResumeDestination.Mafia)
                else -> Result.Failure(DataError.CorruptedData)
            }
        }
    }
}

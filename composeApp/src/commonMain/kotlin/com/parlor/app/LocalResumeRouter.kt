package com.parlor.app

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.storage.snapshot.SnapshotStore

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
    router: GameShellRouter,
    sessionId: SessionId,
): Result<GameShellLaunch.ResumeLocal, DataError> = when (val loaded = store.load(sessionId)) {
    is Result.Failure -> loaded
    is Result.Success -> {
        val snapshot = loaded.data
        if (snapshot.sessionId != sessionId) {
            Result.Failure(DataError.CorruptedData)
        } else {
            router.resumeLocal(snapshot.gameId, sessionId)
                ?.let { destination -> Result.Success(destination) }
                ?: Result.Failure(DataError.CorruptedData)
        }
    }
}

package com.parlor.app

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

/**
 * Owns the single local-resume operation allowed by the application shell.
 *
 * The coordinator keeps cancellation/generation bookkeeping out of Compose and
 * makes stale completion incapable of navigating after the player leaves the
 * recovery flow. It deliberately does not delete unreadable data automatically.
 */
internal class LocalResumeCoordinator(
    private val scope: CoroutineScope,
    private val store: SnapshotStore,
    private val router: GameShellRouter,
) {
    private val gate = LocalResumeRequestGate()
    private val mutableBusy = MutableStateFlow(false)
    private var activeJob: Job? = null

    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    fun request(
        sessionId: SessionId,
        currentScreen: () -> AppScreen,
        navigate: (AppScreen) -> Unit,
    ) {
        val origin = currentScreen()
        if (origin != AppScreen.Home && origin != AppScreen.LocalResumeFailure(sessionId)) return

        launchOperation(replaceActive = true) { requestGeneration ->
            val result = resolveLocalResumeDestination(store, router, sessionId)
            if (gate.isCurrent(requestGeneration) && currentScreen() == origin) {
                navigate(localResumeResultScreen(sessionId, result))
            }
        }
    }

    fun discard(
        sessionId: SessionId,
        currentScreen: () -> AppScreen,
        onDiscarded: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val origin = AppScreen.LocalResumeFailure(sessionId)
        if (currentScreen() != origin || activeJob != null) return

        launchOperation(replaceActive = false) { requestGeneration ->
            when (store.delete(sessionId)) {
                is Result.Success -> if (
                    gate.isCurrent(requestGeneration) && currentScreen() == origin
                ) {
                    onDiscarded()
                }

                is Result.Failure -> if (
                    gate.isCurrent(requestGeneration) && currentScreen() == origin
                ) {
                    onFailure()
                }
            }
        }
    }

    fun invalidate() {
        gate.invalidate()
        activeJob?.cancel()
        activeJob = null
        mutableBusy.value = false
    }

    private fun launchOperation(
        replaceActive: Boolean,
        operation: suspend (requestGeneration: Long) -> Unit,
    ) {
        if (!replaceActive && activeJob != null) return
        if (replaceActive) activeJob?.cancel()

        val requestGeneration = gate.begin()
        val request = scope.launch(start = CoroutineStart.LAZY) {
            try {
                operation(requestGeneration)
            } finally {
                finish(currentCoroutineContext()[Job])
            }
        }
        activeJob = request
        mutableBusy.value = true
        request.start()
    }

    private fun finish(completedJob: Job?) {
        if (activeJob === completedJob) {
            activeJob = null
            mutableBusy.value = false
        }
    }
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

/** Keeps an unreadable save addressable so the shell can offer explicit recovery. */
internal fun localResumeResultScreen(
    sessionId: SessionId,
    result: Result<GameShellLaunch.ResumeLocal, DataError>,
): AppScreen = when (result) {
    is Result.Success -> AppScreen.Game(result.data)
    is Result.Failure -> AppScreen.LocalResumeFailure(sessionId)
}

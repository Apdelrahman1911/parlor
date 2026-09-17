package com.parlor.app.shell.game.multiplayer

import com.parlor.engine.action.GameAction
import com.parlor.core.result.Result
import com.parlor.engine.session.SubmitError
import com.parlor.session.SubmissionReceipt
import com.parlor.session.multidevice.PeerCommandProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reserves a command before launching work, across recomposition and retained route recreation. */
internal class GameActionSubmission<A : GameAction>(
    private val scope: CoroutineScope,
    private val submit: suspend (A) -> Result<SubmissionReceipt, SubmitError>,
    private val commandProgress: StateFlow<PeerCommandProgress>? = null,
) {
    private class Attempt(val awaitingAuthority: Boolean = false)

    class Failure(val error: SubmitError)

    private val attempt = MutableStateFlow<Attempt?>(null)
    val pending = attempt.map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)
    private val _failure = MutableStateFlow<Failure?>(null)
    val failure = _failure.asStateFlow()

    init {
        if (commandProgress != null) {
            scope.launch {
                combine(attempt, commandProgress) { current, progress -> current to progress }
                    .collect { (current, progress) ->
                        if (current?.awaitingAuthority == true && progress is PeerCommandProgress.Idle) {
                            attempt.compareAndSet(current, null)
                        }
                    }
            }
        }
    }

    fun trySubmit(
        action: A,
        enabled: Boolean,
        stillAllowed: () -> Boolean = { true },
    ): Boolean {
        if (!enabled || !stillAllowed() || commandProgress?.value?.let { it !is PeerCommandProgress.Idle } == true) {
            return false
        }
        val submitted = Attempt()
        if (!attempt.compareAndSet(null, submitted)) return false
        _failure.value = null
        scope.launch {
            if (!stillAllowed()) {
                attempt.compareAndSet(submitted, null)
                return@launch
            }
            val result = try {
                submit(action)
            } catch (cancelled: CancellationException) {
                attempt.compareAndSet(submitted, null)
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // UI submission is an I/O translation boundary; exception text
                // may contain transport or private state and is never displayed.
                Result.Failure(SubmitError.SessionClosed)
            }
            when (result) {
                is Result.Failure -> {
                    _failure.value = Failure(result.error)
                    settle(submitted, commandProgress?.value?.let { it !is PeerCommandProgress.Idle } == true)
                }
                is Result.Success -> {
                    if (!result.data.awaitingAuthority) {
                        if (!result.data.stateChanged) {
                            _failure.value = Failure(SubmitError.IllegalForPhase)
                        }
                    }
                    settle(submitted, result.data.awaitingAuthority)
                }
            }
        }
        return true
    }

    fun acknowledgeFailure(failure: Failure) {
        _failure.compareAndSet(failure, null)
    }

    private fun settle(submitted: Attempt, awaitingAuthority: Boolean) {
        val next = if (awaitingAuthority) Attempt(true) else null
        attempt.compareAndSet(submitted, next)
    }
}

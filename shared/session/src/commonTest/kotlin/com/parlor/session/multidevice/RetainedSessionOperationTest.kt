package com.parlor.session.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedSessionOperationTest {
    @Test
    fun concurrentInstallersLaunchTheOperationExactlyOnce() = runTest {
        val retained = RetainedSessionOperation("waiting")
        val operationStarted = CompletableDeferred<Unit>()
        val releaseOperation = CompletableDeferred<Unit>()
        var calls = 0
        val operation: suspend () -> String = {
            calls++
            operationStarted.complete(Unit)
            releaseOperation.await()
            "started"
        }

        val first = async {
            retained.start(backgroundScope, { "failed" }, operation)
        }
        val second = async {
            retained.start(backgroundScope, { "failed" }, operation)
        }
        operationStarted.await()
        releaseOperation.complete(Unit)

        first.await()
        second.await()
        retained.state.first { it == "started" }
        assertThat(calls).isEqualTo(1)
        assertThat(retained.state.value).isEqualTo("started")
    }

    @Test
    fun losingTheUiCallerDoesNotCancelProcessOwnedWork() = runTest {
        val retained = RetainedSessionOperation("waiting")
        val operationStarted = CompletableDeferred<Unit>()
        val releaseOperation = CompletableDeferred<Unit>()

        val uiCaller = launch {
            retained.start(backgroundScope, { "failed" }) {
                operationStarted.complete(Unit)
                releaseOperation.await()
                "started"
            }
        }
        operationStarted.await()
        uiCaller.cancel()
        releaseOperation.complete(Unit)
        retained.state.first { it == "started" }

        assertThat(retained.state.value).isEqualTo("started")
    }

    @Test
    fun unexpectedFailureBecomesTheExplicitSafeState() = runTest {
        val retained = RetainedSessionOperation("waiting")

        retained.start(backgroundScope, { "failed:${it::class.simpleName}" }) {
            throw IllegalStateException("private detail")
        }
        retained.state.first { it != "waiting" }

        assertThat(retained.state.value).isEqualTo("failed:IllegalStateException")
    }

    @Test
    fun sessionCancellationIsNotMappedToAnOrdinaryFailure() = runTest {
        val retained = RetainedSessionOperation("waiting")
        val sessionJob = SupervisorJob()
        val sessionScope = CoroutineScope(coroutineContext + sessionJob)
        val operationStarted = CompletableDeferred<Unit>()
        var mappedFailures = 0

        retained.start(
            scope = sessionScope,
            onUnexpectedFailure = {
                mappedFailures++
                "failed"
            },
        ) {
            operationStarted.complete(Unit)
            CompletableDeferred<Unit>().await()
            "started"
        }
        operationStarted.await()
        sessionScope.cancel()
        runCurrent()

        assertThat(mappedFailures).isEqualTo(0)
        assertThat(retained.state.value).isEqualTo("waiting")
        assertThat(sessionJob.isCancelled).isEqualTo(true)
    }
}

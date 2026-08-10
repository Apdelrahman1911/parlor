package com.parlor.app.shell.settings

import com.parlor.storage.settings.SettingsPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMutationDispatcherTest {
    @Test
    fun mutationOutlivesItsCallingScreenAndReportsPersistenceFailure() = runTest {
        val failures = mutableListOf<SettingsPersistenceException>()
        val appJob = SupervisorJob(coroutineContext[Job])
        val appScope = CoroutineScope(coroutineContext + appJob)
        val callingScreen = Job(appJob)
        val started = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val dispatcher = SettingsMutationDispatcher(appScope, failures::add)
        var persisted = false

        val write = dispatcher.submit {
            started.complete(Unit)
            finishWrite.await()
            persisted = true
        }
        started.await()
        callingScreen.cancel()
        finishWrite.complete(Unit)
        write.join()
        val failedWrite = dispatcher.submit {
            throw SettingsPersistenceException("disk unavailable")
        }
        failedWrite.join()

        assertTrue(persisted)
        assertEquals(1, failures.size)
        assertEquals("disk unavailable", failures.single().message)
        appJob.cancel()
    }

    @Test
    fun ownerCancellationIsNotConvertedIntoPersistenceFailure() = runTest {
        val failures = mutableListOf<SettingsPersistenceException>()
        val dispatcher = SettingsMutationDispatcher(this, failures::add)
        val started = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()

        val job = dispatcher.submit {
            started.complete(Unit)
            neverCompletes.await()
        }
        started.await()
        job.cancel(CancellationException("app stopped"))
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(failures.isEmpty())
    }
}

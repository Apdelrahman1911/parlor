package com.parlor.transport.p2p

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Faults here are synthetic; passing does not establish mobile background execution time. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLifecycleFaultInjectionTest {
    @Test
    fun retentionFailureSuspendsImmediatelyAndReportsOnlyOnce() = runTest {
        val failure = IllegalStateException("injected retention failure")
        val observed = mutableListOf<Exception>()
        val room = FaultRoom(retainFailure = failure)
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, observed::add, elapsedMillis = { testScheduler.currentTime })
        coordinator.register("room", room)
        coordinator.backgrounded(0L)
        repeat(100) { coordinator.backgrounded(0L) }
        runCurrent()

        assertSame(failure, observed.single())
        assertEquals(listOf(0L to 0L), room.suspensions)
        assertEquals(1, room.retentions)
        coordinator.foregrounded(1L)
        assertEquals(listOf(1L), room.foregrounds)
        assertEquals(0, room.validations)
    }

    @Test
    fun validationFailureFallsBackWithoutExtendingOriginalRecoveryDeadline() = runTest {
        val failure = IllegalStateException("injected validation failure")
        val observed = mutableListOf<Exception>()
        val room = FaultRoom(validationFailure = failure)
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, observed::add, elapsedMillis = { testScheduler.currentTime })
        coordinator.register("room", room)
        coordinator.backgrounded(0L)
        advanceTimeBy(1_000L)
        coordinator.foregrounded(1_000L)
        advanceTimeBy(20_000L)
        runCurrent()

        assertSame(failure, observed.single())
        assertEquals(listOf(0L to 1_000L), room.suspensions)
        assertEquals(listOf(1_000L), room.foregrounds)
        assertEquals(1, room.validations)
    }

    @Test
    fun clockRollbackCannotValidateRetainedSocketOrCreateANewGraceWindow() = runTest {
        var now = 1_000L
        val room = FaultRoom()
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { now }, elapsedMillis = { testScheduler.currentTime })
        coordinator.register("room", room)
        coordinator.backgrounded(now)
        now = 999L
        coordinator.foregrounded(now)
        advanceTimeBy(20_000L)
        runCurrent()

        assertEquals(0, room.validations)
        assertEquals(listOf(1_000L to 1_000L), room.suspensions)
        assertEquals(listOf(1_000L), room.foregrounds)
        assertEquals(1, room.retentions)
    }

    @Test
    fun repeatedShortInterruptionsDoNotLeakOldExpiryJobsIntoTheActiveRoom() = runTest {
        val room = FaultRoom()
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        coordinator.register("room", room)
        repeat(100) {
            coordinator.backgrounded(testScheduler.currentTime)
            runCurrent()
            advanceTimeBy(1L)
            coordinator.foregrounded(testScheduler.currentTime)
        }
        advanceTimeBy(20_000L)
        runCurrent()

        assertEquals(100, room.retentions)
        assertEquals(100, room.validations)
        assertEquals(emptyList(), room.suspensions)
        assertEquals(emptyList(), room.foregrounds)
    }

    private class FaultRoom(
        private val retainFailure: Exception? = null,
        private val validationFailure: Exception? = null,
    ) : AppLifecycleAwareRoom, RetainedRoomConnection {
        override val health = MutableStateFlow(true)
        val suspensions = mutableListOf<Pair<Long, Long>>()
        val foregrounds = mutableListOf<Long>()
        var retentions = 0
        var validations = 0

        override suspend fun retainInBackground(atEpochMillis: Long, deadlineEpochMillis: Long): RetainedRoomConnection {
            retentions++
            retainFailure?.let { throw it }
            return this
        }

        override suspend fun resumeRetainedConnection(atEpochMillis: Long): Boolean {
            validations++
            validationFailure?.let { throw it }
            return true
        }

        override suspend fun appBackgrounded(atEpochMillis: Long) = appBackgrounded(atEpochMillis, atEpochMillis)

        override suspend fun appBackgrounded(atEpochMillis: Long, observedAtEpochMillis: Long) {
            suspensions += atEpochMillis to observedAtEpochMillis
        }

        override suspend fun appForegrounded(atEpochMillis: Long) {
            foregrounds += atEpochMillis
        }
    }
}

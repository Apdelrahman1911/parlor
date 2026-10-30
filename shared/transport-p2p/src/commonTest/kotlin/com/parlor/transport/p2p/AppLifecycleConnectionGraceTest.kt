package com.parlor.transport.p2p

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppLifecycleConnectionGraceTest {
    @Test
    fun shortReturnValidatesWithoutSuspendingAndCancelsOldTimer() = runTest {
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        runCurrent()
        assertEquals(emptyList(), room.suspensions)
        advanceTimeBy(1_000L)
        coordinator.foregrounded(1_000L)
        assertEquals(1, room.validations)
        assertEquals(emptyList(), room.foregrounds)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(emptyList(), room.suspensions)
    }

    @Test
    fun fifteenSecondDeadlineIsNotExtendedByDuplicateBackground() = runTest {
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        advanceTimeBy(10_000L)
        coordinator.backgrounded(10_000L)
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(emptyList(), room.suspensions)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(0L to 15_000L), room.suspensions)
        assertEquals(1, room.retentions)
    }

    @Test
    fun knownLossSuspendsImmediatelyNotAtTheGraceDeadline() = runTest {
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        advanceTimeBy(700L)
        room.health.value = false
        runCurrent()
        assertEquals(listOf(0L to 700L), room.suspensions)
        coordinator.foregrounded(700L)
        assertEquals(0, room.validations)
        assertEquals(listOf(700L), room.foregrounds)
    }

    @Test
    fun elapsedDeadlineIsCheckedWhenTheSchedulerNeverRanWhileAsleep() = runTest {
        var wallTime = 0L
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { wallTime }, elapsedMillis = { testScheduler.currentTime })
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        wallTime = 130_000L
        coordinator.foregrounded(wallTime)
        assertEquals(0, room.validations)
        assertEquals(listOf(0L to 130_000L), room.suspensions)
        assertEquals(listOf(130_000L), room.foregrounds)
    }

    @Test
    fun replacementGetsNoFreshGraceAndOldTimerCannotSuspendIt() = runTest {
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        val old = GraceRoom()
        val replacement = GraceRoom()
        coordinator.register("one", old)
        coordinator.backgrounded(0L)
        advanceTimeBy(1_000L)
        coordinator.register("two", replacement)
        coordinator.roomClosed("one")
        assertEquals(listOf(0L to 1_000L), old.suspensions)
        assertEquals(listOf(0L to 1_000L), replacement.suspensions)
        assertEquals(0, replacement.retentions)
        coordinator.foregrounded(1_000L)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(1, replacement.suspensions.size)
        assertEquals(listOf(1_000L), replacement.foregrounds)
        assertEquals(emptyList(), old.foregrounds)
    }

    @Test
    fun failedValidationUsesOriginalBackgroundTimeForHardRecovery() = runTest {
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        val room = GraceRoom(validates = false)
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        advanceTimeBy(1_000L)
        coordinator.foregrounded(1_000L)
        assertEquals(listOf(0L to 1_000L), room.suspensions)
        assertEquals(listOf(1_000L), room.foregrounds)
    }

    @Test
    fun closingRoomCancelsRetentionAndNeverRevivesIt() = runTest {
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime })
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        coordinator.roomClosed("one")
        advanceTimeBy(20_000L)
        runCurrent()
        coordinator.foregrounded(20_000L)
        assertEquals(emptyList(), room.suspensions)
        assertEquals(emptyList(), room.foregrounds)
        assertEquals(0, room.validations)
    }

    @Test
    fun elapsedDeadlineRejectsRetainedValidationBeforeThePendingTimerRuns() = runTest {
        var elapsed = 0L
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { 0L }, elapsedMillis = { elapsed })
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        elapsed = 15_000L
        coordinator.foregrounded(0L)
        assertEquals(0, room.validations)
        assertEquals(listOf(0L to 15_000L), room.suspensions)
        assertEquals(listOf(15_000L), room.foregrounds)
    }

    @Test
    fun delayedTimerStartsWithOnlyTheOriginalElapsedRemainder() = runTest {
        var offset = 0L
        val coordinator = AppLifecycleRoomCoordinator(
            backgroundScope, { testScheduler.currentTime }, elapsedMillis = { testScheduler.currentTime + offset },
        )
        val room = GraceRoom()
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        offset = 10_000L
        runCurrent()
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(emptyList(), room.suspensions)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(0L to 15_000L), room.suspensions)
    }

    @Test
    fun aSlowRetentionCallCannotStartAFreshTimerAfterItsElapsedBudget() = runTest {
        var elapsed = 0L
        val coordinator = AppLifecycleRoomCoordinator(backgroundScope, { 0L }, elapsedMillis = { elapsed })
        val room = GraceRoom(onRetain = { elapsed = 20_000L })
        coordinator.register("one", room)
        coordinator.backgrounded(0L)
        assertEquals(listOf(0L to 20_000L), room.suspensions)
        coordinator.foregrounded(0L)
        assertEquals(0, room.validations)
        assertEquals(listOf(0L), room.foregrounds)
    }

    private class GraceRoom(
        private val validates: Boolean = true,
        private val onRetain: () -> Unit = {},
    ) : AppLifecycleAwareRoom, RetainedRoomConnection {
        override val health = MutableStateFlow(true)
        val suspensions = mutableListOf<Pair<Long, Long>>()
        val foregrounds = mutableListOf<Long>()
        var retentions = 0
        var validations = 0

        override suspend fun retainInBackground(atEpochMillis: Long, deadlineEpochMillis: Long): RetainedRoomConnection {
            retentions++
            onRetain()
            return this
        }

        override suspend fun resumeRetainedConnection(atEpochMillis: Long): Boolean {
            validations++
            return validates
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

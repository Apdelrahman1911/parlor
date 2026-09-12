package com.parlor.transport.p2p

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppLifecycleRoomRegistrationTest {
    @Test
    fun cancelledRegistrationDetachesBeforeQueuedForeground() = runTest {
        val coordinator = AppLifecycleRoomCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val abandoned = RegistrationRoom {
            withContext(NonCancellable) {
                entered.complete(Unit)
                release.await()
            }
        }
        coordinator.backgrounded(100L)
        val registration = async { coordinator.register("abandoned", abandoned) }
        entered.await()
        val foreground = async { coordinator.foregrounded(200L) }
        try {
            runCurrent()
            assertFalse(foreground.isCompleted)
            registration.cancel()
            release.complete(Unit)
            assertFailsWith<CancellationException> { registration.await() }
            foreground.await()
            assertEquals(0, abandoned.foregroundCalls)

            val replacement = RegistrationRoom()
            coordinator.register("replacement", replacement)
            coordinator.roomClosed("abandoned")
            coordinator.backgrounded(300L)
            assertEquals(1, replacement.backgroundCalls)
            assertEquals(1, abandoned.backgroundCalls)
        } finally {
            release.complete(Unit)
            registration.cancelAndJoin()
            foreground.cancelAndJoin()
        }
    }

    @Test
    fun failedRegistrationDetachesBeforeQueuedForeground() = runTest {
        supervisorScope {
            val coordinator = AppLifecycleRoomCoordinator()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val abandoned = RegistrationRoom {
                entered.complete(Unit)
                release.await()
                error("synthetic lifecycle registration failure")
            }
            coordinator.backgrounded(100L)
            val registration = async { coordinator.register("abandoned", abandoned) }
            entered.await()
            val foreground = async { coordinator.foregrounded(200L) }
            try {
                runCurrent()
                assertFalse(foreground.isCompleted)
                release.complete(Unit)
                assertFailsWith<IllegalStateException> { registration.await() }
                foreground.await()
                assertEquals(0, abandoned.foregroundCalls)
                coordinator.backgrounded(300L)
                assertEquals(1, abandoned.backgroundCalls)
            } finally {
                release.complete(Unit)
                registration.cancelAndJoin()
                foreground.cancelAndJoin()
            }
        }
    }

    @Test
    fun cancellationBeforeRegistrationLockDoesNotDetachExistingRoom() = runTest {
        val coordinator = AppLifecycleRoomCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val existing = RegistrationRoom {
            entered.complete(Unit)
            release.await()
        }
        val unopened = RegistrationRoom()
        coordinator.register("existing", existing)
        val background = async { coordinator.backgrounded(100L) }
        entered.await()
        val registration = async { coordinator.register("unopened", unopened) }
        try {
            runCurrent()
            assertFalse(registration.isCompleted)
            registration.cancelAndJoin()
            release.complete(Unit)
            background.await()
            coordinator.foregrounded(200L)
            assertTrue(registration.isCancelled)
            assertEquals(1, existing.foregroundCalls)
            assertEquals(0, unopened.backgroundCalls)
            assertEquals(0, unopened.foregroundCalls)
        } finally {
            release.complete(Unit)
            registration.cancelAndJoin()
            background.cancelAndJoin()
        }
    }

    private class RegistrationRoom(
        private val onBackground: suspend () -> Unit = {},
    ) : AppLifecycleAwareRoom {
        var backgroundCalls = 0
            private set
        var foregroundCalls = 0
            private set

        override suspend fun appBackgrounded(atEpochMillis: Long) {
            backgroundCalls++
            onBackground()
        }

        override suspend fun appForegrounded(atEpochMillis: Long) {
            foregroundCalls++
        }
    }
}

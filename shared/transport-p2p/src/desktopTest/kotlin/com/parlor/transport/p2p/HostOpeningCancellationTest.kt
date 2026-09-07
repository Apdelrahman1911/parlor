package com.parlor.transport.p2p

import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.transport.HostConfig
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.PeerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HostOpeningCancellationTest {
    @Test
    fun cancelledRegistrationClosesRoomBeforeQueuedForegroundCanReviveIt() = runTest {
        val fake = FakeP2pKit(PeerId("cancelled-host"))
        val replacementKit = FakeP2pKit(PeerId("replacement-host"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stopAttempts = 0
        val kit = object : P2pKit by fake {
            override suspend fun stopAdvertising() {
                stopAttempts++
                if (stopAttempts == 1) {
                    // rc3 awaits its feature cleanup NonCancellable as well.
                    withContext(NonCancellable) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                fake.stopAdvertising()
            }
        }
        val transport = newTransport(kit, replacementKit)
        transport.notifyAppBackgrounded()
        runCurrent()
        val opening = async { transport.host(HostConfig("Host")) }
        entered.await()
        try {
            assertEquals(1, fake.incomingSessionsFlow.subscriptionCount.value)
            transport.notifyAppForegrounded()
            runCurrent()
            opening.cancel()
            release.complete(Unit)
            runCurrent()
            assertEquals(0, fake.foregroundCalls)
            assertEquals(1, fake.startAdvertisingCalls)
            opening.join()
            assertTrue(opening.isCancelled)
            assertEquals(1, fake.stopCalls)
            assertEquals(0, fake.incomingSessionsFlow.subscriptionCount.value)

            val replacement = assertIs<Result.Success<LocalRoom>>(
                transport.host(HostConfig("Replacement")),
            ).data
            try {
                transport.notifyAppBackgrounded()
                runCurrent()
                assertEquals(1, replacementKit.backgroundCalls)
                assertEquals(1, fake.backgroundCalls)
                transport.notifyAppForegrounded()
                runCurrent()
                assertEquals(1, replacementKit.foregroundCalls)
                assertEquals(0, fake.foregroundCalls)
            } finally {
                replacement.leave()
            }
            assertEquals(1, replacementKit.stopCalls)
        } finally {
            release.complete(Unit)
            opening.cancelAndJoin()
            // Fakes own no native resources; ensure red runs also finish cleanly.
            if (fake.stopCalls == 0) fake.stop()
        }
    }

    @Test
    fun registrationFailureClosesUnreturnedRoomAndDetachesLifecycle() = runTest {
        val fake = FakeP2pKit(PeerId("failed-host"))
        val kit = object : P2pKit by fake {
            override fun notifyAppBackgrounded() {
                fake.notifyAppBackgrounded()
                error("synthetic background registration failure")
            }
        }
        val transport = newTransport(kit)
        transport.notifyAppBackgrounded()
        runCurrent()
        try {
            val result = transport.host(HostConfig("Host"))
            assertIs<NetError.TransportFailure>(assertIs<Result.Failure<NetError>>(result).error)
            assertEquals(1, fake.stopCalls)
            assertEquals(0, fake.incomingSessionsFlow.subscriptionCount.value)
            transport.notifyAppForegrounded()
            runCurrent()
            assertEquals(0, fake.foregroundCalls)
            assertEquals(1, fake.startAdvertisingCalls)
        } finally {
            if (fake.stopCalls == 0) fake.stop()
        }
    }

    @Test
    fun cancellationDuringKitStartStopsUnownedKit() = runTest {
        verifyEarlyCancellation(duringAdvertising = false)
    }

    @Test
    fun cancellationDuringAdvertisingClosesArmedRoom() = runTest {
        verifyEarlyCancellation(duringAdvertising = true)
    }

    @Test
    fun returnedHostRetainsOwnershipUntilIdempotentLeave() = runTest {
        val kit = FakeP2pKit(PeerId("owned-host"))
        val transport = newTransport(kit)
        val room = assertIs<Result.Success<LocalRoom>>(transport.host(HostConfig("Host"))).data
        try {
            assertEquals(0, kit.stopCalls)
            assertEquals(1, kit.incomingSessionsFlow.subscriptionCount.value)
        } finally {
            room.leave()
            room.leave()
        }
        assertEquals(1, kit.stopCalls)
        assertEquals(0, kit.incomingSessionsFlow.subscriptionCount.value)
        assertEquals(RoomLifecycleState.Closed, room.lifecycle.value)
        transport.notifyAppBackgrounded()
        transport.notifyAppForegrounded()
        runCurrent()
        assertEquals(0, kit.backgroundCalls)
        assertEquals(0, kit.foregroundCalls)
    }

    private suspend fun TestScope.verifyEarlyCancellation(duringAdvertising: Boolean) {
        val kit = FakeP2pKit(PeerId("early-cancelled-host"))
        val entered = CompletableDeferred<Unit>()
        val pause: suspend () -> Unit = {
            entered.complete(Unit)
            awaitCancellation()
        }
        if (duringAdvertising) kit.startAdvertisingHandler = pause else kit.startHandler = pause
        val transport = newTransport(kit)
        val opening = async { transport.host(HostConfig("Host")) }
        entered.await()
        try {
            assertEquals(
                if (duringAdvertising) 1 else 0,
                kit.incomingSessionsFlow.subscriptionCount.value,
            )
            opening.cancelAndJoin()
            assertTrue(opening.isCancelled)
            assertEquals(1, kit.stopCalls)
            assertEquals(0, kit.incomingSessionsFlow.subscriptionCount.value)
        } finally {
            opening.cancelAndJoin()
            if (kit.stopCalls == 0) kit.stop()
        }
    }

    private fun TestScope.newTransport(vararg kits: P2pKit): P2pKitRoomTransport {
        val remaining = kits.iterator()
        return P2pKitRoomTransport(
            appId = AppId("com.parlor.test"),
            deviceName = "synthetic-host",
            scope = backgroundScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
                    remaining.next()
            },
        )
    }
}

package com.parlor.transport.p2p

import com.parlor.networking.transport.HostConfig
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.PeerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit-only reproducer. Requires the existing transport desktopTest FakeP2pKit. */
@OptIn(ExperimentalCoroutinesApi::class)
class SN_C2HostRegistrationCancellationTest {
    @Test
    fun cancelled_host_registration_closes_the_unreturned_room() = runTest {
        val fake = FakeP2pKit(PeerId("audit-host"))
        val stopStarted = CompletableDeferred<Unit>()
        val allowStopCompletion = CompletableDeferred<Unit>()
        var stopAdvertisingAttempts = 0
        val kit = object : P2pKit by fake {
            override suspend fun stopAdvertising() {
                stopAdvertisingAttempts += 1
                if (stopAdvertisingAttempts == 1) {
                    // rc3's stopFeature performs resource cleanup NonCancellable.
                    // Do not make the reproducer depend on aborting that cleanup.
                    withContext(NonCancellable) {
                        stopStarted.complete(Unit)
                        allowStopCompletion.await()
                    }
                }
                fake.stopAdvertising()
            }
        }
        val transport = P2pKitRoomTransport(
            appId = AppId("com.parlor.audit"),
            deviceName = "audit-host",
            scope = backgroundScope,
            kitFactory = object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
        )

        transport.notifyAppBackgrounded()
        runCurrent()
        val opening = async { transport.host(HostConfig("Audit host")) }
        stopStarted.await()
        assertEquals(1, fake.startAdvertisingCalls)
        assertEquals(1, fake.incomingSessionsFlow.subscriptionCount.value)

        try {
            opening.cancel()
            allowStopCompletion.complete(Unit)
            opening.join()
            runCurrent()
            assertTrue(opening.isCancelled)
            assertEquals(
                1,
                fake.stopCalls,
                "A host cancelled before returning ownership must stop its kit",
            )
            assertEquals(0, fake.incomingSessionsFlow.subscriptionCount.value)
        } finally {
            allowStopCompletion.complete(Unit)
            opening.cancelAndJoin()
            // The fake has no native resources. Explicitly end it on failure;
            // runTest then cancels every remaining backgroundScope collector.
            if (fake.stopCalls == 0) fake.stop()
        }
    }
}

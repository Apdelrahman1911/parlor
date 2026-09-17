package com.parlor.transport.p2p

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.room.ForegroundConnectionValidator
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundConnectionRetentionTest {
    @Test
    fun hostRetainsOnlyEstablishedMatchSocketsAndStopsAdmissionsImmediately() = runTest {
        val fixture = Fixture(this, host = true)
        fixture.open()
        fixture.background()
        assertEquals(RoomLifecycleState.Active, fixture.room.lifecycle.value)
        assertTrue(fixture.room.members.value.all { it.connected })
        assertFalse(fixture.room.foregroundReady.value)
        assertFalse(fixture.room.acceptsLocalGameCommands)
        assertTrue(fixture.room.acceptsRemoteGameCommands)
        assertEquals(0, fixture.session.closeCalls)
        assertEquals(0, fixture.kit.backgroundCalls)
        assertEquals(1, fixture.kit.stopAdvertisingCalls)
        val late = FakeP2pSession(peer("late", "Late"))
        fixture.kit.incomingSessionsFlow.emit(late)
        runCurrent()
        advanceTimeBy(P2pKitRoomTransport.ADMISSION_REJECTION_FLUSH_MS)
        runCurrent()
        assertEquals(ConnectionState.Closed, late.state.value)
        assertEquals(Result.Failure(NetError.SessionSuspended), fixture.room.closeAdmissions())
        advanceTimeBy(1_000L)
        fixture.foreground()
        assertEquals(1, fixture.validator.calls)
        assertTrue(fixture.room.foregroundReady.value)
        assertTrue(fixture.room.acceptsLocalGameCommands)
        assertEquals(0, fixture.session.closeCalls)
        assertEquals(1, fixture.kit.startAdvertisingCalls)
        fixture.room.leave()
    }

    @Test
    fun peerKeepsHealthySocketButBlocksLocalInputUntilRoundTripFinishes() = runTest {
        val fixture = Fixture(this)
        fixture.open()
        fixture.background()
        fixture.validator.response = CompletableDeferred()
        advanceTimeBy(1_000L)
        val returning = async { fixture.foreground() }
        runCurrent()
        assertFalse(fixture.room.foregroundReady.value)
        assertFalse(fixture.room.acceptsLocalGameCommands)
        assertEquals(ConnectionState.Connected, fixture.session.state.value)
        fixture.validator.response?.complete(true)
        returning.await()
        assertTrue(fixture.room.foregroundReady.value)
        assertEquals(0, fixture.kit.backgroundCalls)
        assertEquals(0, fixture.session.closeCalls)
        fixture.room.leave()
    }

    @Test
    fun noValidatorOrUnstartedLobbyFallsBackToImmediateSuspension() = runTest {
        val fixture = Fixture(this, host = true)
        fixture.open(closeAdmissions = false)
        fixture.background()
        assertIs<RoomLifecycleState.Suspended>(fixture.room.lifecycle.value)
        assertEquals(1, fixture.session.closeCalls)
        fixture.room.leave()

        val peer = Fixture(this)
        peer.open(registerValidator = false)
        peer.background()
        assertIs<RoomLifecycleState.Suspended>(peer.room.lifecycle.value)
        assertEquals(1, peer.session.closeCalls)
        peer.room.leave()
    }

    @Test
    fun graceExpiryStillExpiresAtOriginalOneHundredTwentySecondDeadline() = runTest {
        val fixture = Fixture(this)
        fixture.open()
        fixture.background()
        advanceTimeBy(14_999L)
        runCurrent()
        assertEquals(RoomLifecycleState.Active, fixture.room.lifecycle.value)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(RoomLifecycleState.Suspended(120_000L), fixture.room.lifecycle.value)
        assertEquals(1, fixture.session.closeCalls)
        advanceTimeBy(104_999L)
        runCurrent()
        assertIs<RoomLifecycleState.Suspended>(fixture.room.lifecycle.value)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(RoomLifecycleState.Expired, fixture.room.lifecycle.value)
        fixture.foreground()
        assertEquals(RoomLifecycleState.Expired, fixture.room.lifecycle.value)
        assertEquals(0, fixture.validator.calls)
        assertFalse(fixture.room.acceptsLocalGameCommands)
    }

    @Test
    fun knownLossDuringGraceIsImmediateAndOldSocketCannotReturn() = runTest {
        val fixture = Fixture(this)
        fixture.open()
        fixture.background()
        advanceTimeBy(500L)
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        assertEquals(RoomLifecycleState.Suspended(120_000L), fixture.room.lifecycle.value)
        assertFalse(fixture.room.members.value.single().connected)
        fixture.session.stateFlow.value = ConnectionState.Connected
        runCurrent()
        fixture.foreground()
        assertIs<RoomLifecycleState.Resuming>(fixture.room.lifecycle.value)
        assertFalse(fixture.room.acceptsLocalGameCommands)
        assertEquals(0, fixture.validator.calls)
        fixture.room.leave()
    }

    @Test
    fun wallClockExpiryBlocksHostCommandsBeforeDelayedTimerRuns() = runTest {
        val fixture = Fixture(this, host = true)
        fixture.open()
        fixture.background()
        fixture.wallOffset = 16_000L
        assertFalse(fixture.room.acceptsRemoteGameCommands)
        fixture.foreground()
        assertEquals(RoomLifecycleState.Resuming(120_000L), fixture.room.lifecycle.value)
        assertEquals(1, fixture.session.closeCalls)
        assertEquals(0, fixture.validator.calls)
        fixture.room.leave()
    }

    @Test
    fun validationTimeoutDoesNotTreatCachedConnectedAsLiveness() = runTest {
        val fixture = Fixture(this)
        fixture.open()
        fixture.background()
        advanceTimeBy(1_000L)
        fixture.validator.response = CompletableDeferred()
        val returning = async { fixture.foreground() }
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()
        returning.await()
        assertEquals(RoomLifecycleState.Resuming(120_000L), fixture.room.lifecycle.value)
        assertEquals(1, fixture.session.closeCalls)
        fixture.validator.response?.complete(true)
        runCurrent()
        assertFalse(fixture.room.acceptsLocalGameCommands)
        fixture.room.leave()
    }

    @Test
    fun explicitLeaveDuringValidationCannotReviveTheRoom() = runTest {
        val fixture = Fixture(this)
        fixture.open()
        fixture.background()
        fixture.validator.response = CompletableDeferred()
        val returning = async { fixture.foreground() }
        runCurrent()
        fixture.room.finalLeave()
        fixture.validator.response?.complete(true)
        returning.await()
        assertEquals(RoomLifecycleState.Closed, fixture.room.lifecycle.value)
        assertFalse(fixture.room.acceptsLocalGameCommands)
        assertEquals(1, fixture.kit.stopCalls)
    }

    @Test
    fun hostLeaveDuringAdvertisingRestartCannotLeaveAGhostAdvertisement() = runTest {
        val fixture = Fixture(this, host = true)
        fixture.open()
        fixture.background()
        val restarting = CompletableDeferred<Unit>()
        val finishRestart = CompletableDeferred<Unit>()
        fixture.kit.startAdvertisingHandler = {
            restarting.complete(Unit)
            finishRestart.await()
        }
        val returning = async { fixture.foreground() }
        runCurrent()
        assertTrue(restarting.isCompleted)
        val leaving = async { fixture.room.finalLeave() }
        runCurrent()
        finishRestart.complete(Unit)
        leaving.await()
        returning.await()
        assertEquals(RoomLifecycleState.Closed, fixture.room.lifecycle.value)
        assertFalse(fixture.room.foregroundReady.value)
        assertFalse(fixture.room.acceptsLocalGameCommands)
        assertEquals(1, fixture.kit.stopCalls)
        assertEquals(3, fixture.kit.stopAdvertisingCalls)
        assertEquals("stop", fixture.kit.callLog.last())
    }

    @Test
    fun hostLeaveAlreadyInFlightCannotFallBackIntoSecureResume() = runTest {
        val fixture = Fixture(this, host = true)
        fixture.open()
        fixture.background()
        fixture.validator.response = CompletableDeferred()
        val returning = async { fixture.foreground() }
        runCurrent()
        val leaving = async { fixture.room.finalLeave() }
        runCurrent()
        fixture.validator.response?.complete(true)
        runCurrent()
        assertFalse(fixture.room.foregroundReady.value)
        assertEquals(0, fixture.kit.startAdvertisingCalls)
        assertEquals(0, fixture.kit.foregroundCalls)
        assertEquals(0, fixture.kit.backgroundCalls)
        leaving.await()
        returning.await()
        assertEquals(RoomLifecycleState.Closed, fixture.room.lifecycle.value)
        assertEquals(1, fixture.kit.stopCalls)
    }

    @Test
    fun foregroundPreparationSharesTheBoundedValidationBudget() = runTest {
        val session = FakeP2pSession(peer("host", "Host"))
        val retention = BackgroundConnectionRetention { testScheduler.currentTime }
        retention.register(Validator())
        assertNotNull(retention.retain(0L, 15_000L, listOf(session)))
        val returning = async { retention.resume(0L) { CompletableDeferred<Unit>().await() } }
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()
        assertFalse(returning.await())
        assertFalse(retention.foregroundReady.value)
        assertFalse(retention.acceptsLocalCommands)
    }

    @Test
    fun eachValidatorRegistrationOwnsADistinctTokenEvenWhenTheValidatorIsReused() = runTest {
        val session = FakeP2pSession(peer("host", "Host"))
        val retention = BackgroundConnectionRetention { 0L }
        val validator = Validator()
        val detachOld = retention.register(validator)
        assertNotNull(retention.retain(0L, 15_000L, listOf(session)))
        val detachNew = retention.register(validator)
        detachOld()
        assertFalse(retention.resume(0L))
        assertNotNull(retention.retain(0L, 15_000L, listOf(session)))
        assertTrue(retention.resume(0L))
        detachNew()
        assertNull(retention.retain(0L, 15_000L, listOf(session)))
    }

    @Test
    fun validatorReplacementAndClockRollbackInvalidateRetainedWindow() = runTest {
        val session = FakeP2pSession(peer("host", "Host"))
        var now = 100L
        val retention = BackgroundConnectionRetention { now }
        val old = Validator()
        val detachOld = retention.register(old)
        assertNotNull(retention.retain(100L, 15_100L, listOf(session)))
        retention.register(Validator())
        detachOld()
        assertFalse(retention.resume(100L))
        assertNotNull(retention.retain(100L, 15_100L, listOf(session)))
        now = 99L
        assertFalse(retention.acceptsRemoteCommands)
        assertFalse(retention.resume(now))
        assertNull(retention.retain(100L, 15_100L, listOf(session)))
    }

    private class Validator : ForegroundConnectionValidator {
        override val ready = true
        var calls = 0
        var response: CompletableDeferred<Boolean>? = null
        override suspend fun validate(): Boolean {
            calls++
            return response?.await() ?: true
        }
    }

    private class Fixture(private val test: TestScope, private val host: Boolean = false) {
        val kit = FakeP2pKit(PeerId(if (host) "host" else "peer"))
        val session = FakeP2pSession(peer(if (host) "peer" else "host", if (host) "Guest" else "Host"))
        private val codec = RoomMessageCodec()
        val validator = Validator()
        var wallOffset = 0L
        private val now get() = test.testScheduler.currentTime + wallOffset
        private val coordinator = AppLifecycleRoomCoordinator(test.backgroundScope, { now })
        val room: LocalRoom = if (host) {
            HostP2pRoom(
                kit, "ABCDEF", "Host", PlayerId("host"), 5,
                scope = test.backgroundScope, codec = codec, currentTimeMillis = { now },
            )
        } else {
            PeerP2pRoom(
                kit, session, session.peer, "ABCDEF", test.backgroundScope, codec,
                hostDisplayName = "Host", currentTimeMillis = { now },
            )
        }

        suspend fun open(closeAdmissions: Boolean = true, registerValidator: Boolean = true) {
            if (host) {
                kit.incomingSessionsFlow.emit(session)
                test.runCurrent()
                session.incomingFlow.emit(
                    P2pMessage.Binary(codec.encode(PeerMessage.AdmissionRequest(
                        ProtocolVersion(), PlayerId("peer"), "ABCDEF", "Guest",
                    ))),
                )
                test.runCurrent()
                val admission = test.async { room.approveAdmission(PlayerId("peer")) }
                test.runCurrent()
                assertIs<Result.Success<Unit>>(admission.await())
                if (closeAdmissions) assertIs<Result.Success<*>>(room.closeAdmissions())
            }
            if (registerValidator) room.registerForegroundValidator(validator)
            coordinator.register("fixture", room as AppLifecycleAwareRoom)
            test.runCurrent()
        }

        suspend fun background() {
            coordinator.backgrounded(now)
            test.runCurrent()
        }

        suspend fun foreground() = coordinator.foregrounded(now)
    }
}

private fun peer(id: String, name: String) = Peer(
    PeerId(id), name, Platform.UNKNOWN, setOf(TransportKind.LAN),
)

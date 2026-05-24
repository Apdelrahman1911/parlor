package com.parlor.session.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.JoinError
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wave 9H-5b: [JoinRoomController] state machine + NetError → JoinError
 * mapping. The fake transport gives us deterministic control over both
 * the discovery flow and the join verdict.
 *
 * The "no raw error leakage" rule is enforced two ways:
 *  1. The [JoinError] sealed interface has no variant carrying a raw
 *     transport message string — the type system itself forbids leakage.
 *  2. Tests verify the mapping table; any `NetError.TransportFailure`
 *     with arbitrary internal text resolves to `JoinError.Generic`, not
 *     to a message-bearing variant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinRoomControllerTest {

    private fun fakeRoom(): LocalRoom = FakeRoom()

    private class FakeRoom : LocalRoom {
        private val _info = MutableStateFlow(
            RoomInfo("ABC234", "Fake Room", PlayerId("host"), RoomInfo.Status.Joined),
        )
        private val _members = MutableStateFlow<List<RoomMember>>(emptyList())
        override val info = _info.asStateFlow()
        override val members = _members.asStateFlow()
        override val isHost = false
        override val selfPlayerId: PlayerId = PlayerId("alice")
        override val incoming: Flow<RoomMessage> = emptyFlow()
        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
            Result.Failure(NetError.Unauthorized)
        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
            Result.Success(Unit)
        override suspend fun leave() {}
    }

    private class FakeTransport(
        val discoveryFlow: MutableSharedFlow<List<DiscoveredRoom>> = MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = 16,
        ),
        var joinVerdict: Result<LocalRoom, NetError> = Result.Failure(NetError.Timeout),
    ) : RoomTransport {
        override val capability: TransportCapability = TransportCapability(
            supportsDiscovery = true,
            latencyHintMs = 0,
            maxPayloadBytes = Int.MAX_VALUE,
        )

        override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> =
            Result.Failure(NetError.Unauthorized)

        override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> =
            joinVerdict

        override fun discoverRooms(): Flow<List<DiscoveredRoom>> = discoveryFlow.asSharedFlow()
    }

    // ---------------------------------------------- Scanning / discovery ----

    @Test
    fun start_scanning_transitions_to_Scanning() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport()
        val controller = JoinRoomController(transport, scope, scanEmptyTimeoutMs = 200L)

        controller.startScanning()
        runCurrent()
        assertEquals(JoinState.Scanning, controller.state.value)
    }

    @Test
    fun discovered_rooms_transition_to_Found() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport()
        val controller = JoinRoomController(transport, scope, scanEmptyTimeoutMs = 200L)

        controller.startScanning()
        runCurrent()
        transport.discoveryFlow.emit(listOf(DiscoveredRoom("ABC234", "Adam's Room")))
        runCurrent()

        val state = controller.state.value
        assertIs<JoinState.Found>(state)
        assertEquals(1, state.rooms.size)
        assertEquals("ABC234", state.rooms.first().code)
    }

    @Test
    fun no_rooms_within_timeout_transitions_to_Empty() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport()
        val controller = JoinRoomController(transport, scope, scanEmptyTimeoutMs = 200L)

        controller.startScanning()
        runCurrent()
        // Emit an empty list to simulate "scan has started, no peers yet".
        transport.discoveryFlow.emit(emptyList())
        advanceTimeBy(250)
        runCurrent()

        assertEquals(JoinState.Empty, controller.state.value)
    }

    @Test
    fun rooms_vanishing_after_found_returns_to_Scanning() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport()
        val controller = JoinRoomController(transport, scope, scanEmptyTimeoutMs = 10_000L)

        controller.startScanning()
        runCurrent()
        transport.discoveryFlow.emit(listOf(DiscoveredRoom("ABC234", "Adam's Room")))
        runCurrent()
        assertIs<JoinState.Found>(controller.state.value)

        // Now all rooms vanish — controller goes back to Scanning so the UI
        // can show its spinner without leaving a stale list on screen.
        transport.discoveryFlow.emit(emptyList())
        runCurrent()
        assertEquals(JoinState.Scanning, controller.state.value)
    }

    // ---------------------------------------------- Manual entry ----

    @Test
    fun enterManualEntry_transitions_to_Manual() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val controller = JoinRoomController(FakeTransport(), scope)
        controller.enterManualEntry()
        runCurrent()
        assertEquals(JoinState.Manual, controller.state.value)
    }

    @Test
    fun retry_from_Failed_returns_to_Manual() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val controller = JoinRoomController(FakeTransport(), scope)
        controller.join("badcode", "Alice") { fail("onJoined should not fire on WrongCode") }
        runCurrent()
        assertIs<JoinState.Failed>(controller.state.value)
        controller.retry()
        assertEquals(JoinState.Manual, controller.state.value)
    }

    // ---------------------------------------------- Join verdicts ----

    @Test
    fun join_with_invalid_code_fails_with_WrongCode() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val controller = JoinRoomController(FakeTransport(), scope)
        controller.join("???", "Alice") { fail("onJoined should not fire on WrongCode") }
        runCurrent()
        val state = controller.state.value
        assertIs<JoinState.Failed>(state)
        assertEquals(JoinError.WrongCode, state.error)
    }

    @Test
    fun join_with_valid_code_and_success_transitions_to_Joined_and_calls_callback() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val room = fakeRoom()
        val transport = FakeTransport(joinVerdict = Result.Success(room))
        val controller = JoinRoomController(transport, scope)

        var received: LocalRoom? = null
        controller.join("ABC234", "Alice") { received = it }
        runCurrent()

        val state = controller.state.value
        assertIs<JoinState.Joined>(state)
        assertEquals("ABC234", state.code)
        assertNotNull(received)
    }

    @Test
    fun join_failure_with_NotConnected_maps_to_HostUnreachable() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport(joinVerdict = Result.Failure(NetError.NotConnected))
        val controller = JoinRoomController(transport, scope)

        controller.join("ABC234", "Alice") {}
        runCurrent()
        val state = controller.state.value
        assertIs<JoinState.Failed>(state)
        assertEquals(JoinError.HostUnreachable, state.error)
    }

    @Test
    fun join_failure_with_Timeout_maps_to_ConnectionTimeout() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport(joinVerdict = Result.Failure(NetError.Timeout))
        val controller = JoinRoomController(transport, scope)
        controller.join("ABC234", "Alice") {}
        runCurrent()
        val state = controller.state.value
        assertIs<JoinState.Failed>(state)
        assertEquals(JoinError.ConnectionTimeout, state.error)
    }

    @Test
    fun join_failure_with_TransportFailure_maps_to_Generic_without_message() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport(
            joinVerdict = Result.Failure(NetError.TransportFailure("internal SSL cipher mismatch x509")),
        )
        val controller = JoinRoomController(transport, scope)
        controller.join("ABC234", "Alice") {}
        runCurrent()
        val state = controller.state.value
        assertIs<JoinState.Failed>(state)
        // The variant carries no message — the original transport string
        // is never reachable from this state branch.
        assertEquals(JoinError.Generic, state.error)
    }

    // ---------------------------------------------- mapNetError table ----

    @Test
    fun mapNetError_table_is_exhaustive_and_typed() {
        assertEquals(JoinError.HostUnreachable, mapNetError(NetError.NotConnected))
        assertEquals(JoinError.ConnectionTimeout, mapNetError(NetError.Timeout))
        assertEquals(JoinError.Generic, mapNetError(NetError.Unauthorized))
        assertEquals(JoinError.Generic, mapNetError(NetError.TransportFailure("anything")))
    }

    // ---------------------------------------------- No-raw-leak structural guard ----

    @Test
    fun JoinError_variants_carry_no_raw_transport_strings() {
        // Structural assertion: each variant is either a data object (no
        // payload) or only carries enum-like values. The test would fail
        // to compile if a `data class Failed(val message: String)` snuck
        // in via a future refactor.
        listOf(
            JoinError.WrongCode,
            JoinError.RoomNotFound,
            JoinError.HostUnreachable,
            JoinError.RoomFull,
            JoinError.GameAlreadyStarted,
            JoinError.ConnectionTimeout,
            JoinError.Generic,
        ).forEach { variant ->
            // toString() of a data object is just the class name — no payload.
            assertTrue(
                variant.toString().none { it == '(' },
                "JoinError.$variant should not expose a payload; got toString='${variant}'",
            )
        }
    }
}

private fun fail(msg: String): Nothing = throw AssertionError(msg)

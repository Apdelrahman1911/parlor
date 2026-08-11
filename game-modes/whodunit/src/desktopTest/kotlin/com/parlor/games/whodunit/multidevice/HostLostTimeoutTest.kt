package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.testing.whodunitPeerCaseForTest
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Host reachability comes from the transport rather than snapshot cadence.
 * A loss starts the 120-second product grace period; restore cancels it and a
 * loss that survives the grace period ends the peer flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HostLostTimeoutTest {

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")
    private val protocol = SessionProtocol(
        sessionId = SessionId("whodunit-session-0001"),
        gameId = WhodunitIds.GameId,
        gameVersion = WhodunitHostRoomBridge.GAME_VERSION,
    )

    private val initialState = WhodunitState(
        public = WhodunitPublic(
            caseId = CaseId("c"),
            modeId = ModeId("m"),
            playersAtTable = listOf(Player(alice, "Alice", seat = 0)),
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = WhodunitHostOnly(
            killerId = alice,
            killerCharacterId = CharacterId("X"),
            randomSeed = 1L,
            seatToCharacter = emptyMap(),
            redHerringTargets = emptyList(),
        ),
        phase = WhodunitPhase.PublicIntro,
        players = listOf(Player(alice, "Alice", seat = 0)),
    )

    @Test
    fun transport_host_loss_that_outlives_grace_ends_peer_session() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = initialState,
            case = whodunitPeerCaseForTest(),
            scope = scope,
            protocol = protocol,
            hostLostTimeoutMs = 200L,
        )
        val events = mutableListOf<PeerEvent>()
        var ended = false
        val eventCollector = scope.launch { bridge.connectionEvents.collect { events += it } }
        val endCollector = scope.launch { bridge.hostDisconnected.collect { ended = true } }
        runCurrent()

        bus.emitHostLost()
        runCurrent()
        assertThat(events).contains(PeerEvent.HostLost)
        assertThat(ended).isFalse()

        advanceTimeBy(201)
        runCurrent()
        assertThat(ended).isTrue()

        eventCollector.cancel()
        endCollector.cancel()
        bridge.close()
    }

    @Test
    fun host_restore_within_grace_cancels_terminal_timeout() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = initialState,
            case = whodunitPeerCaseForTest(),
            scope = scope,
            protocol = protocol,
            hostLostTimeoutMs = 200L,
        )
        val events = mutableListOf<PeerEvent>()
        var ended = false
        val eventCollector = scope.launch { bridge.connectionEvents.collect { events += it } }
        val endCollector = scope.launch { bridge.hostDisconnected.collect { ended = true } }
        runCurrent()

        bus.emitHostLost()
        advanceTimeBy(100)
        bus.emitHostRestored()
        advanceTimeBy(200)
        runCurrent()

        assertThat(events).contains(PeerEvent.HostLost)
        assertThat(events).contains(PeerEvent.HostRestored)
        assertThat(ended).isFalse()

        eventCollector.cancel()
        endCollector.cancel()
        bridge.close()
    }

    @Test
    fun bridge_created_after_host_loss_starts_grace_from_durable_room_state() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(
            bus = bus,
            selfPlayerId = alice,
            displayName = "Alice",
            hostId = hostId,
            initialStatus = RoomInfo.Status.Lost,
        )

        // No HostLost edge is emitted: this models the host disappearing in
        // the hand-off gap before the game bridge subscribed to peerEvents.
        val bridge = WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = initialState,
            case = whodunitPeerCaseForTest(),
            scope = scope,
            protocol = protocol,
            hostLostTimeoutMs = 200L,
        )
        var ended = false
        val endCollector = scope.launch { bridge.hostDisconnected.collect { ended = true } }
        runCurrent()

        assertThat(bridge.connectionState.value.hostLost).isTrue()
        assertThat(ended).isFalse()
        advanceTimeBy(201L)
        runCurrent()
        assertThat(ended).isTrue()

        endCollector.cancel()
        bridge.close()
    }
}

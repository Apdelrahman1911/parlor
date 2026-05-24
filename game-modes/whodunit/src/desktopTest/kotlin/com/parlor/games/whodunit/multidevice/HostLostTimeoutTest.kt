package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.contains
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.room.PeerEvent
import com.parlor.session.multidevice.InMemoryPeerRoom
import com.parlor.session.multidevice.InMemoryRoomBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Wave 9H-5: when no `PublicStateSnapshot` arrives within
 * `hostLostTimeoutMs`, the peer bridge synthesises
 * [PeerEvent.HostLost]. A subsequent snapshot emits
 * [PeerEvent.HostRestored].
 *
 * The tests inject a very short timeout (200 ms of virtual time) and
 * drive the [TestScope] with `advanceTimeBy` to verify both transitions
 * deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HostLostTimeoutTest {

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")

    private val initialState: WhodunitState = WhodunitState(
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
    fun host_silence_for_threshold_emits_host_lost() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)

        val bridge = WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = initialState,
            scope = scope,
            json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true },
            hostLostTimeoutMs = 200L,
        )

        val received = mutableListOf<PeerEvent>()
        val collector = scope.launch { bridge.connectionEvents.collect { received += it } }
        runCurrent()

        // No snapshot arrives — after 250ms the watchdog should fire HostLost.
        advanceTimeBy(250)
        runCurrent()

        assertThat(received).contains(PeerEvent.HostLost)
        collector.cancel()
        bridge.close()
    }

    @Test
    fun snapshot_after_host_lost_emits_host_restored() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)

        val json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true }
        val bridge = WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = initialState,
            scope = scope,
            json = json,
            hostLostTimeoutMs = 200L,
        )

        val received = mutableListOf<PeerEvent>()
        val collector = scope.launch { bridge.connectionEvents.collect { received += it } }
        runCurrent()

        advanceTimeBy(250)
        runCurrent()
        check(received.contains(PeerEvent.HostLost)) { "HostLost should have fired" }

        // Host re-emits a snapshot — bridge should clear HostLost and emit HostRestored.
        val snapshotBytes = json.encodeToString(WhodunitState.serializer(), initialState).encodeToByteArray()
        bus.fromHost(
            target = com.parlor.networking.room.SendTarget.Direct(alice),
            message = com.parlor.networking.protocol.HostMessage.PublicStateSnapshot(snapshotBytes),
        )
        runCurrent()

        assertThat(received).contains(PeerEvent.HostRestored)
        collector.cancel()
        bridge.close()
    }
}

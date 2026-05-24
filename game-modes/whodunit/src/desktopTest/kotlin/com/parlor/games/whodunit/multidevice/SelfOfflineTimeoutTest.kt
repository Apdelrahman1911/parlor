package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.contains
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.action.WhodunitAction
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Wave 9H-5: when `sendToHost` returns [com.parlor.networking.room.NetError.NotConnected],
 * the peer bridge emits [PeerEvent.SelfOffline] and queues the failed
 * action. The next successful send emits [PeerEvent.SelfOnline].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SelfOfflineTimeoutTest {

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
    fun send_failure_emits_self_offline_and_next_success_emits_self_online() = runTest {
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
            hostLostTimeoutMs = 1_000_000L,  // arbitrarily long — irrelevant to this test
        )

        val received = mutableListOf<PeerEvent>()
        val collector = scope.launch { bridge.connectionEvents.collect { received += it } }
        runCurrent()

        // Trip the transport into NotConnected. A submit() will surface this
        // failure to the bridge, which should emit SelfOffline + queue.
        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        runCurrent()

        assertThat(received).contains(PeerEvent.SelfOffline)

        // Restore the transport and let the next submit complete.
        room.simulateNotConnected = false
        bridge.controller.submit(WhodunitAction.AcknowledgeBriefing(alice))
        runCurrent()

        assertThat(received).contains(PeerEvent.SelfOnline)

        collector.cancel()
        bridge.close()
    }
}

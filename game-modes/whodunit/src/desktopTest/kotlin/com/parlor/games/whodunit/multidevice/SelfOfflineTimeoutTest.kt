package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.contains
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.testing.whodunitPeerCaseForTest
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.protocol.SessionProtocol
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
 * the peer bridge emits [PeerEvent.SelfOffline]. A transport restoration
 * emits [PeerEvent.SelfOnline] while the coordinator reconciles the ambiguous
 * command through an outcome query rather than replaying the action.
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
    fun send_failure_emits_self_offline_and_host_restore_emits_self_online() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)

        val bridge = WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = initialState,
            case = whodunitPeerCaseForTest(),
            scope = scope,
            protocol = SessionProtocol(
                sessionId = SessionId("whodunit-session-0001"),
                gameId = WhodunitIds.GameId,
                gameVersion = WhodunitHostRoomBridge.GAME_VERSION,
            ),
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

        // Restore the transport. The original action remains pending until an
        // authoritative outcome; it is not silently replayed.
        room.simulateNotConnected = false
        bus.emitHostRestored()
        runCurrent()

        assertThat(received).contains(PeerEvent.SelfOnline)

        collector.cancel()
        bridge.close()
    }
}

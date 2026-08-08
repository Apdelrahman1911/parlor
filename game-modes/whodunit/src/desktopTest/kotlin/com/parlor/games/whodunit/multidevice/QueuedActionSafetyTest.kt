package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.session.multidevice.InMemoryPeerRoom
import com.parlor.session.multidevice.InMemoryRoomBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Offline intent is retained as its original immutable ClientCommand. Retries
 * preserve the id/sequence for host deduplication, while multiple intents keep
 * increasing sequences and the same expected revision so only a valid
 * authoritative order can apply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuedActionSafetyTest {
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

    private fun bridge(scope: TestScope, room: InMemoryPeerRoom) = WhodunitPeerRoomBridge(
        room = room,
        selfPlayerId = alice,
        initialPublic = initialState,
        scope = scope,
        protocol = protocol,
    )

    @Test
    fun repeated_restore_retries_the_same_command_identity() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = bridge(scope, room)
        val commands = mutableListOf<PeerMessage.ClientCommand>()
        val collector = scope.launch {
            bus.hostMessagesIn.filterIsInstance<PeerMessage.ClientCommand>().take(2).toList(commands)
        }

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        room.simulateNotConnected = false
        bus.emitHostRestored()
        bus.emitHostLost()
        bus.emitHostRestored()
        runCurrent()

        assertThat(commands).hasSize(2)
        assertThat(commands[1].commandId).isEqualTo(commands[0].commandId)
        assertThat(commands[1].clientSequence).isEqualTo(commands[0].clientSequence)
        assertThat(commands[1].expectedRevision).isEqualTo(commands[0].expectedRevision)

        collector.cancel()
        bridge.close()
    }

    @Test
    fun multiple_offline_intents_keep_order_and_cannot_share_command_identity() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = bridge(scope, room)
        val commands = mutableListOf<PeerMessage.ClientCommand>()
        val collector = scope.launch {
            bus.hostMessagesIn.filterIsInstance<PeerMessage.ClientCommand>().take(2).toList(commands)
        }

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        bridge.controller.submit(WhodunitAction.AcknowledgeBriefing(alice))
        room.simulateNotConnected = false
        bus.emitHostRestored()
        runCurrent()

        assertThat(commands).hasSize(2)
        assertThat(commands[0].clientSequence).isEqualTo(1L)
        assertThat(commands[1].clientSequence).isEqualTo(2L)
        assertThat(commands[0].commandId).isNotEqualTo(commands[1].commandId)
        assertThat(commands[0].expectedRevision).isEqualTo(0L)
        assertThat(commands[1].expectedRevision).isEqualTo(0L)

        collector.cancel()
        bridge.close()
    }
}

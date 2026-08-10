package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.testing.whodunitPeerCaseForTest
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Ambiguous offline intent is never replayed. Restore sends an idempotent
 * outcome query for the original command id, and a second mutation is rejected
 * until the host resolves that command.
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
        case = whodunitPeerCaseForTest(),
        scope = scope,
        protocol = protocol,
    )

    @Test
    fun repeated_restore_queries_the_same_command_without_replaying_it() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = bridge(scope, room)
        val commands = mutableListOf<PeerMessage.ClientCommand>()
        val outcomeQueries = mutableListOf<PeerMessage.CommandOutcomeRequest>()
        val collector = scope.launch {
            bus.hostMessagesIn.collect { message ->
                when (message) {
                    is PeerMessage.ClientCommand -> commands += message
                    is PeerMessage.CommandOutcomeRequest -> outcomeQueries += message
                    else -> Unit
                }
            }
        }

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        room.simulateNotConnected = false
        bus.emitHostRestored()
        bus.emitHostLost()
        bus.emitHostRestored()
        runCurrent()

        assertThat(commands).isEmpty()
        assertThat(outcomeQueries).hasSize(2)
        assertThat(outcomeQueries[1].commandId).isEqualTo(outcomeQueries[0].commandId)

        collector.cancel()
        bridge.close()
    }

    @Test
    fun second_offline_intent_is_rejected_while_first_outcome_is_unknown() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = bridge(scope, room)
        val commands = mutableListOf<PeerMessage.ClientCommand>()
        val outcomeQueries = mutableListOf<PeerMessage.CommandOutcomeRequest>()
        val collector = scope.launch {
            bus.hostMessagesIn.collect { message ->
                when (message) {
                    is PeerMessage.ClientCommand -> commands += message
                    is PeerMessage.CommandOutcomeRequest -> outcomeQueries += message
                    else -> Unit
                }
            }
        }

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        val second = bridge.controller.submit(WhodunitAction.AcknowledgeBriefing(alice))
        room.simulateNotConnected = false
        bus.emitHostRestored()
        runCurrent()

        assertThat(second).isInstanceOf(Result.Failure::class)
        assertThat((second as Result.Failure).error).isEqualTo(SubmitError.CommandPending)
        assertThat(commands).isEmpty()
        assertThat(outcomeQueries).hasSize(1)

        collector.cancel()
        bridge.close()
    }
}

package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.session.multidevice.InMemoryPeerRoom
import com.parlor.session.multidevice.InMemoryRoomBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Wave 9H-5: the offline action queue must never let a stale action
 * fire. Three guard cases:
 *
 *  - Phase advanced while offline → queued action is dropped.
 *  - Peer dropped (host called `ContinueWithoutPlayer`) → queued action
 *    is dropped regardless of phase.
 *  - Single-slot — a second queued action while still offline replaces
 *    the first, so the user's *latest* intent is what fires (never
 *    both, never the old one).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuedActionSafetyTest {

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")

    private fun baseState(phase: WhodunitPhase, droppedPlayers: Set<PlayerId> = emptySet()): WhodunitState =
        WhodunitState(
            public = WhodunitPublic(
                caseId = CaseId("c"),
                modeId = ModeId("m"),
                playersAtTable = listOf(Player(alice, "Alice", seat = 0)),
                droppedPlayers = droppedPlayers,
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = WhodunitHostOnly(
                killerId = alice,
                killerCharacterId = CharacterId("X"),
                randomSeed = 1L,
                seatToCharacter = emptyMap(),
                redHerringTargets = emptyList(),
            ),
            phase = phase,
            players = listOf(Player(alice, "Alice", seat = 0)),
        )

    private fun buildBridge(scope: TestScope, room: InMemoryPeerRoom): WhodunitPeerRoomBridge =
        WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = baseState(WhodunitPhase.PublicIntro),
            scope = scope,
            json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true },
            hostLostTimeoutMs = 1_000_000L,
        )

    @Test
    fun queued_action_dropped_when_phase_changes_during_offline() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = buildBridge(scope, room)

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        runCurrent()

        // The bridge held the action; phase is still PublicIntro at this point.
        assertThat(bridge.queuedActionForTest()).isEqualTo(WhodunitAction.AcknowledgeIntro(alice))

        // While offline, the canonical state moves to RulesBriefing — the
        // shadow controller picks up the new public projection.
        bridge.controller.updatePublic(PublicProjection(baseState(WhodunitPhase.RulesBriefing)))
        runCurrent()

        // Reconnect. The next submit succeeds and triggers replay — but the
        // phase-stamp check should drop the queued AcknowledgeIntro because
        // the canonical state is now RulesBriefing.
        room.simulateNotConnected = false
        bridge.controller.submit(WhodunitAction.AcknowledgeBriefing(alice))
        runCurrent()

        assertThat(bridge.queuedActionForTest()).isNull()
    }

    @Test
    fun queued_action_dropped_when_peer_added_to_droppedPlayers() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = buildBridge(scope, room)

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        runCurrent()
        assertThat(bridge.queuedActionForTest()).isEqualTo(WhodunitAction.AcknowledgeIntro(alice))

        // While offline, host drops alice. The shadow's next public update
        // shows alice in droppedPlayers.
        bridge.controller.updatePublic(
            PublicProjection(baseState(WhodunitPhase.PublicIntro, droppedPlayers = setOf(alice))),
        )
        runCurrent()

        room.simulateNotConnected = false
        // Even a same-phase replay attempt must be dropped because the peer
        // is now a spectator.
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        runCurrent()
        // The action above also fails the droppedPlayer check on the host —
        // here we only assert the bridge cleared its queue.
        assertThat(bridge.queuedActionForTest()).isNull()
    }

    @Test
    fun double_queue_while_offline_keeps_only_latest() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = buildBridge(scope, room)

        room.simulateNotConnected = true
        bridge.controller.submit(WhodunitAction.AcknowledgeIntro(alice))
        runCurrent()
        // The user changes their mind and acks briefing instead while still
        // offline (UI scenario: they swiped past the intro before reconnect).
        bridge.controller.submit(WhodunitAction.AcknowledgeBriefing(alice))
        runCurrent()

        // Only the latest survives in the queue.
        assertThat(bridge.queuedActionForTest()).isEqualTo(WhodunitAction.AcknowledgeBriefing(alice))
    }
}

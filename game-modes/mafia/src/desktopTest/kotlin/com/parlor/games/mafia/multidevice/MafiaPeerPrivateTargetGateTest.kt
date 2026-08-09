package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.state.DetectiveResult
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaPublic
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostRoomBridge
import com.parlor.games.mafia.ui.flow.multidevice.MafiaPeerRoomBridge
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.SendTarget
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
 * Atomic player snapshots have no self-attested target: routing is direct and
 * enforced by the trusted host. The peer additionally rejects any private
 * slice carried by the wrong session/game/version envelope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MafiaPeerPrivateTargetGateTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val frank = PlayerId("frank")
    private val players = listOf(
        Player(hostId, "Host", 0),
        Player(alice, "Alice", 1),
        Player(bob, "Bob", 2),
        Player(PlayerId("carol"), "Carol", 3),
        Player(PlayerId("dave"), "Dave", 4),
        Player(PlayerId("eve"), "Eve", 5),
        Player(frank, "Frank", 6),
    )
    private val protocol = SessionProtocol(
        sessionId = SessionId("mafia-session-000001"),
        gameId = MafiaIds.GameId,
        gameVersion = MafiaHostRoomBridge.GAME_VERSION,
    )

    private fun emptyPublic() = MafiaState(
        public = MafiaPublic(
            settings = MafiaSettingsPresets.forPlayerCount(players.size),
            roster = players.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = MafiaHostOnly(emptyMap(), 0L),
        phase = MafiaPhase.Night(day = 1),
        players = players,
    )

    private fun header(sequence: Long, gameId: GameId = MafiaIds.GameId) =
        SessionEnvelopeHeader(
            protocol = ProtocolVersion(),
            sessionId = protocol.sessionId,
            gameId = gameId,
            gameVersion = protocol.gameVersion,
            messageId = "mafia-snapshot-0000000$sequence",
            sequence = sequence,
        )

    @Test
    fun peer_rejects_private_payload_from_wrong_game_envelope() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = MafiaPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = emptyPublic(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        var failedClosed = false
        val endCollector = scope.launch { bridge.hostDisconnected.collect { failedClosed = true } }

        val foreign = MafiaPrivate(
            role = Role.Detective,
            team = Team.Town,
            pendingDetectiveResult = DetectiveResult(1, frank, DetectiveSeesAs.Mafia),
        )
        bus.fromHost(
            SendTarget.Direct(alice),
            HostMessage.PlayerSnapshot(
                header = header(1, gameId = GameId("foreign-game")),
                revision = 1,
                publicPayload = json.encodeToString(MafiaState.serializer(), emptyPublic()).encodeToByteArray(),
                privatePayload = json.encodeToString(MafiaPrivate.serializer(), foreign).encodeToByteArray(),
            ),
        )
        scope.runCurrent()

        assertThat(bridge.controller.publicState.value.state.privatePerPlayer[alice]).isNull()
        assertThat(failedClosed).isTrue()
        endCollector.cancel()
        bridge.close()
    }

    @Test
    fun peer_installs_its_direct_private_slice_from_valid_atomic_snapshot() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val room = InMemoryPeerRoom(bus, alice, "Alice", hostId)
        val bridge = MafiaPeerRoomBridge(
            room = room,
            selfPlayerId = alice,
            initialPublic = emptyPublic(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        val own = MafiaPrivate(
            role = Role.Civilian,
            team = Team.Town,
            lastSuspicion = frank,
        )
        bus.fromHost(
            SendTarget.Direct(alice),
            HostMessage.PlayerSnapshot(
                header = header(1),
                revision = 1,
                publicPayload = json.encodeToString(MafiaState.serializer(), emptyPublic()).encodeToByteArray(),
                privatePayload = json.encodeToString(MafiaPrivate.serializer(), own).encodeToByteArray(),
            ),
        )
        scope.runCurrent()

        assertThat(bridge.controller.publicState.value.state.privatePerPlayer).isEmpty()
        val installed = bridge.controller.privateStateFor(alice).value.state.privatePerPlayer[alice]
        assertThat(installed?.lastSuspicion).isEqualTo(frank)
        assertThat(installed?.role).isEqualTo(Role.Civilian)
        bridge.close()
    }
}

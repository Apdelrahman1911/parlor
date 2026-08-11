package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
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
        startId = "mafia-start-00000001",
    )

    private fun emptyPublic() = MafiaState(
        public = MafiaPublic(
            settings = MafiaSettingsPresets.forPlayerCount(players.size),
            day = 1,
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

    private fun canonicalSecretState() = emptyPublic().copy(
        privatePerPlayer = mapOf(
            alice to MafiaPrivate(Role.Civilian, Team.Town),
            bob to MafiaPrivate(Role.Mafia, Team.Mafia, knownTeammates = setOf(frank)),
        ),
        hostOnly = MafiaHostOnly(
            fullRoleMap = mapOf(alice to Role.Civilian, bob to Role.Mafia),
            randomSeed = 9_001L,
        ),
    )

    @Test
    fun peer_redacts_an_accidentally_canonical_initial_placeholder() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = canonicalSecretState(),
            scope = scope,
            protocol = protocol,
            json = json,
        )

        val public = bridge.controller.publicState.value.state
        val own = bridge.controller.privateStateFor(alice).value.state
        assertThat(public.privatePerPlayer).isEmpty()
        assertThat(public.hostOnly.fullRoleMap).isEmpty()
        assertThat(public.hostOnly.randomSeed).isEqualTo(0L)
        assertThat(own.privatePerPlayer).isEmpty()
        assertThat(own.hostOnly.fullRoleMap).isEmpty()
        assertThat(own.hostOnly.randomSeed).isEqualTo(0L)
        bridge.close()
    }

    @Test
    fun peer_rejects_snapshot_whose_public_payload_contains_host_secrets() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = emptyPublic(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        var failedClosed = false
        val endCollector = scope.launch { bridge.hostDisconnected.collect { failedClosed = true } }

        bus.fromHost(
            SendTarget.Direct(alice),
            HostMessage.PlayerSnapshot(
                header = header(1),
                revision = 1,
                publicPayload = json.encodeToString(
                    MafiaState.serializer(),
                    canonicalSecretState(),
                ).encodeToByteArray(),
                privatePayload = ByteArray(0),
            ),
        )
        scope.runCurrent()

        assertThat(bridge.hasAuthoritativeSnapshot.value).isFalse()
        assertThat(bridge.controller.publicState.value.state.hostOnly.randomSeed).isEqualTo(0L)
        assertThat(failedClosed).isTrue()
        endCollector.cancel()
        bridge.close()
    }

    @Test
    fun peer_rejects_privacy_safe_but_reducer_impossible_public_snapshot() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val initial = emptyPublic().copy(
            phase = MafiaPhase.Setup,
            public = emptyPublic().public.copy(day = 0),
        )
        val bridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = initial,
            scope = scope,
            protocol = protocol,
            json = json,
        )
        val impossible = initial.copy(
            phase = MafiaPhase.Night(day = 1),
            // A legal Night(1) snapshot must also expose public.day == 1 and
            // carry the receiving player's assigned private role.
            public = initial.public.copy(day = 0),
        )
        val validOwnPrivate = MafiaPrivate(Role.Civilian, Team.Town)

        bus.fromHost(
            SendTarget.Direct(alice),
            HostMessage.PlayerSnapshot(
                header = header(1),
                revision = 1,
                publicPayload = json.encodeToString(
                    MafiaState.serializer(),
                    emptyPublic(),
                ).encodeToByteArray(),
                privatePayload = json.encodeToString(
                    MafiaPrivate.serializer(),
                    validOwnPrivate,
                ).encodeToByteArray(),
            ),
        )
        scope.runCurrent()
        assertThat(bridge.hasAuthoritativeSnapshot.value).isTrue()
        assertThat(bridge.controller.publicState.value.state.phase).isEqualTo(MafiaPhase.Night(1))

        bus.fromHost(
            SendTarget.Direct(alice),
            HostMessage.PlayerSnapshot(
                header = header(2),
                revision = 2,
                publicPayload = json.encodeToString(
                    MafiaState.serializer(),
                    impossible,
                ).encodeToByteArray(),
                privatePayload = json.encodeToString(
                    MafiaPrivate.serializer(),
                    validOwnPrivate,
                ).encodeToByteArray(),
            ),
        )
        scope.runCurrent()

        assertThat(bridge.hasAuthoritativeSnapshot.value).isTrue()
        assertThat(bridge.controller.publicState.value.state.phase).isEqualTo(MafiaPhase.Night(1))
        assertThat(bridge.controller.privateStateFor(alice).value.state.privatePerPlayer[alice])
            .isEqualTo(validOwnPrivate)
        bridge.close()
    }

    @Test
    fun peer_rejects_active_snapshot_with_a_permanently_dropped_seat_atomically() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = emptyPublic(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        val own = MafiaPrivate(Role.Civilian, Team.Town)
        bus.fromHost(
            SendTarget.Direct(alice),
            playerSnapshot(emptyPublic(), own, sequence = 1, revision = 1),
        )
        scope.runCurrent()
        val accepted = bridge.controller.publicState.value.state

        val impossible = emptyPublic().copy(
            public = emptyPublic().public.copy(droppedPlayers = setOf(bob)),
        )
        bus.fromHost(
            SendTarget.Direct(alice),
            playerSnapshot(impossible, own, sequence = 2, revision = 2),
        )
        scope.runCurrent()

        assertThat(bridge.controller.publicState.value.state).isEqualTo(accepted)
        assertThat(bridge.controller.privateStateFor(alice).value.state.privatePerPlayer[alice])
            .isEqualTo(own)
        bridge.close()
    }

    @Test
    fun peer_rejects_malformed_utf8_snapshot_without_replacing_last_good_state() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = emptyPublic(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        val own = MafiaPrivate(Role.Civilian, Team.Town)
        bus.fromHost(
            SendTarget.Direct(alice),
            playerSnapshot(emptyPublic(), own, sequence = 1, revision = 1),
        )
        scope.runCurrent()
        val accepted = bridge.controller.publicState.value.state

        bus.fromHost(
            SendTarget.Direct(alice),
            HostMessage.PlayerSnapshot(
                header = header(2),
                revision = 2,
                publicPayload = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), '}'.code.toByte()),
                privatePayload = json.encodeToString(MafiaPrivate.serializer(), own).encodeToByteArray(),
            ),
        )
        scope.runCurrent()

        assertThat(bridge.controller.publicState.value.state).isEqualTo(accepted)
        assertThat(bridge.controller.privateStateFor(alice).value.state.privatePerPlayer[alice])
            .isEqualTo(own)
        bridge.close()
    }

    @Test
    fun peer_rejects_a_structurally_valid_roster_substitution_for_the_same_session() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = emptyPublic(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        val own = MafiaPrivate(Role.Civilian, Team.Town)
        bus.fromHost(
            SendTarget.Direct(alice),
            playerSnapshot(emptyPublic(), own, sequence = 1, revision = 1),
        )
        scope.runCurrent()

        val renamedPlayers = players.map { player ->
            if (player.id == bob) player.copy(displayName = "Mallory") else player
        }
        val substituted = emptyPublic().copy(
            players = renamedPlayers,
            public = emptyPublic().public.copy(
                roster = renamedPlayers.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
            ),
        )
        bus.fromHost(
            SendTarget.Direct(alice),
            playerSnapshot(substituted, own, sequence = 2, revision = 2),
        )
        scope.runCurrent()

        assertThat(bridge.controller.publicState.value.state.players).isEqualTo(players)
        bridge.close()
    }

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
            nightChoiceSubmitted = true,
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

    private fun playerSnapshot(
        state: MafiaState,
        ownPrivate: MafiaPrivate,
        sequence: Long,
        revision: Long,
    ) = HostMessage.PlayerSnapshot(
        header = header(sequence),
        revision = revision,
        publicPayload = json.encodeToString(MafiaState.serializer(), state).encodeToByteArray(),
        privatePayload = json.encodeToString(MafiaPrivate.serializer(), ownPrivate).encodeToByteArray(),
    )
}

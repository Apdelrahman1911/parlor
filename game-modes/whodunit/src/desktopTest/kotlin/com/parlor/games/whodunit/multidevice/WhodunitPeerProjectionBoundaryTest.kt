package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
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

/** Regression coverage for the game-specific public/private snapshot boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
class WhodunitPeerProjectionBoundaryTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val host = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val players = listOf(
        Player(host, "Host", 0),
        Player(alice, "Alice", 1),
        Player(bob, "Bob", 2),
        Player(PlayerId("carol"), "Carol", 3),
    )
    private val protocol = SessionProtocol(
        sessionId = SessionId("whodunit-session-0001"),
        gameId = WhodunitIds.GameId,
        gameVersion = WhodunitHostRoomBridge.GAME_VERSION,
        startId = "whodunit-start-0001",
    )

    private fun canonicalSecretState() = WhodunitState(
        public = WhodunitPublic(
            caseId = CaseId("last-dinner"),
            modeId = WhodunitIds.ClassicVoteModeId,
            playersAtTable = players,
            roleAssignmentGeneration = 7L,
        ),
        privatePerPlayer = mapOf(
            host to WhodunitPrivate(PlayerRole.Innocent, CharacterId("chef")),
            alice to WhodunitPrivate(
                PlayerRole.Killer,
                CharacterId("heir"),
                deflectionTargets = listOf(CharacterId("doctor")),
            ),
            bob to WhodunitPrivate(PlayerRole.Innocent, CharacterId("doctor")),
        ),
        hostOnly = WhodunitHostOnly(
            killerId = alice,
            killerCharacterId = CharacterId("heir"),
            randomSeed = 9_001L,
            seatToCharacter = mapOf(alice to CharacterId("heir")),
            redHerringTargets = listOf(CharacterId("doctor")),
        ),
        phase = WhodunitPhase.PublicIntro,
        players = players,
    )

    private fun header(sequence: Long) = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = "whodunit-snapshot-0000$sequence",
        sequence = sequence,
    )

    @Test
    fun peer_redacts_an_accidentally_canonical_initial_placeholder() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = WhodunitPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", host),
            selfPlayerId = alice,
            initialPublic = canonicalSecretState(),
            scope = scope,
            protocol = protocol,
            json = json,
        )

        val public = bridge.controller.publicState.value.state
        val own = bridge.controller.privateStateFor(alice).value.state
        assertThat(public.privatePerPlayer).isEmpty()
        assertThat(public.hostOnly.randomSeed).isEqualTo(0L)
        assertThat(public.hostOnly.seatToCharacter).isEmpty()
        assertThat(public.public.roleAssignmentGeneration).isEqualTo(7L)
        assertThat(own.privatePerPlayer).isEmpty()
        assertThat(own.hostOnly.randomSeed).isEqualTo(0L)
        assertThat(own.public.roleAssignmentGeneration).isEqualTo(7L)
        bridge.close()
    }

    @Test
    fun peer_rejects_snapshot_whose_public_payload_contains_host_secrets() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = WhodunitPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", host),
            selfPlayerId = alice,
            initialPublic = canonicalSecretState(),
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
                    WhodunitState.serializer(),
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
    fun ownProjectionKeepsAssignmentEpochAndDossierFromTheSameRevision() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val bridge = WhodunitPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", host),
            selfPlayerId = alice,
            initialPublic = canonicalSecretState(),
            scope = scope,
            protocol = protocol,
            json = json,
        )
        val first = canonicalSecretState().copy(
            phase = WhodunitPhase.CharacterReveal(0),
            privatePerPlayer = canonicalSecretState().privatePerPlayer + (
                alice to canonicalSecretState().privatePerPlayer.getValue(alice).copy(
                    dossierUnlocked = true,
                )
            ),
        )
        val second = first.copy(
            public = first.public.copy(roleAssignmentGeneration = 8L),
            privatePerPlayer = first.privatePerPlayer + (
                alice to WhodunitPrivate(
                    role = PlayerRole.Innocent,
                    characterId = CharacterId("doctor"),
                    dossierUnlocked = false,
                )
            ),
        )

        bus.fromHost(SendTarget.Direct(alice), playerSnapshot(first, sequence = 1, revision = 1))
        scope.runCurrent()
        val firstRenderState = bridge.controller.privateStateFor(alice).value.state
        assertThat(firstRenderState.public.roleAssignmentGeneration).isEqualTo(7L)
        assertThat(firstRenderState.privatePerPlayer.getValue(alice).characterId)
            .isEqualTo(CharacterId("heir"))
        assertThat(firstRenderState.privatePerPlayer.getValue(alice).dossierUnlocked).isTrue()

        bus.fromHost(SendTarget.Direct(alice), playerSnapshot(second, sequence = 2, revision = 2))
        scope.runCurrent()
        val secondRenderState = bridge.controller.privateStateFor(alice).value.state
        assertThat(secondRenderState.public.roleAssignmentGeneration).isEqualTo(8L)
        assertThat(secondRenderState.privatePerPlayer.getValue(alice).characterId)
            .isEqualTo(CharacterId("doctor"))
        assertThat(secondRenderState.privatePerPlayer.getValue(alice).dossierUnlocked).isFalse()
        bridge.close()
    }

    private fun playerSnapshot(
        state: WhodunitState,
        sequence: Long,
        revision: Long,
    ): HostMessage.PlayerSnapshot = HostMessage.PlayerSnapshot(
        header = header(sequence),
        revision = revision,
        publicPayload = json.encodeToString(
            WhodunitState.serializer(),
            WhodunitProjectionPolicy.toPublic(state).state,
        ).encodeToByteArray(),
        privatePayload = json.encodeToString(
            WhodunitPrivate.serializer(),
            state.privatePerPlayer.getValue(alice),
        ).encodeToByteArray(),
    )
}

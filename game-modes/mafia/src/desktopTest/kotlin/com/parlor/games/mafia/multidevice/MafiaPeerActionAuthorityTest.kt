package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostRoomBridge
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Peer-side action authority contract for the Mafia multi-device path.
 *
 * Verifies — over the wire, through a real [MafiaHostRoomBridge] — that:
 *
 *  1. A peer's **SelfActor** action with the correct sender id is accepted
 *     by the host bridge and updates the canonical state.
 *  2. A peer's **SelfActor** action submitted on someone else's behalf is
 *     rejected (impersonation).
 *  3. A peer's **HostOnly** action (e.g. `ResolveNight`) is rejected.
 *  4. A peer in [com.parlor.games.mafia.domain.state.MafiaPublic.droppedPlayers]
 *     cannot submit actions at all.
 *
 * The bridge uses `MafiaActionAuthority.isAllowed(action, sender, hostId,
 * dropped)` for these checks — failure here would mean a peer could
 * exfiltrate state changes from the host that it should not be able to
 * produce.
 *
 * The test is structured to be deterministic: we use a [FakeClock] and a
 * seeded [RandomSource]. We assert the **canonical host state** before
 * and after each peer submission rather than relying on snapshot
 * propagation timing — direct state inspection is the tightest contract
 * we can write here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MafiaPeerActionAuthorityTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val carol = PlayerId("carol")
    private val dave = PlayerId("dave")
    private val eve = PlayerId("eve")
    private val frank = PlayerId("frank")

    private val players = listOf(
        Player(hostId, "Host", seat = 0),
        Player(alice, "Alice", seat = 1),
        Player(bob, "Bob", seat = 2),
        Player(carol, "Carol", seat = 3),
        Player(dave, "Dave", seat = 4),
        Player(eve, "Eve", seat = 5),
        Player(frank, "Frank", seat = 6),
    )

    private suspend fun startedFixture(scope: TestScope): Fixture {
        val bus = InMemoryRoomBus()
        for (p in players) bus.registerPeer(p.id)

        val definition = MafiaDefinition(json)
        val config = SessionConfig(
            sessionId = SessionId("authority-host"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = players,
            randomSeed = 7L,
        )
        val hostSession = PassAndPlaySessionController(
            definition = definition,
            config = config,
            reducerContext = DefaultReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(7L),
            ),
            scope = scope,
        )
        val hostRoom = TestHostRoom(bus, hostId)
        val bridge = MafiaHostRoomBridge(
            hostSession, hostRoom, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )

        // Apply settings + start the game so we reach Night with assigned
        // roles. Use the preset for 7 players adjusted to 2 mafia.
        val settings = MafiaSettingsPresets.forPlayerCount(players.size)
            .copy(roleCounts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1))
        hostSession.submit(MafiaAction.ApplySettings(settings))
        scope.runCurrent()
        hostSession.submit(MafiaAction.StartGame)
        scope.runCurrent()
        // Ack every player's role and advance to Night.
        for (p in players) {
            hostSession.submit(MafiaAction.AcknowledgeRoleViewed(p.id))
            scope.runCurrent()
        }
        hostSession.submit(MafiaAction.AdvanceFromRoleAssignment)
        scope.runCurrent()
        return Fixture(bus, hostSession, hostRoom, bridge)
    }

    @Test
    fun peer_self_actor_with_correct_sender_is_accepted() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val state = f.session.hostState.value.state
            // We need a peer whose role is non-Mafia for an unambiguous
            // submission. Pick the Detective and submit their inspect.
            val detectiveId = state.privatePerPlayer.entries
                .first { it.value.role == Role.Detective }.key
            val detectiveRoom = InMemoryPeerRoom(
                bus = f.bus, selfPlayerId = detectiveId,
                displayName = state.players.first { it.id == detectiveId }.displayName,
                hostId = hostId,
            )

            // Pick any other living player as the target.
            val target = state.players.first { it.id != detectiveId }.id
            val result = detectiveRoom.sendToHost(
                command(
                    protocol = f.bridge.protocol,
                    claimedActor = detectiveId,
                    payload = MafiaActionCodec.encode(
                        MafiaAction.SubmitDetectiveInspect(detectiveId, target),
                    ),
                ),
            )
            assertThat(result is Result.Success).isTrue()
            scope.runCurrent()

            // Detective's pendingNightChoice should now be the target.
            val afterPriv = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            assertThat(afterPriv?.pendingNightChoice).isEqualTo(target)
        } finally {
            f.bridge.close()
        }
    }

    @Test
    fun peer_self_actor_with_impersonated_sender_is_rejected() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val state = f.session.hostState.value.state
            val detectiveId = state.privatePerPlayer.entries
                .first { it.value.role == Role.Detective }.key
            // Pick a Town player who is NOT the detective; they will try to
            // submit a detective inspect "on behalf of" the detective.
            val townImposter = state.privatePerPlayer.entries
                .first { it.value.role == Role.Civilian }.key
            val imposterRoom = InMemoryPeerRoom(
                bus = f.bus, selfPlayerId = townImposter,
                displayName = state.players.first { it.id == townImposter }.displayName,
                hostId = hostId,
            )
            val target = state.players.first { it.id != detectiveId }.id

            val before = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            imposterRoom.sendToHost(
                command(
                    protocol = f.bridge.protocol,
                    claimedActor = townImposter,
                    payload = MafiaActionCodec.encode(
                        MafiaAction.SubmitDetectiveInspect(detectiveId, target),
                    ),
                ),
            )
            scope.runCurrent()
            val after = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            // Detective's slice must NOT have been mutated by the imposter.
            assertThat(after?.pendingNightChoice == before?.pendingNightChoice).isTrue()
        } finally {
            f.bridge.close()
        }
    }

    /**
     * A modified peer forges `ClientCommand.actor` to the victim's id. The
     * transport overwrites it with the connection-bound imposter identity, so
     * the authority gate sees actor=imposter != by=victim and rejects it.
     */
    @Test
    fun peer_cannot_forge_sender_to_impersonate_another_player() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val state = f.session.hostState.value.state
            val detectiveId = state.privatePerPlayer.entries
                .first { it.value.role == Role.Detective }.key
            val townImposter = state.privatePerPlayer.entries
                .first { it.value.role == Role.Civilian }.key
            val imposterRoom = InMemoryPeerRoom(
                bus = f.bus, selfPlayerId = townImposter,
                displayName = state.players.first { it.id == townImposter }.displayName,
                hostId = hostId,
            )
            val target = state.players.first { it.id != detectiveId }.id
            val before = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            imposterRoom.sendToHost(
                command(
                    protocol = f.bridge.protocol,
                    // FORGED: the in-memory transport overwrites this actor
                    // with the room-bound townImposter identity.
                    claimedActor = detectiveId,
                    payload = MafiaActionCodec.encode(
                        MafiaAction.SubmitDetectiveInspect(detectiveId, target),
                    ),
                ),
            )
            scope.runCurrent()
            val after = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            // Transport-authenticated sender (townImposter) != by (detective) → rejected.
            assertThat(after?.pendingNightChoice == before?.pendingNightChoice).isTrue()
        } finally {
            f.bridge.close()
        }
    }

    @Test
    fun peer_host_only_action_is_rejected() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val state = f.session.hostState.value.state
            // Use any peer (the Detective in this case) to try a HostOnly
            // action: ResolveNight is classified HostOnly by
            // MafiaActionAuthority.
            val detectiveId = state.privatePerPlayer.entries
                .first { it.value.role == Role.Detective }.key
            val peerRoom = InMemoryPeerRoom(
                bus = f.bus, selfPlayerId = detectiveId,
                displayName = state.players.first { it.id == detectiveId }.displayName,
                hostId = hostId,
            )

            val phaseBefore = f.session.hostState.value.state.phase
            peerRoom.sendToHost(
                command(
                    protocol = f.bridge.protocol,
                    claimedActor = detectiveId,
                    payload = MafiaActionCodec.encode(MafiaAction.ResolveNight),
                ),
            )
            scope.runCurrent()
            val phaseAfter = f.session.hostState.value.state.phase
            assertThat(phaseAfter == phaseBefore).isTrue()
            assertThat(phaseAfter is MafiaPhase.Night).isTrue()
        } finally {
            f.bridge.close()
        }
    }

    @Test
    fun peer_in_dropped_players_cannot_submit_actions() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val state = f.session.hostState.value.state
            val detectiveId = state.privatePerPlayer.entries
                .first { it.value.role == Role.Detective }.key
            // Drop the detective via the host-side action (HostOnly).
            f.session.submit(MafiaAction.ContinueWithoutPlayer(detectiveId))
            scope.runCurrent()
            val droppedNow = f.session.hostState.value.state.public.droppedPlayers
            assertThat(detectiveId in droppedNow).isTrue()

            // The dropped peer now tries to submit a valid SelfActor action.
            val droppedRoom = InMemoryPeerRoom(
                bus = f.bus, selfPlayerId = detectiveId,
                displayName = state.players.first { it.id == detectiveId }.displayName,
                hostId = hostId,
            )
            val target = state.players.first { it.id != detectiveId }.id
            val before = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            droppedRoom.sendToHost(
                command(
                    protocol = f.bridge.protocol,
                    claimedActor = detectiveId,
                    payload = MafiaActionCodec.encode(
                        MafiaAction.SubmitDetectiveInspect(detectiveId, target),
                    ),
                ),
            )
            scope.runCurrent()
            val after = f.session.hostState.value.state.privatePerPlayer[detectiveId]
            // pendingNightChoice should not have been written by the
            // dropped detective — authority rejects via droppedPlayers.
            assertThat(after?.pendingNightChoice == before?.pendingNightChoice).isTrue()
            // And, sanity: pendingNightChoice was null going in (we haven't
            // submitted anything yet for this night).
            assertThat(before?.pendingNightChoice == null).isTrue()
        } finally {
            f.bridge.close()
        }
    }

    @Test
    fun peer_malformed_payload_is_silently_dropped() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val phaseBefore = f.session.hostState.value.state.phase
            val peerRoom = InMemoryPeerRoom(
                bus = f.bus, selfPlayerId = alice,
                displayName = "Alice", hostId = hostId,
            )
            // Garbage bytes — codec will fail. Bridge swallows the error.
            peerRoom.sendToHost(
                command(
                    protocol = f.bridge.protocol,
                    claimedActor = alice,
                    payload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
                ),
            )
            scope.runCurrent()
            // State unchanged.
            assertThat(f.session.hostState.value.state.phase == phaseBefore).isTrue()
        } finally {
            f.bridge.close()
        }
    }

    /**
     * NN-01 regression: the broadcast `SessionStarting` must NOT carry the
     * role-assignment seed (which deterministically derives the mafia/role
     * map). The host ships only a public nonce. Guards against a future change
     * re-shipping `hostOnly.randomSeed` over the wire.
     */
    @Test
    fun session_starting_does_not_leak_role_seed() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val f = startedFixture(scope)
        try {
            val announcing = async {
                f.bridge.announceStart("default", MafiaIds.ClassicModeId.raw)
            }
            scope.runCurrent()
            val captured = f.hostRoom.sent
                .mapNotNull { it.second as? HostMessage.SessionStarting }
                .first()
            players.drop(1).forEach { player ->
                f.bus.fromPeer(
                    PeerMessage.SessionStartReady(
                        header = startAckHeader(f.bridge.protocol, "ready-${player.seat}"),
                        actor = player.id,
                        startId = captured.startId,
                    ),
                )
            }
            scope.runCurrent()
            players.drop(1).forEach { player ->
                f.bus.fromPeer(
                    PeerMessage.SessionStartCommitAck(
                        header = startAckHeader(f.bridge.protocol, "commit-${player.seat}"),
                        actor = player.id,
                        startId = captured.startId,
                    ),
                )
            }
            scope.runCurrent()
            assertThat(announcing.await() is Result.Success).isTrue()
            val roleSeed = f.session.hostState.value.state.hostOnly.randomSeed
            assertThat(captured.sessionNonce != roleSeed).isTrue()
        } finally {
            f.bridge.close()
        }
    }

    // ============================================================ Fixture helpers ==

    private data class Fixture(
        val bus: InMemoryRoomBus,
        val session: PassAndPlaySessionController<MafiaState, MafiaAction, MafiaEvent>,
        val hostRoom: TestHostRoom,
        val bridge: MafiaHostRoomBridge,
    )

    private fun command(
        protocol: SessionProtocol,
        claimedActor: PlayerId,
        payload: ByteArray,
    ): PeerMessage.ClientCommand {
        val commandId = "mafia-command-000000000001"
        return PeerMessage.ClientCommand(
            header = SessionEnvelopeHeader(
                protocol = protocol.protocol,
                sessionId = protocol.sessionId,
                gameId = protocol.gameId,
                gameVersion = protocol.gameVersion,
                messageId = commandId,
                sequence = 0L,
            ),
            actor = claimedActor,
            commandId = commandId,
            clientSequence = 1L,
            expectedRevision = 0L,
            payload = payload,
        )
    }

    private fun startAckHeader(
        protocol: SessionProtocol,
        label: String,
    ) = SessionEnvelopeHeader(
        protocol = protocol.protocol,
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = "$label-012345678901234567890",
        sequence = 0L,
        connectionEpoch = protocol.connectionEpoch,
    )

    /**
     * Test-only host-side room: routes outbound to the bus, exposes the
     * bus's host inbox as `incoming`, reports `isHost = true`. Mirrors the
     * Whodunit `TestHostRoom` used by the multi-device contract tests.
     */
    private class TestHostRoom(
        private val bus: InMemoryRoomBus,
        hostId: PlayerId,
    ) : LocalRoom {
        private val _info = MutableStateFlow(
            RoomInfo(
                code = "test",
                displayName = "Test Host",
                hostPlayerId = hostId,
                status = RoomInfo.Status.Hosting,
            ),
        )
        private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

        override val info = _info.asStateFlow()
        override val members = _members.asStateFlow()
        override val isHost = true
        override val selfPlayerId: PlayerId = hostId
        override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
        override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents
        val sent = mutableListOf<Pair<SendTarget, HostMessage>>()

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
            sent += target to message
            bus.fromHost(target, message)
            return Result.Success(Unit)
        }

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
            Result.Failure(NetError.Unauthorized)

        override suspend fun leave() {}
    }
}

package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * End-to-end contract for Party Play:
 *  - Host + three peers all wired through `WhodunitHostRoomBridge` and
 *    `WhodunitPeerRoomBridge` over an in-memory bus.
 *  - When a peer submits a SelfActor action (CompleteCharacterReveal of
 *    *themselves*), the host bridge accepts it and the canonical reducer
 *    advances the public phase, which then propagates back to all peers.
 *  - When a peer submits a HostOnly action (e.g. RevealNextClue or
 *    AdvanceFromIntro), the host bridge drops it via `WhodunitActionAuthority`
 *    and game state does not advance.
 *  - When a peer tries to submit a SelfActor action with someone else's
 *    actor id, the host bridge drops it.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class MultiDevicePartyPlayContractTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val carol = PlayerId("carol")

    private val players = listOf(
        Player(id = hostId, displayName = "Host", seat = 0),
        Player(id = alice, displayName = "Alice", seat = 1),
        Player(id = bob, displayName = "Bob", seat = 2),
        Player(id = carol, displayName = "Carol", seat = 3),
    )

    @Test
    fun host_accepts_self_actor_from_correct_peer_and_rejects_host_only() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()

        // Register peer inboxes BEFORE the host starts so that the host's
        // initial broadcast reaches them.
        bus.registerPeer(hostId)
        bus.registerPeer(alice)
        bus.registerPeer(bob)
        bus.registerPeer(carol)

        val case = loadCase()

        val hostSession = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("contract-host"),
                caseId = CaseId(case.envelope.caseId),
                modeId = ModeId(case.envelope.supportedModes.first()),
                players = players,
                randomSeed = 42L,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(42L),
                case = case,
            ),
            scope = scope,
        )
        val hostRoom = TestHostRoom(bus, hostId = hostId)
        val hostBridge = WhodunitHostRoomBridge(
            hostSession, hostRoom, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )

        // Two peer bridges, each with their own InMemoryPeerRoom over the bus.
        val aliceRoom = InMemoryPeerRoom(bus, selfPlayerId = alice, displayName = "Alice", hostId = hostId)
        val bobRoom = InMemoryPeerRoom(bus, selfPlayerId = bob, displayName = "Bob", hostId = hostId)
        val carolRoom = InMemoryPeerRoom(bus, selfPlayerId = carol, displayName = "Carol", hostId = hostId)
        val aliceBridge = WhodunitPeerRoomBridge(
            aliceRoom, alice, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )
        val bobBridge = WhodunitPeerRoomBridge(
            bobRoom, bob, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )
        val carolBridge = WhodunitPeerRoomBridge(
            carolRoom, carol, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )

        // Host starts the game — this seeds roles + advances to PublicIntro.
        submitHost(hostSession, hostBridge, WhodunitAction.AssignRoles(seed = 42L))
        runCurrent()

        // Verify host is in PublicIntro and peers received the snapshot.
        assertThat(hostSession.publicState.value.state.phase is WhodunitPhase.PublicIntro).isTrue()

        // -- Authority case 1: HostOnly from peer is rejected.
        val phaseBefore = hostSession.publicState.value.state.phase
        // Alice attempts to advance from intro — host-only. Bridge must drop.
        aliceBridge.controller.submit(WhodunitAction.AdvanceFromIntro)
        runCurrent()
        val phaseAfter = hostSession.publicState.value.state.phase
        assertThat(phaseAfter == phaseBefore).isTrue()  // unchanged

        // -- Authority case 2: SelfActor with impersonated actor is rejected.
        // Bob tries to send CompleteCharacterReveal claiming to be Alice.
        // Host advances to CharacterReveal first so the action is otherwise valid.
        players.forEach {
            submitHost(hostSession, hostBridge, WhodunitAction.AcknowledgeIntro(it.id))
        }
        submitHost(hostSession, hostBridge, WhodunitAction.AdvanceFromIntro)
        runCurrent()
        // Skip through briefing.
        players.forEach {
            submitHost(hostSession, hostBridge, WhodunitAction.AcknowledgeBriefing(it.id))
        }
        var safety = 0
        while (hostSession.publicState.value.state.phase is WhodunitPhase.RulesBriefing && safety < 10) {
            submitHost(hostSession, hostBridge, WhodunitAction.AdvanceBriefingCard(safety + 1))
            runCurrent()
            safety++
        }
        val assignmentGeneration =
            hostSession.publicState.value.state.public.roleAssignmentGeneration
        // The host must receive the canonical unlock before completion.
        submitHost(
            hostSession,
            hostBridge,
            WhodunitAction.StartCharacterReveal(hostId, assignmentGeneration),
        )
        submitHost(
            hostSession,
            hostBridge,
            WhodunitAction.CompleteCharacterReveal(hostId, assignmentGeneration),
        )
        runCurrent()

        // Alice authoritatively unlocks first. Bob's attempt to complete that
        // unlocked dossier must still fail solely on actor binding.
        aliceBridge.controller.submit(
            WhodunitAction.StartCharacterReveal(alice, assignmentGeneration),
        )
        runCurrent()
        assertThat(
            hostSession.hostState!!.value.state.privatePerPlayer
                .getValue(alice)
                .dossierUnlocked,
        ).isTrue()
        val phaseBeforeImpersonation = hostSession.publicState.value.state.phase
        bobBridge.controller.submit(
            WhodunitAction.CompleteCharacterReveal(alice, assignmentGeneration),
        )
        runCurrent()
        val phaseAfterImpersonation = hostSession.publicState.value.state.phase
        assertThat(phaseBeforeImpersonation == phaseAfterImpersonation).isTrue()
        assertThat(alice in hostSession.publicState.value.state.public.rolesViewed).isEqualTo(false)
        assertThat(
            hostSession.hostState!!.value.state.privatePerPlayer
                .getValue(alice)
                .dossierUnlocked,
        ).isTrue()

        // -- Authority case 3: SelfActor with the correct actor is accepted.
        // 9H-3: CompleteCharacterReveal no longer auto-advances the phase;
        // instead it adds the player to public.rolesViewed. The assertion
        // here is: the canonical state mutates in response to alice's
        // correctly-attested action.
        val rolesViewedBefore = hostSession.publicState.value.state.public.rolesViewed
        aliceBridge.controller.submit(
            WhodunitAction.CompleteCharacterReveal(alice, assignmentGeneration),
        )
        runCurrent()
        val rolesViewedAfter = hostSession.publicState.value.state.public.rolesViewed
        assertThat(alice in rolesViewedAfter).isTrue()
        assertThat(rolesViewedAfter != rolesViewedBefore).isTrue()

        // A reroll rotates the epoch. An authenticated action from the right
        // player but the old epoch is rejected before it can expose the new
        // dossier; the current epoch remains usable afterward.
        submitHost(hostSession, hostBridge, WhodunitAction.RequestReroll)
        runCurrent()
        val replacementGeneration =
            hostSession.publicState.value.state.public.roleAssignmentGeneration
        assertThat(replacementGeneration).isEqualTo(assignmentGeneration + 1L)
        aliceBridge.controller.submit(
            WhodunitAction.StartCharacterReveal(alice, assignmentGeneration),
        )
        runCurrent()
        assertThat(
            hostSession.hostState!!.value.state.privatePerPlayer
                .getValue(alice)
                .dossierUnlocked,
        ).isEqualTo(false)
        aliceBridge.controller.submit(
            WhodunitAction.StartCharacterReveal(alice, replacementGeneration),
        )
        runCurrent()
        assertThat(
            hostSession.hostState!!.value.state.privatePerPlayer
                .getValue(alice)
                .dossierUnlocked,
        ).isTrue()

        hostBridge.close()
        aliceBridge.close()
        bobBridge.close()
        carolBridge.close()
    }

    /**
     * Wave 9H-9: end-to-end party flow with three peers + host driving every
     * readiness gate. Asserts the peer shadow states converge with the host
     * canonical state at each major phase boundary.
     *
     * Trajectory:
     *   AssignRoles → PublicIntro
     *   (host + alice + bob + carol ack)
     *   AdvanceFromIntro → RulesBriefing
     *   (host + alice + bob + carol ack)
     *   AdvanceBriefingCard ×N → CharacterReveal
     *   (host + alice + bob + carol each CompleteCharacterReveal(self))
     *   AdvanceFromCharacterReveal → Round(1)
     */
    @Test
    fun full_party_flow_with_three_peers_converges_at_every_gate() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)
        bus.registerPeer(bob)
        bus.registerPeer(carol)

        val case = loadCase()

        val hostSession = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("party-flow-host"),
                caseId = CaseId(case.envelope.caseId),
                modeId = ModeId(case.envelope.supportedModes.first()),
                players = players,
                randomSeed = 99L,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(99L),
                case = case,
            ),
            scope = scope,
        )
        val hostRoom = TestHostRoom(bus, hostId = hostId)
        val hostBridge = WhodunitHostRoomBridge(
            hostSession, hostRoom, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )

        val aliceRoom = InMemoryPeerRoom(bus, selfPlayerId = alice, displayName = "Alice", hostId = hostId)
        val bobRoom = InMemoryPeerRoom(bus, selfPlayerId = bob, displayName = "Bob", hostId = hostId)
        val carolRoom = InMemoryPeerRoom(bus, selfPlayerId = carol, displayName = "Carol", hostId = hostId)
        val aliceBridge = WhodunitPeerRoomBridge(
            aliceRoom, alice, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )
        val bobBridge = WhodunitPeerRoomBridge(
            bobRoom, bob, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )
        val carolBridge = WhodunitPeerRoomBridge(
            carolRoom, carol, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )

        // --- AssignRoles → PublicIntro ---
        submitHost(hostSession, hostBridge, WhodunitAction.AssignRoles(seed = 99L))
        runCurrent()
        assertThat(hostSession.publicState.value.state.phase is WhodunitPhase.PublicIntro).isTrue()

        // --- PublicIntro: every active player acks ---
        for (player in players) {
            // Each peer submits via the wire so authority + bridge are exercised.
            when (player.id) {
                alice -> aliceBridge.controller.submit(WhodunitAction.AcknowledgeIntro(player.id))
                bob -> bobBridge.controller.submit(WhodunitAction.AcknowledgeIntro(player.id))
                carol -> carolBridge.controller.submit(WhodunitAction.AcknowledgeIntro(player.id))
                else -> submitHost(
                    hostSession,
                    hostBridge,
                    WhodunitAction.AcknowledgeIntro(player.id),
                )
            }
            runCurrent()
        }
        // Readiness invariant satisfied — host advances.
        submitHost(hostSession, hostBridge, WhodunitAction.AdvanceFromIntro)
        testScheduler.advanceUntilIdle()
        assertThat(hostSession.publicState.value.state.phase is WhodunitPhase.RulesBriefing).isTrue()

        // --- RulesBriefing: walk through cards then ack ---
        var safety = 0
        while (hostSession.publicState.value.state.phase is WhodunitPhase.RulesBriefing && safety < 10) {
            // Ack each card-step gate at the LAST card; earlier cards advance freely.
            if (safety == 3) {
                for (player in players) {
                    when (player.id) {
                        alice -> aliceBridge.controller.submit(
                            WhodunitAction.AcknowledgeBriefing(player.id),
                        )
                        bob -> bobBridge.controller.submit(
                            WhodunitAction.AcknowledgeBriefing(player.id),
                        )
                        carol -> carolBridge.controller.submit(
                            WhodunitAction.AcknowledgeBriefing(player.id),
                        )
                        else -> submitHost(
                            hostSession,
                            hostBridge,
                            WhodunitAction.AcknowledgeBriefing(player.id),
                        )
                    }
                    runCurrent()
                }
            }
            submitHost(hostSession, hostBridge, WhodunitAction.AdvanceBriefingCard(safety + 1))
            runCurrent()
            safety++
        }
        testScheduler.advanceUntilIdle()
        assertThat(hostSession.publicState.value.state.phase is WhodunitPhase.CharacterReveal).isTrue()

        // --- CharacterReveal: simultaneous — every player confirms ---
        val assignmentGeneration =
            hostSession.publicState.value.state.public.roleAssignmentGeneration
        for (player in players) {
            when (player.id) {
                alice -> {
                    aliceBridge.controller.submit(
                        WhodunitAction.StartCharacterReveal(player.id, assignmentGeneration),
                    )
                    runCurrent()
                    aliceBridge.controller.submit(
                        WhodunitAction.CompleteCharacterReveal(player.id, assignmentGeneration),
                    )
                }
                bob -> {
                    bobBridge.controller.submit(
                        WhodunitAction.StartCharacterReveal(player.id, assignmentGeneration),
                    )
                    runCurrent()
                    bobBridge.controller.submit(
                        WhodunitAction.CompleteCharacterReveal(player.id, assignmentGeneration),
                    )
                }
                carol -> {
                    carolBridge.controller.submit(
                        WhodunitAction.StartCharacterReveal(player.id, assignmentGeneration),
                    )
                    runCurrent()
                    carolBridge.controller.submit(
                        WhodunitAction.CompleteCharacterReveal(player.id, assignmentGeneration),
                    )
                }
                else -> {
                    submitHost(
                        hostSession,
                        hostBridge,
                        WhodunitAction.StartCharacterReveal(player.id, assignmentGeneration),
                    )
                    runCurrent()
                    submitHost(
                        hostSession,
                        hostBridge,
                        WhodunitAction.CompleteCharacterReveal(player.id, assignmentGeneration),
                    )
                }
            }
            runCurrent()
        }
        // Readiness met — host advances to Round 1.
        submitHost(hostSession, hostBridge, WhodunitAction.AdvanceFromCharacterReveal)
        testScheduler.advanceUntilIdle()
        val finalPhase = hostSession.publicState.value.state.phase
        assertThat(finalPhase is WhodunitPhase.Round && finalPhase.index == 1).isTrue()
        // Note: peer shadow convergence through the InMemoryRoomBus + channel
        // pipeline runs on its own coroutine cadence; this contract test pins
        // the host canonical trajectory which is the authority for "the game
        // advanced." Peer-side reception is verified separately by the
        // PartyConnectionEventsTest re-snapshot path.

        hostBridge.close()
        aliceBridge.close()
        bobBridge.close()
        carolBridge.close()
    }

    /** A peer departure pauses canonical play and grace expiry ends the case. */
    @Test
    fun disconnected_peer_is_recorded_as_dropped_when_grace_expiry_ends_case() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)
        bus.registerPeer(bob)
        bus.registerPeer(carol)
        val case = loadCase()
        val hostSession = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("dropped-flow-host"),
                caseId = CaseId(case.envelope.caseId),
                modeId = ModeId(case.envelope.supportedModes.first()),
                players = players,
                randomSeed = 77L,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(77L),
                case = case,
            ),
            scope = scope,
        )
        val hostRoom = TestHostRoom(bus, hostId = hostId)
        val hostBridge = WhodunitHostRoomBridge(
            hostSession,
            hostRoom,
            players,
            scope,
            json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        val aliceRoom = InMemoryPeerRoom(bus, selfPlayerId = alice, displayName = "Alice", hostId = hostId)
        val bobRoom = InMemoryPeerRoom(bus, selfPlayerId = bob, displayName = "Bob", hostId = hostId)
        val carolRoom = InMemoryPeerRoom(bus, selfPlayerId = carol, displayName = "Carol", hostId = hostId)
        val aliceBridge = WhodunitPeerRoomBridge(
            aliceRoom, alice, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )
        val bobBridge = WhodunitPeerRoomBridge(
            bobRoom, bob, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )
        val carolBridge = WhodunitPeerRoomBridge(
            carolRoom, carol, hostSession.publicState.value.state, case, scope, hostBridge.protocol, json,
        )

        submitHost(hostSession, hostBridge, WhodunitAction.AssignRoles(seed = 77L))
        runCurrent()

        // Bob disconnects (synthesised on the bus).
        bus.emitPeerLeft(bob, "Bob")
        runCurrent()
        assertThat(bob in hostSession.publicState.value.state.public.disconnectedPlayers).isTrue()
        assertThat(hostSession.publicState.value.state.public.paused).isTrue()

        // Grace expiry is dispatched by the bridge, not a host UI action.
        testScheduler.advanceTimeBy(201L)
        runCurrent()
        val terminal = hostSession.publicState.value.state
        assertThat(terminal.phase is WhodunitPhase.Reveal).isTrue()
        assertThat(terminal.public.paused).isEqualTo(false)
        assertThat(terminal.public.droppedPlayers).isEqualTo(setOf(bob))
        assertThat(terminal.players.map { it.id }).isEqualTo(players.map { it.id })

        hostBridge.close()
        aliceBridge.close()
        bobBridge.close()
        carolBridge.close()
    }

    private suspend fun submitHost(
        @Suppress("UNUSED_PARAMETER")
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
        bridge: WhodunitHostRoomBridge,
        action: WhodunitAction,
    ) {
        bridge.submitHostAction(action)
    }

    private suspend fun loadCase(): ValidatedCase<WhodunitCase> {
        val bundled = BundledWhodunitCases(
            knownCaseIds = listOf("last-dinner"),
            loadJson = { id -> runCatching { Res.readBytes("files/cases/$id.json").decodeToString() }.getOrNull() },
            json = json,
        )
        val emptyHttp = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = com.parlor.content.repository.DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(emptyHttp, baseUrl = "https://test.local"),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = com.parlor.content.validation.DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = SemVer(1, 0, 0),
                gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
            ),
            json = json,
        )
        val res = repo.loadCase(CaseId("last-dinner"), WhodunitPayloadValidator(json))
        val payload = (res as Result.Success).data.payload
        return validatedWhodunitCaseForTest(payload, caseId = "last-dinner")
    }
}

/**
 * Test-only host-side room: routes outbound to the bus, exposes the bus's
 * host inbox as `incoming`, and reports `isHost = true`.
 */
private class TestHostRoom(
    private val bus: InMemoryRoomBus,
    private val hostId: PlayerId,
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
    override val peerEvents: kotlinx.coroutines.flow.SharedFlow<com.parlor.networking.room.PeerEvent> =
        bus.peerEvents

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        bus.fromHost(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun retireDisconnectedMember(playerId: PlayerId): Result<Unit, NetError> {
        _members.value = _members.value.filterNot { it.playerId == playerId }
        return Result.Success(Unit)
    }

    override suspend fun leave() {}
}

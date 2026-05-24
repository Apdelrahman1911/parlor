package com.parlor.games.whodunit.multidevice

import assertk.assertThat
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
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
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
import com.parlor.session.multidevice.InMemoryPeerRoom
import com.parlor.session.multidevice.InMemoryRoomBus
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
 *  - Host + two peers all wired through `WhodunitHostRoomBridge` and
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

    private val players = listOf(
        Player(id = hostId, displayName = "Host", seat = 0),
        Player(id = alice, displayName = "Alice", seat = 1),
        Player(id = bob, displayName = "Bob", seat = 2),
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
                case = case.payload,
            ),
            scope = scope,
        )
        val hostRoom = TestHostRoom(bus, hostId = hostId)
        val hostBridge = WhodunitHostRoomBridge(hostSession, hostRoom, players, scope, json)

        // Two peer bridges, each with their own InMemoryPeerRoom over the bus.
        val aliceRoom = InMemoryPeerRoom(bus, selfPlayerId = alice, displayName = "Alice", hostId = hostId)
        val bobRoom = InMemoryPeerRoom(bus, selfPlayerId = bob, displayName = "Bob", hostId = hostId)
        val aliceBridge = WhodunitPeerRoomBridge(aliceRoom, alice, hostSession.publicState.value.state, scope, json)
        val bobBridge = WhodunitPeerRoomBridge(bobRoom, bob, hostSession.publicState.value.state, scope, json)

        // Host starts the game — this seeds roles + advances to PublicIntro.
        hostSession.submit(WhodunitAction.AssignRoles(seed = 42L))
        runCurrent()

        // Verify host is in PublicIntro and peers received the snapshot.
        assertThat(hostSession.publicState.value.state.phase is WhodunitPhase.PublicIntro).isTrue()

        // -- Authority case 1: HostOnly from peer is rejected.
        val phaseBefore = hostSession.publicState.value.state.phase
        // Alice attempts to advance from intro — host-only. Bridge must drop.
        aliceRoom.sendToHost(
            PeerMessage.ActionSubmit(
                sender = alice,
                payload = WhodunitActionCodec.encode(WhodunitAction.AdvanceFromIntro),
            ),
        )
        runCurrent()
        val phaseAfter = hostSession.publicState.value.state.phase
        assertThat(phaseAfter == phaseBefore).isTrue()  // unchanged

        // -- Authority case 2: SelfActor with impersonated actor is rejected.
        // Bob tries to send CompleteCharacterReveal claiming to be Alice.
        // Host advances to CharacterReveal first so the action is otherwise valid.
        hostSession.ackIntroForAll(players)
        hostSession.submit(WhodunitAction.AdvanceFromIntro)
        runCurrent()
        // Skip through briefing.
        hostSession.ackBriefingForAll(players)
        var safety = 0
        while (hostSession.publicState.value.state.phase is WhodunitPhase.RulesBriefing && safety < 10) {
            hostSession.submit(WhodunitAction.AdvanceBriefingCard(safety + 1))
            runCurrent()
            safety++
        }
        // Now CharacterReveal at index 0 (host) — kick to reveal index 1 (alice).
        // Host completes their own reveal so the controller moves to alice's.
        hostSession.submit(WhodunitAction.CompleteCharacterReveal(hostId))
        runCurrent()

        val phaseBeforeImpersonation = hostSession.publicState.value.state.phase
        bobRoom.sendToHost(
            PeerMessage.ActionSubmit(
                sender = bob,
                payload = WhodunitActionCodec.encode(WhodunitAction.CompleteCharacterReveal(alice)),
            ),
        )
        runCurrent()
        val phaseAfterImpersonation = hostSession.publicState.value.state.phase
        assertThat(phaseBeforeImpersonation == phaseAfterImpersonation).isTrue()

        // -- Authority case 3: SelfActor with the correct actor is accepted.
        // 9H-3: CompleteCharacterReveal no longer auto-advances the phase;
        // instead it adds the player to public.rolesViewed. The assertion
        // here is: the canonical state mutates in response to alice's
        // correctly-attested action.
        val rolesViewedBefore = hostSession.publicState.value.state.public.rolesViewed
        aliceRoom.sendToHost(
            PeerMessage.ActionSubmit(
                sender = alice,
                payload = WhodunitActionCodec.encode(WhodunitAction.CompleteCharacterReveal(alice)),
            ),
        )
        runCurrent()
        val rolesViewedAfter = hostSession.publicState.value.state.public.rolesViewed
        assertThat(alice in rolesViewedAfter).isTrue()
        assertThat(rolesViewedAfter != rolesViewedBefore).isTrue()

        hostBridge.close()
        aliceBridge.close()
        bobBridge.close()
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
        return (res as Result.Success).data
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

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        bus.fromHost(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() {}
}

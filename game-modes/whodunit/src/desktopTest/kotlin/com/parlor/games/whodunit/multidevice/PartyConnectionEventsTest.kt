package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.session.multidevice.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import com.parlor.core.result.Result as PResult

/**
 * Wave 9H-5: the host bridge subscribes to LocalRoom.peerEvents and
 * translates them into canonical actions:
 *
 *  - PeerLeft → MarkPlayerDisconnected
 *  - PeerReconnected → MarkPlayerReconnected + targeted snapshot re-send
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class PartyConnectionEventsTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")
    private val players = listOf(
        Player(hostId, "Host", seat = 0),
        Player(alice, "Alice", seat = 1),
    )

    @Test
    fun peer_left_event_marks_player_disconnected() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)

        val payload = loadCase()
        val session = buildHostSession(payload)
        val hostRoom = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(session, hostRoom, players, scope, json)
        runCurrent()

        // PeerLeft → host state's disconnectedPlayers grows.
        bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        assertThat(session.publicState.value.state.public.disconnectedPlayers).contains(alice)

        bridge.close()
    }

    @Test
    fun peer_reconnected_event_clears_disconnected_and_triggers_snapshot_resend() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)

        val payload = loadCase()
        val session = buildHostSession(payload)
        val hostRoom = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(session, hostRoom, players, scope, json)
        runCurrent()

        // Alice drops off, then reconnects.
        bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        bus.emitPeerReconnected(alice, "Alice")
        runCurrent()

        // disconnectedPlayers should be empty again.
        assertThat(session.publicState.value.state.public.disconnectedPlayers)
            .doesNotContain(alice)

        bridge.close()
    }

    // ============================================================ Fixture ==

    private suspend fun loadCase(): WhodunitCase {
        val bundled = com.parlor.games.whodunit.content.BundledWhodunitCases(
            knownCaseIds = listOf("last-dinner"),
            loadJson = { id -> runCatching { Res.readBytes("files/cases/$id.json").decodeToString() }.getOrNull() },
            json = json,
        )
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val definition = WhodunitDefinition(json)
        val repo = com.parlor.content.repository.DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(emptyRemote, "https://test.local"),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = com.parlor.content.validation.DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = com.parlor.core.versioning.SemVer(1, 0, 0),
                gameRegistry = com.parlor.engine.registry.DefaultGameRegistry(listOf(definition)),
            ),
            json = json,
        )
        val r = repo.loadCase(CaseId("last-dinner"), com.parlor.games.whodunit.content.WhodunitPayloadValidator(json))
        return (r as PResult.Success).data.payload
    }

    private fun TestScope.buildHostSession(
        payload: WhodunitCase,
    ): PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent> {
        val sessionScope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("conn-events"),
                caseId = CaseId("last-dinner"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = 42L,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(42L),
                case = payload,
            ),
            scope = sessionScope,
        )
        // Drive past Setup into PublicIntro so the state has assigned roles.
        kotlinx.coroutines.runBlocking { session.submit(WhodunitAction.AssignRoles(42L)) }
        return session
    }
}

private class PartyEventsHostRoom(
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
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents

    override suspend fun send(target: SendTarget, message: HostMessage): com.parlor.core.result.Result<Unit, NetError> {
        bus.fromHost(target, message)
        return com.parlor.core.result.Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): com.parlor.core.result.Result<Unit, NetError> =
        com.parlor.core.result.Result.Failure(NetError.Unauthorized)

    override suspend fun leave() {}
}

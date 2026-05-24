package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
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
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Pass-and-play parity: a Party Play host session driven through the same
 * action sequence as a pure pass-and-play session must reach the same
 * terminal public state. Guards the rule:
 *
 *     "Pass-and-play must not regress. If any multiplayer refactor changes
 *      or breaks pass-and-play behavior, treat that as a blocker."
 *
 * The host bridge is allowed to have wire-level side effects (broadcasts)
 * but MUST NOT alter the reducer outcome.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class PartyPlayPassAndPlayParityTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val players = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
        Player(PlayerId("p3"), "Cara", seat = 2),
        Player(PlayerId("p4"), "Diego", seat = 3),
    )

    private val seed = 0xC0FFEEL

    @Test
    fun party_play_host_reaches_same_terminal_state_as_pass_and_play() = runTest {
        val payload = loadCase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val passAndPlay = buildSession(payload, TestScope(dispatcher).coroutineContext.let { CoroutineScope(dispatcher) })
        val partyPlayHost = buildSession(payload, CoroutineScope(dispatcher))

        // Same action sequence on both.
        val driver: suspend (action: WhodunitAction) -> Unit = { action ->
            passAndPlay.session.submit(action)
            partyPlayHost.session.submit(action)
            runCurrent()
        }

        // Wire the party-play host with a bridge so we exercise the production
        // path (broadcast side-effects must not interfere).
        val bus = InMemoryRoomBus()
        val bridge = WhodunitHostRoomBridge(
            controller = partyPlayHost.session,
            room = SilentHostRoom(bus, players.first().id),
            players = players,
            scope = partyPlayHost.scope,
            json = json,
        )
        runCurrent()

        playFullGame(driver, killerHint = { partyPlayHost.session.hostState!!.value.state.hostOnly.killerId })

        val ppState = passAndPlay.session.publicState.value.state
        val mpState = partyPlayHost.session.publicState.value.state

        assertThat(mpState.phase).isEqualTo(ppState.phase)
        assertThat(mpState.public).isEqualTo(ppState.public)
        // Reducer determinism: the bridge shouldn't have nudged the random
        // sequence either.
        assertThat(mpState.players).isEqualTo(ppState.players)

        bridge.close()
        passAndPlay.session.close()
        partyPlayHost.session.close()
    }

    private suspend fun playFullGame(
        driver: suspend (WhodunitAction) -> Unit,
        killerHint: () -> PlayerId,
    ) {
        // Setup → PublicIntro → RulesBriefing → CharacterReveal → Round(1..3) → FinalVote → Reveal → PostGame
        driver(WhodunitAction.AssignRoles(seed))
        // Wave 9H readiness gating: ack intro before AdvanceFromIntro,
        // ack briefing before the final AdvanceBriefingCard.
        for (player in players) driver(WhodunitAction.AcknowledgeIntro(player.id))
        driver(WhodunitAction.AdvanceFromIntro)
        for (player in players) driver(WhodunitAction.AcknowledgeBriefing(player.id))
        for (i in 1..4) driver(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            driver(WhodunitAction.StartCharacterReveal(player.id))
            driver(WhodunitAction.CompleteCharacterReveal(player.id))
        }
        driver(WhodunitAction.AdvanceFromCharacterReveal)
        for (round in 1..3) {
            driver(WhodunitAction.RevealNextClue)
            driver(WhodunitAction.StartDiscussionTimer(60))
            driver(WhodunitAction.AdvanceFromDiscussion)
        }
        val killer = killerHint()
        for (voter in players.map { it.id }) {
            driver(WhodunitAction.CastVote(voter, killer))
        }
        driver(WhodunitAction.CloseVote)
        // Reveal → PostGame.
        driver(WhodunitAction.AcknowledgeReveal)
    }

    private data class SessionHandle(
        val session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
        val scope: CoroutineScope,
    )

    private fun buildSession(payload: WhodunitCase, scope: CoroutineScope): SessionHandle {
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("parity-${scope.hashCode()}"),
                caseId = CaseId("last-dinner"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = seed,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                random = RandomSource.seeded(seed),
                case = payload,
            ),
            scope = scope,
        )
        return SessionHandle(session, scope)
    }

    private suspend fun loadCase(): WhodunitCase {
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
        val repo = DefaultCaseRepository(
            remote = KtorRemoteCaseDataSource(emptyHttp, baseUrl = "https://test.local"),
            cache = InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = SemVer(1, 0, 0),
                gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
            ),
            json = json,
        )
        val r = repo.loadCase(CaseId("last-dinner"), WhodunitPayloadValidator(json))
        return (r as Result.Success).data.payload
    }
}

/**
 * Host-side room that swallows outbound broadcasts. The bridge under test
 * sends snapshots/privates/SessionStarting via `send(...)` — we don't care
 * about the wire traffic here, just that the canonical reducer is unaffected.
 */
private class SilentHostRoom(
    private val bus: InMemoryRoomBus,
    private val hostId: PlayerId,
) : LocalRoom {
    private val _info = MutableStateFlow(RoomInfo("parity", "Host", hostId, RoomInfo.Status.Hosting))
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost = true
    override val selfPlayerId: PlayerId = hostId
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        // Swallow; no peers registered in this parity test.
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() {}
}

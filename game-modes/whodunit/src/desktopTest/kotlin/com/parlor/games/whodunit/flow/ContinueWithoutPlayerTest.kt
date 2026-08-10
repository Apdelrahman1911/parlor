package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
import com.parlor.games.whodunit.revealRolesAndAdvance
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Reducer-driven tests for Whodunit's disconnect/rejoin contract.
 *
 * A dossier is essential case information, so a live game never shrinks its
 * roster. Disconnect pauses the canonical state, rejoin preserves the exact
 * point of play, and grace-period expiry ends with a truthful early reveal.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class ContinueWithoutPlayerTest {

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

    @Test
    fun disconnect_blocks_progress_until_rejoin_and_explicit_resume() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 1L)
        session.submit(WhodunitAction.AssignRoles(seed = 1L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.MarkPlayerDisconnected(players[3].id))

        assertThat(stateOf(session).public.disconnectedPlayers).contains(players[3].id)
        assertThat(stateOf(session).public.paused).isEqualTo(true)

        // Neither phase progress nor an explicit resume is legal while a seat
        // is still disconnected.
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.submit(WhodunitAction.Resume)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)
        assertThat(stateOf(session).public.paused).isEqualTo(true)

        // Rejoin restores the seat but deliberately leaves the session paused:
        // the table must explicitly agree to resume.
        session.submit(WhodunitAction.MarkPlayerReconnected(players[3].id))
        assertThat(stateOf(session).public.disconnectedPlayers).isEmpty()
        assertThat(stateOf(session).public.paused).isEqualTo(true)
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        session.submit(WhodunitAction.Resume)
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)
    }

    @Test
    fun disconnect_freezes_round_timer_and_rejects_delayed_progress() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 2L)
        session.submit(WhodunitAction.AssignRoles(seed = 2L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))
        session.submit(WhodunitAction.MarkPlayerDisconnected(players[3].id))

        assertThat(stateOf(session).public.paused).isEqualTo(true)
        assertThat(stateOf(session).public.timer?.paused).isEqualTo(true)
        assertThat(stateOf(session).public.timer?.remainingSeconds).isEqualTo(180)

        // In-flight messages from before the disconnect cannot consume time or
        // advance the round.
        session.submit(WhodunitAction.TimerTicked(12))
        session.submit(WhodunitAction.TimerExpired)
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        assertThat((phaseOf(session) as WhodunitPhase.Round).index).isEqualTo(1)
        assertThat(stateOf(session).public.timer?.remainingSeconds).isEqualTo(180)

        session.submit(WhodunitAction.MarkPlayerReconnected(players[3].id))
        session.submit(WhodunitAction.Resume)
        assertThat(stateOf(session).public.timer?.paused).isEqualTo(false)
        // The host may explicitly end the discussion after recovery. A timer
        // expiry is reserved for the ticker's terminal one-second edge.
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        assertThat((phaseOf(session) as WhodunitPhase.Round).index).isEqualTo(2)
    }

    @Test
    fun grace_expiry_reveals_case_marks_missing_seat_and_never_shrinks_roster() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 3L)
        session.submit(WhodunitAction.AssignRoles(seed = 3L))
        session.submit(WhodunitAction.MarkPlayerDisconnected(players[3].id))
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))

        val state = stateOf(session)
        assertThat(state.phase).isInstanceOf(WhodunitPhase.Reveal::class)
        assertThat(state.public.paused).isEqualTo(false)
        assertThat(state.public.timer).isEqualTo(null)
        assertThat(state.public.disconnectedPlayers).isEmpty()
        assertThat(state.public.droppedPlayers).contains(players[3].id)
        assertThat(state.players.map { it.id }).containsExactlyInAnyOrder(*players.map { it.id }.toTypedArray())
        assertThat(state.public.verdict as Any).isInstanceOf(Verdict.KillerWins::class)
        assertThat((state.public.verdict as Verdict.KillerWins).cause)
            .isEqualTo(KillerWinCause.GameEndedEarly)
    }

    @Test
    fun grace_expiry_during_reveal_completes_terminal_flow_and_blocks_replay() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 31L)
        session.submit(WhodunitAction.AssignRoles(seed = 31L))
        session.submit(WhodunitAction.EndGameEarly(withReveal = true))
        assertThat(phaseOf(session)).isEqualTo(WhodunitPhase.Reveal)

        val missing = players.last().id
        session.submit(WhodunitAction.MarkPlayerDisconnected(missing))
        session.submit(WhodunitAction.ContinueWithoutPlayer(missing))

        assertThat(phaseOf(session)).isEqualTo(WhodunitPhase.PostGame)
        assertThat(stateOf(session).public.disconnectedPlayers).isEmpty()
        assertThat(stateOf(session).public.droppedPlayers).contains(missing)
        val terminal = stateOf(session)
        session.submit(WhodunitAction.BeginReplay)
        assertThat(stateOf(session)).isEqualTo(terminal)
    }

    @Test
    fun post_game_disconnect_is_ignored_instead_of_creating_permanent_overlay_state() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 32L)
        session.submit(WhodunitAction.AssignRoles(seed = 32L))
        session.submit(WhodunitAction.EndGameEarly(withReveal = false))
        val terminal = stateOf(session)

        session.submit(WhodunitAction.MarkPlayerDisconnected(players.last().id))

        assertThat(stateOf(session)).isEqualTo(terminal)
    }

    @Test
    fun grace_expiry_action_is_rejected_without_a_current_disconnect() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 4L)
        session.submit(WhodunitAction.AssignRoles(seed = 4L))
        val before = stateOf(session)

        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))

        assertThat(stateOf(session)).isEqualTo(before)
    }

    @Test
    fun duplicate_and_unknown_disconnect_events_are_idempotent() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 5L)
        session.submit(WhodunitAction.AssignRoles(seed = 5L))
        session.submit(WhodunitAction.MarkPlayerDisconnected(players[1].id))
        val afterFirst = stateOf(session)

        session.submit(WhodunitAction.MarkPlayerDisconnected(players[1].id))
        session.submit(WhodunitAction.MarkPlayerDisconnected(PlayerId("unknown")))

        assertThat(stateOf(session)).isEqualTo(afterFirst)
    }

    // ============================================================ Fixture ==

    private suspend fun loadCase(): WhodunitCase {
        val bundled = BundledWhodunitCases(
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
        val repo = DefaultCaseRepository(
            remote = KtorRemoteCaseDataSource(client = emptyRemote, baseUrl = "https://test.local"),
            cache = InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = SemVer(1, 0, 0),
                gameRegistry = DefaultGameRegistry(listOf(definition)),
            ),
            json = json,
        )
        val result = repo.loadCase(CaseId("last-dinner"), WhodunitPayloadValidator(json))
        return (result as Result.Success).data.payload
    }

    private fun TestScope.buildSession(
        payload: WhodunitCase,
        modeId: ModeId,
        players: List<Player>,
        seed: Long,
    ): Pair<PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>, CoroutineScope> {
        val sessionScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("dropped-$seed"),
                caseId = CaseId("last-dinner"),
                modeId = modeId,
                players = players,
                randomSeed = seed,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                random = RandomSource.seeded(seed),
                case = validatedWhodunitCaseForTest(payload, caseId = "last-dinner"),
            ),
            scope = sessionScope,
        )
        return session to sessionScope
    }

    private fun phaseOf(session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>) =
        session.publicState.value.state.phase

    private fun stateOf(session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>) =
        session.publicState.value.state
}

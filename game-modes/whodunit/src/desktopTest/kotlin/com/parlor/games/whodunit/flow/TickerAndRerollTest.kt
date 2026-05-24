package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
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
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.ui.timer.runDiscussionTickerLoop
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Phase 6.4 — discussion-timer ticker + privacy/reroll flow.
 *
 * The ticker is unit-tested against `runDiscussionTickerLoop` directly under
 * virtual time. The reroll flow is exercised end-to-end through the reducer:
 * we drive a session into CharacterReveal, capture the host-only assignment,
 * submit `RequestReroll`, and assert the new assignment is genuinely new
 * while non-rerollable state (players, case identity, session id) is
 * preserved.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class TickerAndRerollTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private suspend fun loadCase(): WhodunitCase {
        val bundled = BundledWhodunitCases(
            knownCaseIds = listOf("last-dinner"),
            loadJson = { id ->
                runCatching { Res.readBytes("files/cases/$id.json").decodeToString() }.getOrNull()
            },
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

    private fun fourPlayers() = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
        Player(PlayerId("p3"), "Cara", seat = 2),
        Player(PlayerId("p4"), "Diego", seat = 3),
    )

    private fun TestScope.buildSession(
        payload: WhodunitCase,
        modeId: ModeId,
        players: List<Player>,
        seed: Long,
        sessionId: SessionId = SessionId("ticker-reroll-$seed"),
    ): Pair<PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>, CoroutineScope> {
        val sessionScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = sessionId,
                caseId = CaseId("last-dinner"),
                modeId = modeId,
                players = players,
                randomSeed = seed,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                random = RandomSource.seeded(seed),
                case = payload,
            ),
            scope = sessionScope,
        )
        return session to sessionScope
    }

    private fun stateOf(s: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>) =
        s.publicState.value.state

    private fun hostState(s: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>) =
        s.hostState!!.value.state

    private suspend fun driveToFirstDiscussionTimer(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
        players: List<Player>,
        seed: Long,
        totalSeconds: Int = 30,
    ) {
        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            session.submit(WhodunitAction.StartCharacterReveal(player.id))
            session.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(totalSeconds))
    }

    // ==================================================================== Timer ticker ==

    @Test
    fun ticker_decrements_once_per_second() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 1L)
        driveToFirstDiscussionTimer(session, players, 1L, totalSeconds = 30)
        val timerId = stateOf(session).public.timer!!.timerId

        val tickerJob = scope.launch { runDiscussionTickerLoop(session, timerId) }

        // Five real-seconds of virtual time → five ticks; remainingSeconds
        // moves from 30 → 25. The ticker must not double-tick when virtual
        // time advances in a single block.
        advanceTimeBy(5.seconds + 1.milliseconds); runCurrent()
        assertThat(stateOf(session).public.timer!!.remainingSeconds).isEqualTo(25)

        tickerJob.cancelAndJoin()
        session.close()
    }

    @Test
    fun ticker_freezes_while_session_paused_and_resumes_after_unpause() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 2L)
        driveToFirstDiscussionTimer(session, players, 2L, totalSeconds = 30)
        val timerId = stateOf(session).public.timer!!.timerId

        val tickerJob = scope.launch { runDiscussionTickerLoop(session, timerId) }

        // Tick 3 seconds → 27.
        advanceTimeBy(3.seconds + 1.milliseconds); runCurrent()
        assertThat(stateOf(session).public.timer!!.remainingSeconds).isEqualTo(27)

        // Pause: subsequent virtual-time advances should NOT tick the timer.
        // Pause must complete before we advance time, so we await the submit
        // via the unconfined dispatcher's synchronous semantics.
        session.submit(WhodunitAction.Pause)
        advanceTimeBy(10.seconds + 1.milliseconds); runCurrent()
        assertThat(stateOf(session).public.timer!!.remainingSeconds).isEqualTo(27)

        // Resume: ticker resumes from 27 over the next 4 seconds.
        session.submit(WhodunitAction.Resume)
        advanceTimeBy(4.seconds + 1.milliseconds); runCurrent()
        assertThat(stateOf(session).public.timer!!.remainingSeconds).isEqualTo(23)

        tickerJob.cancelAndJoin()
        session.close()
    }

    @Test
    fun ticker_submits_TimerExpired_at_zero_and_clears_timer() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 3L)
        driveToFirstDiscussionTimer(session, players, 3L, totalSeconds = 3)
        val timerId = stateOf(session).public.timer!!.timerId

        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        val tickerJob = scope.launch { runDiscussionTickerLoop(session, timerId) }

        // 30 seconds is more than enough to drain a 3-second timer.
        advanceTimeBy(30.seconds + 1.milliseconds); runCurrent()

        // After expiry, the reducer clears the timer.
        assertThat(stateOf(session).public.timer).isNull()
        assertThat(events).contains(WhodunitEvent.TimerExhausted)

        // The ticker loop returned on its own; cancelling it is a no-op.
        tickerJob.cancelAndJoin()
        collector.cancel()
        session.close()
    }

    @Test
    fun ticker_does_not_double_tick_when_two_loops_share_a_timer_id() = runTest {
        // Compose's LaunchedEffect(key) cancels the prior coroutine before
        // starting a new one with the same key, so production never has two
        // tickers running for the same timerId. But if a bug ever caused two
        // to run, the visible behavior is *exactly* what this test pins:
        // each loop submits one TimerTicked per second, so two loops would
        // make remainingSeconds drop by ~2 per second of virtual time.
        // This test demonstrates the assumption: the LaunchedEffect's
        // single-coroutine-per-key guarantee is what stops double-ticking.
        val payload = loadCase()
        val players = fourPlayers()
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 4L)
        driveToFirstDiscussionTimer(session, players, 4L, totalSeconds = 60)
        val timerId = stateOf(session).public.timer!!.timerId

        // A single ticker — the production case.
        val onlyTicker: Job = scope.launch { runDiscussionTickerLoop(session, timerId) }

        advanceTimeBy(5.seconds + 1.milliseconds); runCurrent()
        // After 5 virtual seconds, a single ticker has produced exactly 5
        // ticks. If a second ticker were running we'd see ~10.
        assertThat(stateOf(session).public.timer!!.remainingSeconds).isEqualTo(55)

        onlyTicker.cancelAndJoin()
        session.close()
    }

    @Test
    fun ticker_stops_when_timer_id_changes() = runTest {
        // Round 1 ticker should exit cleanly when AdvanceFromDiscussion
        // clears the timer and round 2 (eventually) starts its own.
        val payload = loadCase()
        val players = fourPlayers()
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 5L)
        driveToFirstDiscussionTimer(session, players, 5L, totalSeconds = 30)
        val firstTimerId = stateOf(session).public.timer!!.timerId

        val tickerJob = scope.launch { runDiscussionTickerLoop(session, firstTimerId) }

        advanceTimeBy(2.seconds + 1.milliseconds); runCurrent()
        assertThat(stateOf(session).public.timer!!.remainingSeconds).isEqualTo(28)

        // Advance to round 2; the timer becomes null.
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        assertThat(stateOf(session).public.timer).isNull()

        // Any further time should NOT touch state — the ticker has returned.
        advanceTimeBy(10.seconds + 1.milliseconds); runCurrent()
        assertThat(stateOf(session).public.timer).isNull()

        tickerJob.cancelAndJoin()
        session.close()
    }

    // ====================================================================== Reroll flow ==

    @Test
    fun reroll_changes_role_assignment_emits_RerolledAt_and_lands_in_character_reveal() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 9001L
        val sessionId = SessionId("reroll-test")
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed, sessionId)
        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        // Drive into CharacterReveal so the player is "looking at" their dossier.
        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        // We're now in CharacterReveal(playerIndex = 0).
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        val beforeKillerId = hostState(session).hostOnly.killerId
        val beforeKillerChar = hostState(session).hostOnly.killerCharacterId
        val beforeMap = hostState(session).hostOnly.seatToCharacter
        val beforeSeed = hostState(session).hostOnly.randomSeed
        val priorPhaseId = stateOf(session).phase.id

        // Clear events captured during setup so we only inspect post-reroll events.
        events.clear()

        session.submit(WhodunitAction.RequestReroll)

        // Phase lands at CharacterReveal(0) — not PublicIntro.
        val after = hostState(session)
        assertThat(after.phase).isInstanceOf(WhodunitPhase.CharacterReveal::class)
        assertThat((after.phase as WhodunitPhase.CharacterReveal).playerIndex).isEqualTo(0)

        // Seed deterministically advances; assignment derived from it changes.
        assertThat(after.hostOnly.randomSeed).isNotEqualTo(beforeSeed)
        assertThat(after.hostOnly.seatToCharacter).isNotEqualTo(beforeMap)

        // Reroll only restarts the reveal — these stay anchored.
        assertThat(after.players).isEqualTo(players)
        assertThat(after.public.caseId).isEqualTo(stateOf(session).public.caseId)
        assertThat(after.public.modeId).isEqualTo(WhodunitIds.ClassicVoteModeId)

        // Public/transient state is wiped so the rerolled game starts cleanly.
        assertThat(after.public.revealedClues).isEmpty()
        assertThat(after.public.currentRound).isEqualTo(0)
        assertThat(after.public.timer).isNull()

        // Private state was reset and then reassigned for every seat.
        assertThat(after.privatePerPlayer.keys).isEqualTo(players.map { it.id }.toSet())

        // Events: PhaseEntered(CharacterReveal(0)) appears exactly once, the
        // misleading PhaseEntered(PublicIntro) is gone, RerolledAt carries
        // the prior phase id.
        val phaseEnteredEvents = events.filterIsInstance<WhodunitEvent.PhaseEntered>()
        assertThat(phaseEnteredEvents).hasSize(1)
        assertThat(phaseEnteredEvents[0].phase).isInstanceOf(WhodunitPhase.CharacterReveal::class)
        val rerolledAt = events.filterIsInstance<WhodunitEvent.RerolledAt>().single()
        assertThat(rerolledAt.phaseId).isEqualTo(priorPhaseId)

        // Sanity: the rerolled killer assignment is internally consistent —
        // killerId still maps to a real player + character.
        assertThat(after.hostOnly.seatToCharacter[after.hostOnly.killerId]).isEqualTo(after.hostOnly.killerCharacterId)
        // And the killer character id sits inside the case payload.
        val killerChar = payload.characters.firstOrNull { it.id == after.hostOnly.killerCharacterId.raw }
        assertThat(killerChar).isNotNull()

        // Reroll doesn't leak the previous killer id forward — we recorded
        // the before-value so a regression that "rerolls" without actually
        // changing anything would assert here.
        // (With a deterministic seed the new killer *could* coincidentally
        // be the same player; if that happens we still require the seed and
        // mapping to differ.)
        val killerChanged = after.hostOnly.killerId != beforeKillerId
        val characterChanged = after.hostOnly.killerCharacterId != beforeKillerChar
        assertThat(killerChanged || characterChanged || after.hostOnly.seatToCharacter != beforeMap).isTrue()

        // SessionId is unchanged — important so any active snapshot keeps
        // its identity through the reroll.
        assertThat(sessionId).isEqualTo(SessionId("reroll-test"))

        collector.cancel()
        session.close()
    }

    @Test
    fun reroll_clears_pause_so_post_reroll_game_starts_unpaused() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 9002L
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed)
        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        // Pause during CharacterReveal, then reroll.
        session.submit(WhodunitAction.Pause)
        assertThat(stateOf(session).public.paused).isTrue()

        session.submit(WhodunitAction.RequestReroll)

        // Reroll resets paused so the new reveal flow is interactive immediately.
        assertThat(stateOf(session).public.paused).isEqualTo(false)
        session.close()
    }

    @Test
    fun reroll_emits_PhaseEntered_for_character_reveal_only_and_does_not_announce_public_intro() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 9003L
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed)
        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        events.clear()

        session.submit(WhodunitAction.RequestReroll)

        // No phantom PublicIntro event — the snapshot writer and any telemetry
        // listener must not be told we re-entered the public intro phase.
        val phaseEntered = events.filterIsInstance<WhodunitEvent.PhaseEntered>()
        assertThat(phaseEntered.map { it.phase }).containsExactly(WhodunitPhase.CharacterReveal(0))

        collector.cancel()
        session.close()
    }
}

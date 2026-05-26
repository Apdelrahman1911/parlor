package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.ids.CaseId
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
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * End-to-end reducer drive: load The Last Dinner, build a session, submit the
 * exact action sequence the UI would, and assert the reducer reaches Reveal +
 * PostGame.
 *
 * This is the Phase 5 acceptance bar from `docs/APP_PLAN.md` §5: a full Classic
 * Vote game plays from setup to reveal. Elimination is exercised as a separate
 * test below to confirm the per-round vote → eliminate → continue loop and the
 * killer-voted-out-immediately ends-game path.
 *
 * Uses [UnconfinedTestDispatcher] so the session's derived `publicState`
 * (built with `stateIn(scope, Eagerly, ...)`) updates synchronously between
 * `submit(...)` and the subsequent `.value` read — `StandardTestDispatcher`
 * would queue the upstream map and leave a stale cached value.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class FullGameDriveTest {

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
        val definition = WhodunitDefinition(json)
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(definition)),
        )
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = DefaultCaseRepository(
            remote = KtorRemoteCaseDataSource(client = emptyRemote, baseUrl = "https://test.local"),
            cache = InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = validator,
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

    private fun fivePlayers() = fourPlayers() + Player(PlayerId("p5"), "Esme", seat = 4)

    private fun TestScope.buildSession(
        payload: WhodunitCase,
        modeId: com.parlor.core.ids.ModeId,
        players: List<Player>,
        seed: Long,
    ): Pair<PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>, CoroutineScope> {
        val sessionScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("s-$seed"),
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

    private fun phaseOf(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    ): WhodunitPhase = session.publicState.value.state.phase

    private fun stateOf(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    ): WhodunitState = session.publicState.value.state

    private fun hostState(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    ): WhodunitState = session.hostState!!.value.state

    @Test
    fun classic_vote_four_player_game_reaches_reveal_with_a_verdict() = runTest {
        val payload = loadCase()
        val seed = 1234567L
        val players = fourPlayers()
        val (session, sessionScope) = buildSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed,
        )

        val events = mutableListOf<WhodunitEvent>()
        val collector = sessionScope.launch { session.events.collect { events += it } }

        // AssignRoles → PublicIntro
        session.submit(WhodunitAction.AssignRoles(seed))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        // AdvanceFromIntro → RulesBriefing
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)

        // Four briefing cards → CharacterReveal(0)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        // Per-player reveal → Round(1)
        session.revealRolesAndAdvance(players)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.Round::class)

        // Three rounds for a 4-player Classic game → FinalVote
        for (roundIndex in 1..3) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(60))
            session.submit(WhodunitAction.AdvanceFromDiscussion)
        }
        val atVote = stateOf(session)
        assertThat(atVote.phase).isInstanceOf(WhodunitPhase.FinalVote::class)
        assertThat(atVote.public.voteState).isInstanceOf(VoteState.Collecting::class)

        // Everyone votes for the actual killer → PlayersWin
        val killerId = hostState(session).hostOnly.killerId
        val ballot = (atVote.public.voteState as VoteState.Collecting).ballotPlayerIds
        for (voter in ballot) session.submit(WhodunitAction.CastVote(voter, killerId))
        session.submit(WhodunitAction.CloseVote)

        val final = stateOf(session)
        assertThat(final.phase).isInstanceOf(WhodunitPhase.Reveal::class)
        assertThat((final.public.voteState as VoteState.Resolved).wasKiller).isTrue()
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.PlayersWin::class)

        // AcknowledgeReveal → PostGame
        session.submit(WhodunitAction.AcknowledgeReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PostGame::class)

        collector.cancel()
        session.close()
    }

    @Test
    fun classic_vote_innocent_accusation_yields_killer_wins_verdict() = runTest {
        val payload = loadCase()
        val seed = 99L
        val players = fourPlayers()
        val (session, sessionScope) = buildSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed,
        )

        val events = mutableListOf<WhodunitEvent>()
        val collector = sessionScope.launch { session.events.collect { events += it } }

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        for (roundIndex in 1..3) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(60))
            session.submit(WhodunitAction.AdvanceFromDiscussion)
        }

        val killerId = hostState(session).hostOnly.killerId
        val innocent = players.first { it.id != killerId }.id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds
        for (voter in ballot) session.submit(WhodunitAction.CastVote(voter, innocent))
        session.submit(WhodunitAction.CloseVote)

        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.KillerWins::class)

        collector.cancel()
        session.close()
    }

    @Test
    fun elimination_killer_voted_out_first_round_ends_game_immediately() = runTest {
        val payload = loadCase()
        val seed = 4242L
        val players = fivePlayers()
        val (session, sessionScope) = buildSession(
            payload, WhodunitIds.EliminationModeId, players, seed,
        )

        val events = mutableListOf<WhodunitEvent>()
        val collector = sessionScope.launch { session.events.collect { events += it } }

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)

        // Round 1 in Elimination mode: discussion advance opens a vote.
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(30))
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        assertThat(stateOf(session).public.voteState).isInstanceOf(VoteState.Collecting::class)

        // Vote out the killer immediately.
        val killerId = hostState(session).hostOnly.killerId
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds
        for (voter in ballot) session.submit(WhodunitAction.CastVote(voter, killerId))
        session.submit(WhodunitAction.CloseVote)

        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.Reveal::class)
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.PlayersWin::class)

        collector.cancel()
        session.close()
    }

    @Test
    fun elimination_innocent_voted_out_holds_on_announcement_until_acknowledged() = runTest {
        // Design doc §13: when an innocent is eliminated and the game continues,
        // the app must surface "[Name] was innocent. The killer is still among
        // you." before the next round opens. The reducer expresses that as a
        // hold on voteState = Resolved(wasKiller = false); AcknowledgeRevealCard
        // is the table's tap-through.
        val payload = loadCase()
        val seed = 8888L
        val players = fivePlayers()
        val (session, sessionScope) = buildSession(
            payload, WhodunitIds.EliminationModeId, players, seed,
        )

        val events = mutableListOf<WhodunitEvent>()
        val collector = sessionScope.launch { session.events.collect { events += it } }

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)

        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(30))
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        assertThat(stateOf(session).public.voteState).isInstanceOf(VoteState.Collecting::class)

        // Everyone votes for an innocent (not the killer).
        val killerId = hostState(session).hostOnly.killerId
        val innocent = players.first { it.id != killerId }.id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds
        for (voter in ballot) session.submit(WhodunitAction.CastVote(voter, innocent))
        session.submit(WhodunitAction.CloseVote)

        // The reducer holds on the announcement: phase still Round(1),
        // voteState Resolved(wasKiller = false), the innocent is in
        // eliminatedPlayers. No PhaseEntered(Round(2)) was emitted yet.
        val holding = stateOf(session)
        assertThat(holding.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat((holding.phase as WhodunitPhase.Round).index).isEqualTo(1)
        val resolved = holding.public.voteState as VoteState.Resolved
        assertThat(resolved.accusedPlayerId).isEqualTo(innocent)
        assertThat(resolved.wasKiller).isEqualTo(false)
        assertThat(holding.public.eliminatedPlayers.contains(innocent)).isTrue()
        val eliminatedEvent = events.filterIsInstance<WhodunitEvent.PlayerEliminated>().last()
        assertThat(eliminatedEvent.wasKiller).isEqualTo(false)

        // Host taps through → next round opens with a fresh ballot.
        session.submit(WhodunitAction.AcknowledgeRevealCard)
        val advanced = stateOf(session)
        assertThat(advanced.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat((advanced.phase as WhodunitPhase.Round).index).isEqualTo(2)
        assertThat(advanced.public.voteState).isEqualTo(VoteState.Idle)
        // The innocent stays in eliminatedPlayers — they are audience now.
        assertThat(advanced.public.eliminatedPlayers.contains(innocent)).isTrue()

        collector.cancel()
        session.close()
    }

    @Test
    fun replay_after_reveal_reseeds_and_returns_to_public_intro() = runTest {
        val payload = loadCase()
        val seed = 7777L
        val players = fourPlayers()
        val (session, _) = buildSession(
            payload, WhodunitIds.ClassicVoteModeId, players, seed,
        )

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        for (roundIndex in 1..3) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(30))
            session.submit(WhodunitAction.AdvanceFromDiscussion)
        }
        val killerId = hostState(session).hostOnly.killerId
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds
        for (voter in ballot) session.submit(WhodunitAction.CastVote(voter, killerId))
        session.submit(WhodunitAction.CloseVote)
        session.submit(WhodunitAction.AcknowledgeReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PostGame::class)

        val firstSeed = hostState(session).hostOnly.randomSeed

        session.submit(WhodunitAction.BeginReplay)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)
        val after = stateOf(session)
        assertThat(after.public.revealedClues.isEmpty()).isTrue()
        assertThat(after.public.voteState).isEqualTo(VoteState.Idle)
        val replaySeed = hostState(session).hostOnly.randomSeed
        assertThat(replaySeed == firstSeed).isEqualTo(false)

        session.close()
    }
}

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
import com.parlor.games.whodunit.domain.event.KillerWinCause
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
 * Tied-revote outcomes per design doc §12 / §13.
 *
 * The previous reducer carried the "second tie" marker on `VoteState.Tied`,
 * but the marker was read inside `handleTie` from `state.public.voteState`,
 * which at close-time is `Collecting` — not `Tied`. The flag was therefore
 * always false and the second-tie outcome paths were unreachable. This test
 * pins the fix: the marker now travels on `VoteState.Collecting.isSecondRound`,
 * set by `openVote` whenever it runs from a `Tied` state.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class TiedRevoteTest {

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
                sessionId = SessionId("tied-test-$seed"),
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

    private fun stateOf(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    ): WhodunitState = session.publicState.value.state

    private fun hostState(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    ): WhodunitState = session.hostState!!.value.state

    /** Drive setup through to the open vote ballot in a 4-player Classic game. */
    private suspend fun driveToClassicVote(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
        players: List<Player>,
        seed: Long,
    ) {
        session.submit(WhodunitAction.AssignRoles(seed))
        session.submit(WhodunitAction.AdvanceFromIntro)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            session.submit(WhodunitAction.StartCharacterReveal(player.id))
            session.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }
        for (roundIndex in 1..3) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(30))
            session.submit(WhodunitAction.AdvanceFromDiscussion)
        }
    }

    @Test
    fun classic_two_consecutive_ties_yields_killer_wins_unresolved() = runTest {
        val payload = loadCase()
        val seed = 13L
        val players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
            Player(PlayerId("p4"), "Diego", seat = 3),
        )
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed)
        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        driveToClassicVote(session, players, seed)
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.FinalVote::class)

        val killer = hostState(session).hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id
        val targetB = innocents[1].id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // First round: 2-2 tie between targetA and targetB.
        session.submit(WhodunitAction.CastVote(ballot[0], targetA))
        session.submit(WhodunitAction.CastVote(ballot[1], targetA))
        session.submit(WhodunitAction.CastVote(ballot[2], targetB))
        session.submit(WhodunitAction.CastVote(ballot[3], targetB))
        session.submit(WhodunitAction.CloseVote)

        // We're in TiedRevote with a Tied voteState.
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)
        val tied = stateOf(session).public.voteState as VoteState.Tied
        assertThat(tied.tiedPlayerIds.toSet()).isEqualTo(setOf(targetA, targetB))

        // Open the revote: Collecting.isSecondRound must be true (the bug fix).
        session.submit(WhodunitAction.OpenVote)
        val secondCollecting = stateOf(session).public.voteState as VoteState.Collecting
        assertThat(secondCollecting.isSecondRound).isTrue()

        // Second round: tie again with the same split.
        session.submit(WhodunitAction.CastVote(ballot[0], targetA))
        session.submit(WhodunitAction.CastVote(ballot[1], targetA))
        session.submit(WhodunitAction.CastVote(ballot[2], targetB))
        session.submit(WhodunitAction.CastVote(ballot[3], targetB))
        session.submit(WhodunitAction.CloseVote)

        // Killer wins via TieUnresolved; phase moves to Reveal.
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.Reveal::class)
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.KillerWins::class)
        assertThat((verdict as Verdict.KillerWins).cause).isEqualTo(KillerWinCause.TieUnresolved)

        collector.cancel()
        session.close()
    }

    @Test
    fun elimination_two_consecutive_ties_advances_round_without_elimination() = runTest {
        val payload = loadCase()
        val seed = 55L
        // 5 voters: 2 + 2 with one abstention lets us reproduce a tie cleanly.
        val players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
            Player(PlayerId("p4"), "Diego", seat = 3),
            Player(PlayerId("p5"), "Esme", seat = 4),
        )
        val (session, _) = buildSession(payload, WhodunitIds.EliminationModeId, players, seed)

        session.submit(WhodunitAction.AssignRoles(seed))
        session.submit(WhodunitAction.AdvanceFromIntro)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            session.submit(WhodunitAction.StartCharacterReveal(player.id))
            session.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }

        // Round 1: reveal clue, discussion advance opens an Elimination vote.
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(30))
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        val firstCollecting = stateOf(session).public.voteState as VoteState.Collecting

        val killer = hostState(session).hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id
        val targetB = innocents[1].id
        val ballot = firstCollecting.ballotPlayerIds

        // 2-2 tie with one abstention.
        session.submit(WhodunitAction.CastVote(ballot[0], targetA))
        session.submit(WhodunitAction.CastVote(ballot[1], targetA))
        session.submit(WhodunitAction.CastVote(ballot[2], targetB))
        session.submit(WhodunitAction.CastVote(ballot[3], targetB))
        session.submit(WhodunitAction.AbstainVote(ballot[4]))
        session.submit(WhodunitAction.CloseVote)

        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)

        // Second tied revote.
        session.submit(WhodunitAction.OpenVote)
        assertThat((stateOf(session).public.voteState as VoteState.Collecting).isSecondRound).isTrue()
        session.submit(WhodunitAction.CastVote(ballot[0], targetA))
        session.submit(WhodunitAction.CastVote(ballot[1], targetA))
        session.submit(WhodunitAction.CastVote(ballot[2], targetB))
        session.submit(WhodunitAction.CastVote(ballot[3], targetB))
        session.submit(WhodunitAction.AbstainVote(ballot[4]))
        session.submit(WhodunitAction.CloseVote)

        // Per design doc §13: no one eliminated, advance to next round with a
        // fresh ballot (voteState reset to Idle).
        val afterDoubleTie = stateOf(session)
        assertThat(afterDoubleTie.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat((afterDoubleTie.phase as WhodunitPhase.Round).index).isEqualTo(2)
        assertThat(afterDoubleTie.public.voteState).isEqualTo(VoteState.Idle)
        assertThat(afterDoubleTie.public.eliminatedPlayers.isEmpty()).isTrue()

        session.close()
    }
}

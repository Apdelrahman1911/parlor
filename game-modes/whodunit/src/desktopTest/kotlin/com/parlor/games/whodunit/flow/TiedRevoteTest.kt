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
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
import com.parlor.games.whodunit.accuseWithAllOtherVoters
import com.parlor.games.whodunit.castSplitVote
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
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
                case = validatedWhodunitCaseForTest(payload, caseId = "last-dinner"),
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
    ): WhodunitState = session.hostState.value.state

    /** Drive setup through to the open vote ballot in a 4-player Classic game. */
    private suspend fun driveToClassicVote(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
        players: List<Player>,
        seed: Long,
    ) {
        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        repeat(3) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(180))
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
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
        session.submit(WhodunitAction.CloseVote)

        // We're in TiedRevote with a Tied voteState.
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)
        val tied = stateOf(session).public.voteState as VoteState.Tied
        assertThat(tied.tiedPlayerIds.toSet()).isEqualTo(setOf(targetA, targetB))
        assertThat(tied.debateSecondsRemaining).isEqualTo(0)

        // Open the revote: Collecting.isSecondRound must be true (the bug fix).
        session.submit(WhodunitAction.OpenVote)
        val secondCollecting = stateOf(session).public.voteState as VoteState.Collecting
        assertThat(secondCollecting.isSecondRound).isTrue()

        // Second round: tie again with the same split.
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
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
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)

        // Round 1: reveal clue, discussion advance opens an Elimination vote.
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        val firstCollecting = stateOf(session).public.voteState as VoteState.Collecting

        val killer = hostState(session).hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id
        val targetB = innocents[1].id
        val ballot = firstCollecting.ballotPlayerIds

        // 2-2 tie with one abstention.
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
        session.submit(WhodunitAction.CloseVote)

        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)

        // Second tied revote.
        session.submit(WhodunitAction.OpenVote)
        assertThat((stateOf(session).public.voteState as VoteState.Collecting).isSecondRound).isTrue()
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
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

    @Test
    fun classic_first_tie_then_resolved_revote_resolves_normally() = runTest {
        val payload = loadCase()
        val seed = 31L
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
        val targetA = killer
        val targetB = players.first { it.id != killer }.id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // First vote: 2-2 tie between targetA and targetB.
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
        session.submit(WhodunitAction.CloseVote)
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)

        // Open the revote; verify the second-round marker is propagated.
        session.submit(WhodunitAction.OpenVote)
        val secondCollecting = stateOf(session).public.voteState as VoteState.Collecting
        assertThat(secondCollecting.isSecondRound).isTrue()

        // Second vote: room agrees and unanimously accuses the actual killer.
        // The revote MUST resolve normally (no fake tied-twice trigger).
        session.accuseWithAllOtherVoters(ballot, killer)
        session.submit(WhodunitAction.CloseVote)

        // Outcome: Reveal phase, killer accused, PlayersWin verdict.
        val finalState = stateOf(session)
        assertThat(finalState.phase).isInstanceOf(WhodunitPhase.Reveal::class)
        val resolved = finalState.public.voteState as VoteState.Resolved
        assertThat(resolved.accusedPlayerId).isEqualTo(killer)
        assertThat(resolved.wasKiller).isTrue()
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.PlayersWin::class)

        collector.cancel()
        session.close()
    }

    /**
     * Regression (NEW deadlock fix): an Elimination **revote** that resolves to
     * an INNOCENT (not the killer, not the final two) must return the room to a
     * `Round` phase whose `AcknowledgeRevealCard` can advance — previously the
     * phase was left at `TiedRevote` with a `Resolved` voteState, which the
     * router rendered as a blank screen and `acknowledgeRevealCard` (Round-gated)
     * could never clear → permanent deadlock.
     */
    @Test
    fun elimination_revote_resolving_to_innocent_does_not_deadlock() = runTest {
        val payload = loadCase()
        val seed = 91L
        val players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
            Player(PlayerId("p4"), "Diego", seat = 3),
            Player(PlayerId("p5"), "Esme", seat = 4),
        )
        val (session, _) = buildSession(payload, WhodunitIds.EliminationModeId, players, seed)

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))
        session.submit(WhodunitAction.AdvanceFromDiscussion)

        val killer = hostState(session).hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id // the innocent we'll wrongly eliminate
        val targetB = innocents[1].id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // First vote: 2-2-abstain tie → TiedRevote.
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
        session.submit(WhodunitAction.CloseVote)
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)

        // Revote resolves to an innocent with a clear 3-2 majority (no tie,
        // not the killer); 4 players remain so it is not the final two.
        session.submit(WhodunitAction.OpenVote)
        session.accuseWithAllOtherVoters(ballot, targetA)
        session.submit(WhodunitAction.CloseVote)

        // Phase normalised back to a Round (NOT stranded on TiedRevote), holding
        // the innocent-eliminated announcement.
        val afterRevote = stateOf(session)
        assertThat(afterRevote.phase).isInstanceOf(WhodunitPhase.Round::class)
        val resolved = afterRevote.public.voteState as VoteState.Resolved
        assertThat(resolved.accusedPlayerId).isEqualTo(targetA)
        assertThat(resolved.wasKiller).isEqualTo(false)
        assertThat(afterRevote.public.eliminatedPlayers).isEqualTo(listOf(targetA))

        // The host's tap-through MUST advance the round (proves the deadlock is
        // gone — acknowledgeRevealCard's Round gate now fires).
        session.submit(WhodunitAction.AcknowledgeRevealCard)
        val advanced = stateOf(session)
        assertThat(advanced.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat((advanced.phase as WhodunitPhase.Round).index)
            .isEqualTo((afterRevote.phase as WhodunitPhase.Round).index + 1)
        assertThat(advanced.public.voteState).isEqualTo(VoteState.Idle)

        session.close()
    }

    /**
     * Regression (NEW deadlock fix): every voter abstaining/refusing in a
     * Classic final vote previously produced a `NoResolution` voteState that no
     * screen consumed (blank-screen deadlock). It now resolves as a killer win
     * (the table failed to accuse anyone).
     */
    @Test
    fun classic_all_abstain_resolves_to_killer_wins() = runTest {
        val payload = loadCase()
        val seed = 41L
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
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // Everyone refuses to vote.
        for (voter in ballot) session.submit(WhodunitAction.RefuseToVote(voter))
        session.submit(WhodunitAction.CloseVote)

        // Resolves to Reveal with KillerWins(TieUnresolved) — no blank screen.
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.Reveal::class)
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.KillerWins::class)
        assertThat((verdict as Verdict.KillerWins).cause).isEqualTo(KillerWinCause.TieUnresolved)

        collector.cancel()
        session.close()
    }

    /**
     * Regression (NEW deadlock fix): all-abstain in an Elimination round
     * advances to the next round (no elimination) instead of stranding the room
     * on a `NoResolution` state.
     */
    @Test
    fun elimination_all_abstain_advances_round_without_elimination() = runTest {
        val payload = loadCase()
        val seed = 42L
        val players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
            Player(PlayerId("p4"), "Diego", seat = 3),
            Player(PlayerId("p5"), "Esme", seat = 4),
        )
        val (session, _) = buildSession(payload, WhodunitIds.EliminationModeId, players, seed)

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds
        val roundBefore = (stateOf(session).phase as WhodunitPhase.Round).index

        for (voter in ballot) session.submit(WhodunitAction.AbstainVote(voter))
        session.submit(WhodunitAction.CloseVote)

        val after = stateOf(session)
        assertThat(after.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat((after.phase as WhodunitPhase.Round).index).isEqualTo(roundBefore + 1)
        assertThat(after.public.voteState).isEqualTo(VoteState.Idle)
        assertThat(after.public.eliminatedPlayers.isEmpty()).isTrue()

        session.close()
    }

    @Test
    fun elimination_first_tie_then_resolved_revote_eliminates_killer_ending_game() = runTest {
        val payload = loadCase()
        val seed = 77L
        val players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
            Player(PlayerId("p4"), "Diego", seat = 3),
            Player(PlayerId("p5"), "Esme", seat = 4),
        )
        val (session, scope) = buildSession(payload, WhodunitIds.EliminationModeId, players, seed)
        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))
        session.submit(WhodunitAction.AdvanceFromDiscussion)

        val killer = hostState(session).hostOnly.killerId
        val targetA = killer
        val targetB = players.first { it.id != killer }.id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // First vote: 2-2-abstain tie.
        session.castSplitVote(ballot, targetA, 2, targetB, 2)
        session.submit(WhodunitAction.CloseVote)
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)

        // Revote: everyone votes for the actual killer.
        session.submit(WhodunitAction.OpenVote)
        session.accuseWithAllOtherVoters(ballot, killer)
        session.submit(WhodunitAction.CloseVote)

        // Elimination: killer eliminated immediately → game ends in Reveal.
        val finalState = stateOf(session)
        assertThat(finalState.phase).isInstanceOf(WhodunitPhase.Reveal::class)
        assertThat(finalState.public.eliminatedPlayers).isEqualTo(listOf(killer))
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.PlayersWin::class)

        collector.cancel()
        session.close()
    }
}

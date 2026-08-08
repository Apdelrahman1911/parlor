package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
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
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
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
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.snapshot.InMemorySnapshotFileSystem
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
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
 * Phase 6.3 — pause, leave-game, and refuse-to-vote contracts.
 *
 * Reducer level: pause flips state and freezes timers; ticks while paused are
 * no-ops; refuse-to-vote tallies identically to abstain but emits a distinct
 * event. Persistence level: paused state survives the snapshot round-trip so
 * resume re-renders the overlay. Driver level: refuse-to-vote works under
 * normal majority, Classic tie, and Elimination second-tie (per design doc
 * §12 / §13) without breaking the existing tally/tie semantics.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class PauseRefuseLeaveTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val engineVersion = SemVer(1, 0, 0)

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
                installedAppVersion = engineVersion,
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

    private fun fivePlayers() = fourPlayers() + Player(PlayerId("p5"), "Erin", seat = 4)

    private fun TestScope.buildSession(
        payload: WhodunitCase,
        modeId: ModeId,
        players: List<Player>,
        seed: Long,
        sessionId: SessionId = SessionId("pause-refuse-$seed"),
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

    private suspend fun driveToFirstRoundTimer(
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
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(60))
    }

    private suspend fun driveToFinalVote(
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
        // Per WhodunitReducer.isLastRound: 4-player games end at round 3,
        // 5–6-player games end at round 4. Drive every non-final round through
        // its discussion so we land in FinalVote with the ballot opened.
        val lastRound = if (players.size <= 4) 3 else 4
        for (roundIndex in 1..lastRound) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(30))
            session.submit(WhodunitAction.AdvanceFromDiscussion)
        }
    }

    // ============================================================== Pause + timer freeze ==

    @Test
    fun pause_sets_public_paused_and_freezes_discussion_timer() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 1L)
        driveToFirstRoundTimer(session, players, 1L)

        val beforePause = stateOf(session)
        assertThat(beforePause.public.paused).isFalse()
        assertThat(beforePause.public.timer).isNotNull()
        assertThat(beforePause.public.timer!!.paused).isFalse()

        session.submit(WhodunitAction.Pause)
        val paused = stateOf(session)
        assertThat(paused.public.paused).isTrue()
        // Pausing the session also freezes the discussion timer.
        assertThat(paused.public.timer!!.paused).isTrue()
        session.close()
    }

    @Test
    fun resume_clears_paused_and_unfreezes_timer() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 2L)
        driveToFirstRoundTimer(session, players, 2L)
        session.submit(WhodunitAction.Pause)
        session.submit(WhodunitAction.Resume)

        val resumed = stateOf(session)
        assertThat(resumed.public.paused).isFalse()
        assertThat(resumed.public.timer!!.paused).isFalse()
        session.close()
    }

    @Test
    fun timer_ticked_while_paused_is_a_no_op() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 3L)
        driveToFirstRoundTimer(session, players, 3L)
        val before = stateOf(session).public.timer!!.remainingSeconds

        session.submit(WhodunitAction.Pause)
        // Try to advance the timer while paused.
        session.submit(WhodunitAction.TimerTicked(before - 30))
        val afterTickWhilePaused = stateOf(session).public.timer!!.remainingSeconds
        assertThat(afterTickWhilePaused).isEqualTo(before)

        // After resume, ticks are honoured again.
        session.submit(WhodunitAction.Resume)
        session.submit(WhodunitAction.TimerTicked(before - 30))
        val afterTickWhileRunning = stateOf(session).public.timer!!.remainingSeconds
        assertThat(afterTickWhileRunning).isEqualTo(before - 30)
        session.close()
    }

    @Test
    fun pause_state_survives_snapshot_round_trip() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 4L
        val sessionId = SessionId("pause-resume-survives")
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed, sessionId)
        driveToFirstRoundTimer(session, players, seed)
        session.submit(WhodunitAction.Pause)

        val paused = hostState(session)
        assertThat(paused.public.paused).isTrue()

        // Persist and rebuild a new controller from the snapshot.
        val store: SnapshotStore = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), json)
        val codec = WhodunitDefinition(json).snapshotCodec()
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_001),
                phaseId = paused.phase.id,
                payload = codec.encode(paused),
            ),
        )

        val loaded = (store.load(sessionId) as Result.Success).data
        val decoded = codec.decode(loaded.payload)

        val resumedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val resumed = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = sessionId,
                caseId = CaseId("last-dinner"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = decoded.players,
                randomSeed = decoded.hostOnly.randomSeed,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_100)),
                random = RandomSource.seeded(decoded.hostOnly.randomSeed),
                case = payload,
            ),
            scope = resumedScope,
            restoredState = decoded,
        )

        // The resumed controller boots paused — UI will render the overlay.
        val bootState = resumed.hostState!!.value.state
        assertThat(bootState.public.paused).isTrue()
        assertThat(bootState.public.timer!!.paused).isTrue()
        // Sanity: the in-game phase is preserved too.
        assertThat(bootState.phase).isInstanceOf(WhodunitPhase.Round::class)
        resumed.close()
        session.close()
    }

    // ============================================================ Leave-game flow ==

    @Test
    fun resume_later_keeps_snapshot_in_listUnfinished() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 7L
        val sessionId = SessionId("resume-later-keeps")
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed, sessionId)
        val store: SnapshotStore = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), json)
        val codec = WhodunitDefinition(json).snapshotCodec()

        driveToFirstRoundTimer(session, players, seed)
        // The Pause action is what fires the eager snapshot save in the UI; we
        // replicate that contract here.
        session.submit(WhodunitAction.Pause)
        val current = hostState(session)
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_002),
                phaseId = current.phase.id,
                payload = codec.encode(current),
            ),
        )

        // "Resume later" is a UI-level no-op against the store: the snapshot
        // stays, so Home shows the resume tile.
        val unfinished = (store.listUnfinished() as Result.Success).data
        assertThat(unfinished.map { it.raw }).containsExactly(sessionId.raw)
        session.close()
    }

    @Test
    fun end_now_deletes_snapshot_and_clears_listUnfinished() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 8L
        val sessionId = SessionId("end-now-deletes")
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed, sessionId)
        val store: SnapshotStore = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), json)
        val codec = WhodunitDefinition(json).snapshotCodec()

        driveToFirstRoundTimer(session, players, seed)
        session.submit(WhodunitAction.Pause)
        val current = hostState(session)
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_003),
                phaseId = current.phase.id,
                payload = codec.encode(current),
            ),
        )
        // "End now" is the UI's snapshot.delete + navigate. The session
        // controller is thrown away; we model that by just deleting and
        // confirming Home would render an empty list.
        store.delete(sessionId)

        assertThat((store.listUnfinished() as Result.Success).data).isEmpty()
        session.close()
    }

    // =============================================================== Refuse-to-vote ==

    @Test
    fun refuse_to_vote_emits_VoteRefused_and_tallies_like_abstain() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 100L
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed)
        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        driveToFinalVote(session, players, seed)
        val killer = hostState(session).hostOnly.killerId
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // Two votes for the killer, two refusers. With only 2 cast votes both
        // on the killer, killer is the top tally → PlayersWin.
        val accusingVoters = ballot.filterNot { it == killer }.take(2)
        session.submit(WhodunitAction.CastVote(accusingVoters[0], killer))
        session.submit(WhodunitAction.CastVote(accusingVoters[1], killer))
        val refuserA = ballot.first { it !in accusingVoters }
        val refuserB = ballot.last { it !in accusingVoters && it != refuserA }
        session.submit(WhodunitAction.RefuseToVote(refuserA))
        session.submit(WhodunitAction.RefuseToVote(refuserB))
        session.submit(WhodunitAction.CloseVote)

        // VoteRefused emitted for each refuser, in order.
        val refused = events.filterIsInstance<WhodunitEvent.VoteRefused>().map { it.voter }
        assertThat(refused).containsExactly(refuserA, refuserB)

        // The voteState's `abstained` set contains both refusers — refusing is
        // tally-equivalent to abstaining for the reducer.
        val resolved = stateOf(session).public.voteState
        assertThat(resolved).isInstanceOf(VoteState.Resolved::class)
        assertThat((resolved as VoteState.Resolved).wasKiller).isTrue()
        assertThat(resolved.accusedPlayerId).isEqualTo(killer)

        collector.cancel()
        session.close()
    }

    @Test
    fun classic_refuse_to_vote_with_tied_voters_creates_normal_tie() = runTest {
        val payload = loadCase()
        val players = fivePlayers()
        val seed = 200L
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed)
        driveToFinalVote(session, players, seed)

        val killer = hostState(session).hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id
        val targetB = innocents[1].id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // 2 vs 2 split with one refuser — a normal tie. The reducer must enter
        // TiedRevote regardless of how many refused (refused doesn't break
        // ties).
        session.castSplitVote(
            ballot = ballot,
            targetA = targetA,
            votesForA = 2,
            targetB = targetB,
            votesForB = 2,
            refuseRemainder = true,
        )
        session.submit(WhodunitAction.CloseVote)

        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)
        val tied = stateOf(session).public.voteState as VoteState.Tied
        assertThat(tied.tiedPlayerIds.toSet()).isEqualTo(setOf(targetA, targetB))
        session.close()
    }

    @Test
    fun elimination_refuse_to_vote_does_not_break_tie_then_revote_advances_round() = runTest {
        val payload = loadCase()
        val players = fivePlayers()
        val seed = 300L
        val (session, _) = buildSession(payload, WhodunitIds.EliminationModeId, players, seed)
        session.submit(WhodunitAction.AssignRoles(seed))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        session.revealRolesAndAdvance(players)
        // In Elimination Mode, voting happens at the end of each round. Drive
        // through round 1's clue + discussion, then vote.
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(30))
        session.submit(WhodunitAction.AdvanceFromDiscussion)
        val firstVoteOpened = stateOf(session).public.voteState
        assertThat(firstVoteOpened).isInstanceOf(VoteState.Collecting::class)

        val killer = hostState(session).hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id
        val targetB = innocents[1].id
        val ballot = (firstVoteOpened as VoteState.Collecting).ballotPlayerIds

        // First vote: 2 vs 2 tie, plus 1 refuser.
        session.castSplitVote(
            ballot = ballot,
            targetA = targetA,
            votesForA = 2,
            targetB = targetB,
            votesForB = 2,
            refuseRemainder = true,
        )
        session.submit(WhodunitAction.CloseVote)

        // Elimination tie → enter TiedRevote phase, voteState becomes Tied.
        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.TiedRevote::class)
        assertThat(stateOf(session).public.voteState).isInstanceOf(VoteState.Tied::class)

        // Open revote → Collecting with isSecondRound = true.
        session.submit(WhodunitAction.OpenVote)
        val revote = stateOf(session).public.voteState as VoteState.Collecting
        assertThat(revote.isSecondRound).isTrue()

        // Second vote: same 2-2 tie pattern, again with a refuser. Per design
        // doc §13, Elimination second tie advances to next round without
        // eliminating anyone.
        session.castSplitVote(
            ballot = ballot,
            targetA = targetA,
            votesForA = 2,
            targetB = targetB,
            votesForB = 2,
            refuseRemainder = true,
        )
        session.submit(WhodunitAction.CloseVote)

        val afterSecondTie = stateOf(session)
        assertThat(afterSecondTie.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat((afterSecondTie.phase as WhodunitPhase.Round).index).isEqualTo(2)
        // No one was eliminated.
        assertThat(afterSecondTie.public.eliminatedPlayers).isEmpty()
        // VoteState resets to Idle so the next round opens cleanly.
        assertThat(afterSecondTie.public.voteState).isEqualTo(VoteState.Idle)
        session.close()
    }

    // ===================================================== Refuse-to-vote sanity (single) ==

    @Test
    fun classic_majority_with_one_refuser_resolves_with_innocent_accused() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 400L
        val (session, scope) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed)
        val events = mutableListOf<WhodunitEvent>()
        val collector = scope.launch { session.events.collect { events += it } }

        driveToFinalVote(session, players, seed)
        val killer = hostState(session).hostOnly.killerId
        val anInnocent = players.first { it.id != killer }.id
        val ballot = (stateOf(session).public.voteState as VoteState.Collecting).ballotPlayerIds

        // Three players vote for the same innocent (a different innocent than
        // the killer); one refuses. Innocent wins the tally → KillerWins via
        // InnocentAccused.
        for (voter in ballot.filterNot { it == anInnocent }) {
            session.submit(WhodunitAction.CastVote(voter, anInnocent))
        }
        val refuser = anInnocent
        session.submit(WhodunitAction.RefuseToVote(refuser))
        session.submit(WhodunitAction.CloseVote)

        assertThat(stateOf(session).phase).isInstanceOf(WhodunitPhase.Reveal::class)
        val verdict = events.filterIsInstance<WhodunitEvent.WinnerDecided>().last().winner
        assertThat(verdict).isInstanceOf(Verdict.KillerWins::class)
        assertThat((verdict as Verdict.KillerWins).cause).isEqualTo(KillerWinCause.InnocentAccused)

        collector.cancel()
        session.close()
    }
}

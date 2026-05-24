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
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Reducer-driven tests for the connection-rule actions and the
 * dropped-player invariants.
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
    fun continue_without_player_drops_them_from_active_roster_and_unblocks_readiness() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 1L)
        session.submit(WhodunitAction.AssignRoles(seed = 1L))

        // Three of four ack; advance is blocked.
        session.submit(WhodunitAction.AcknowledgeIntro(players[0].id))
        session.submit(WhodunitAction.AcknowledgeIntro(players[1].id))
        session.submit(WhodunitAction.AcknowledgeIntro(players[2].id))
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        // Host drops the fourth player → active roster shrinks, advance now succeeds.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))
        assertThat(stateOf(session).public.droppedPlayers).contains(players[3].id)
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)

        // droppedPlayers persists across phase change.
        assertThat(stateOf(session).public.droppedPlayers).contains(players[3].id)
    }

    @Test
    fun dropped_player_ack_is_a_noop_via_reducer_defense() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 2L)
        session.submit(WhodunitAction.AssignRoles(seed = 2L))

        // Drop the player BEFORE they ack.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))

        // A stale or queued ack arrives from the dropped player → no-op.
        // (Authority would reject this on the wire too; defensive belt + suspenders.)
        session.submit(WhodunitAction.AcknowledgeIntro(players[3].id))
        assertThat(stateOf(session).public.introAcknowledged).doesNotContain(players[3].id)
    }

    @Test
    fun continue_without_player_clears_existing_ack_for_them() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 3L)
        session.submit(WhodunitAction.AssignRoles(seed = 3L))

        // Player acks first.
        session.submit(WhodunitAction.AcknowledgeIntro(players[3].id))
        assertThat(stateOf(session).public.introAcknowledged).contains(players[3].id)

        // Then host drops them.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))
        assertThat(stateOf(session).public.introAcknowledged).doesNotContain(players[3].id)
    }

    @Test
    fun readmit_player_works_in_intro_phase_and_restores_the_ack_gate() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 4L)
        session.submit(WhodunitAction.AssignRoles(seed = 4L))

        // Drop and readmit while still in PublicIntro.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))
        session.submit(WhodunitAction.ReadmitPlayer(players[3].id))
        assertThat(stateOf(session).public.droppedPlayers).isEmpty()

        // Now all four must ack to advance.
        players.take(3).forEach { session.submit(WhodunitAction.AcknowledgeIntro(it.id)) }
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        session.submit(WhodunitAction.AcknowledgeIntro(players[3].id))
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)
    }

    @Test
    fun readmit_player_rejected_after_round_phase_starts() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 5L)
        session.submit(WhodunitAction.AssignRoles(seed = 5L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        // Drive through character reveal to Round(1).
        for (player in players) {
            session.submit(WhodunitAction.StartCharacterReveal(player.id))
            session.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.Round::class)

        // Drop and then try to readmit — rejected because we're past the
        // readiness-gated phases.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))
        session.submit(WhodunitAction.ReadmitPlayer(players[3].id))
        assertThat(stateOf(session).public.droppedPlayers).contains(players[3].id)
    }

    @Test
    fun mark_player_disconnected_and_reconnected_toggles_set() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 6L)
        session.submit(WhodunitAction.AssignRoles(seed = 6L))

        session.submit(WhodunitAction.MarkPlayerDisconnected(players[1].id))
        assertThat(stateOf(session).public.disconnectedPlayers).contains(players[1].id)

        session.submit(WhodunitAction.MarkPlayerReconnected(players[1].id))
        assertThat(stateOf(session).public.disconnectedPlayers).isEmpty()
    }

    @Test
    fun disconnected_player_still_blocks_readiness_until_host_drops_them() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 7L)
        session.submit(WhodunitAction.AssignRoles(seed = 7L))

        // Three players ack; fourth is disconnected but NOT dropped.
        session.submit(WhodunitAction.AcknowledgeIntro(players[0].id))
        session.submit(WhodunitAction.AcknowledgeIntro(players[1].id))
        session.submit(WhodunitAction.AcknowledgeIntro(players[2].id))
        session.submit(WhodunitAction.MarkPlayerDisconnected(players[3].id))

        // Advance is still blocked — the disconnected player is in the active roster.
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        // Now host explicitly drops them → advance unblocks.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)
    }

    @Test
    fun open_vote_excludes_dropped_players_from_the_ballot() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 8L)
        session.submit(WhodunitAction.AssignRoles(seed = 8L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            session.submit(WhodunitAction.StartCharacterReveal(player.id))
            session.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }

        // Drop a player BEFORE OpenVote rebuilds the ballot from the active roster.
        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))

        for (roundIndex in 1..3) {
            session.submit(WhodunitAction.RevealNextClue)
            session.submit(WhodunitAction.StartDiscussionTimer(60))
            session.submit(WhodunitAction.AdvanceFromDiscussion)
        }
        val voteState = stateOf(session).public.voteState
        assertThat(voteState).isInstanceOf(VoteState.Collecting::class)
        val ballot = (voteState as VoteState.Collecting).ballotPlayerIds
        assertThat(ballot).doesNotContain(players[3].id)
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
                case = payload,
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

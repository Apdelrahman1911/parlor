package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
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
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
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
 * Reducer-driven tests for the new readiness gates.
 *
 *  - `AcknowledgeIntro(p)` is accepted and adds `p` to the public set.
 *  - `AdvanceFromIntro` is rejected when the set is incomplete and accepted
 *    when full. Successful advance clears the set.
 *  - Same pattern for `AcknowledgeBriefing` + final `AdvanceBriefingCard`.
 *  - Card-by-card briefing advances (not the final one) are NOT gated.
 *  - Sender mismatches are caught at the authority layer (covered by
 *    `WhodunitActionAuthorityTest`); the reducer side accepts the ack
 *    regardless of sender because it trusts the action's actor id.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class IntroAndBriefingReadinessTest {

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
    fun advance_from_intro_blocked_until_all_players_acknowledge() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 1234L)
        session.submit(WhodunitAction.AssignRoles(seed = 1234L))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        // No acks yet — advance is a no-op.
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)

        // Partial: still blocked.
        session.submit(WhodunitAction.AcknowledgeIntro(players[0].id))
        session.submit(WhodunitAction.AcknowledgeIntro(players[1].id))
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.PublicIntro::class)
        assertThat(stateOf(session).public.introAcknowledged)
            .containsExactlyInAnyOrder(players[0].id, players[1].id)

        // Complete the ack set; advance now succeeds and the set is cleared.
        session.submit(WhodunitAction.AcknowledgeIntro(players[2].id))
        session.submit(WhodunitAction.AcknowledgeIntro(players[3].id))
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)
        assertThat(stateOf(session).public.introAcknowledged).isEmpty()
    }

    @Test
    fun briefing_card_advances_pass_through_then_final_advance_is_gated_by_readiness() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 5678L)
        session.submit(WhodunitAction.AssignRoles(seed = 5678L))

        // Ack everyone + advance to RulesBriefing.
        players.forEach { session.submit(WhodunitAction.AcknowledgeIntro(it.id)) }
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)

        // Card-by-card advances are NOT gated — they just walk the index.
        for (i in 1..3) {
            session.submit(WhodunitAction.AdvanceBriefingCard(i))
            assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)
            assertThat(stateOf(session).public.briefingCardIndex).isEqualTo(i)
        }

        // The final advance (index == BRIEFING_CARD_COUNT) IS gated.
        // Submit it with no readiness — no-op.
        session.submit(WhodunitAction.AdvanceBriefingCard(4))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)

        // Ack three of four; still blocked.
        session.submit(WhodunitAction.AcknowledgeBriefing(players[0].id))
        session.submit(WhodunitAction.AcknowledgeBriefing(players[1].id))
        session.submit(WhodunitAction.AcknowledgeBriefing(players[2].id))
        session.submit(WhodunitAction.AdvanceBriefingCard(4))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)

        // Final ack + final advance → CharacterReveal, set cleared.
        session.submit(WhodunitAction.AcknowledgeBriefing(players[3].id))
        session.submit(WhodunitAction.AdvanceBriefingCard(4))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.CharacterReveal::class)
        assertThat(stateOf(session).public.briefingReady).isEmpty()
    }

    @Test
    fun acknowledge_intro_outside_public_intro_is_a_noop() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 9L)
        session.submit(WhodunitAction.AssignRoles(seed = 9L))
        players.forEach { session.submit(WhodunitAction.AcknowledgeIntro(it.id)) }
        session.submit(WhodunitAction.AdvanceFromIntro)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.RulesBriefing::class)

        // Submitting AcknowledgeIntro outside PublicIntro phase is a no-op.
        session.submit(WhodunitAction.AcknowledgeIntro(players[0].id))
        assertThat(stateOf(session).public.introAcknowledged).isEmpty()
    }

    @Test
    fun acknowledge_intro_for_unknown_player_id_is_a_noop() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 17L)
        session.submit(WhodunitAction.AssignRoles(seed = 17L))
        val ghost = PlayerId("ghost")
        session.submit(WhodunitAction.AcknowledgeIntro(ghost))
        assertThat(stateOf(session).public.introAcknowledged).isEmpty()
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
                sessionId = SessionId("readiness-$seed"),
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

package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.contains
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
 * Wave 9H-3: CharacterReveal is now the simultaneous-reveal phase.
 *
 *  - `CompleteCharacterReveal(p)` (or `ConfirmRoleViewed(p)`) adds `p` to
 *    `public.rolesViewed` without auto-advancing.
 *  - `AdvanceFromCharacterReveal` (HostOnly) advances to `Round(1)` only
 *    when every active-roster player is in `rolesViewed`.
 *  - Reducer rejects `AdvanceFromCharacterReveal` while the set is
 *    incomplete; UI cannot mutate this — the gate is in the reducer.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class SimultaneousCharacterRevealTest {

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
    fun advance_from_character_reveal_blocked_until_every_active_player_confirms() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 1L)
        session.submit(WhodunitAction.AssignRoles(seed = 1L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        // No confirms — host's advance is a no-op.
        session.submit(WhodunitAction.AdvanceFromCharacterReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        // Partial — still blocked.
        session.submit(WhodunitAction.CompleteCharacterReveal(players[0].id))
        session.submit(WhodunitAction.CompleteCharacterReveal(players[1].id))
        session.submit(WhodunitAction.AdvanceFromCharacterReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        // Complete the set — host's advance now succeeds and the set clears.
        session.submit(WhodunitAction.CompleteCharacterReveal(players[2].id))
        session.submit(WhodunitAction.CompleteCharacterReveal(players[3].id))
        session.submit(WhodunitAction.AdvanceFromCharacterReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.Round::class)
        assertThat(stateOf(session).public.rolesViewed).isEmpty()
    }

    @Test
    fun confirm_role_viewed_is_equivalent_to_complete_character_reveal_for_readiness() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 2L)
        session.submit(WhodunitAction.AssignRoles(seed = 2L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))

        // Mix the two action names — they should both feed the same set.
        session.submit(WhodunitAction.CompleteCharacterReveal(players[0].id))
        session.submit(WhodunitAction.ConfirmRoleViewed(players[1].id))
        session.submit(WhodunitAction.CompleteCharacterReveal(players[2].id))
        session.submit(WhodunitAction.ConfirmRoleViewed(players[3].id))
        session.submit(WhodunitAction.AdvanceFromCharacterReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.Round::class)
    }

    @Test
    fun disconnect_during_character_reveal_pauses_and_expiry_ends_the_case() = runTest {
        val payload = loadCase()
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed = 3L)
        session.submit(WhodunitAction.AssignRoles(seed = 3L))
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))

        session.submit(WhodunitAction.MarkPlayerDisconnected(players[3].id))
        assertThat(stateOf(session).public.paused).isEqualTo(true)

        // Private readiness submitted after the disconnect cannot advance a
        // partially revealed case.
        session.submit(WhodunitAction.CompleteCharacterReveal(players[0].id))
        session.submit(WhodunitAction.CompleteCharacterReveal(players[1].id))
        session.submit(WhodunitAction.CompleteCharacterReveal(players[2].id))
        session.submit(WhodunitAction.AdvanceFromCharacterReveal)
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.CharacterReveal::class)

        session.submit(WhodunitAction.ContinueWithoutPlayer(players[3].id))
        assertThat(phaseOf(session)).isInstanceOf(WhodunitPhase.Reveal::class)
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
                sessionId = SessionId("simul-$seed"),
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

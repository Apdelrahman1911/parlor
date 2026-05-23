package com.parlor.games.whodunit.snapshot

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Phase 6.1 round-trip: persist a real Whodunit session's snapshot through
 * `FileBackedSnapshotStore` + an in-memory `SnapshotFileSystem`, then decode
 * via `WhodunitSnapshotCodec` and assert the deserialized state is
 * byte-identical to the original.
 *
 * Specifically pins `VoteState.Collecting.isSecondRound` so the tied-revote
 * fix from commit 5a3f60d survives serialization — a regression here would
 * silently break the killer-wins-on-second-tie path after a process death.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class WhodunitSnapshotRoundTripTest {

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

    private fun TestScope.buildSession(
        payload: WhodunitCase,
        modeId: ModeId,
        players: List<Player>,
        seed: Long,
        sessionId: SessionId,
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

    private fun fourPlayers() = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
        Player(PlayerId("p3"), "Cara", seat = 2),
        Player(PlayerId("p4"), "Diego", seat = 3),
    )

    private fun buildStore(fs: InMemorySnapshotFileSystem): SnapshotStore =
        FileBackedSnapshotStore(fileSystem = fs, json = json)

    @Test
    fun mid_round_state_round_trips_through_file_backed_store() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 12345L
        val sessionId = SessionId("test-round-trip")
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed, sessionId)
        val codec = WhodunitDefinition(json).snapshotCodec()

        // Drive partway: setup → into Round 1, clue revealed, discussion timer running.
        session.submit(WhodunitAction.AssignRoles(seed))
        session.submit(WhodunitAction.AdvanceFromIntro)
        for (i in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(i))
        for (player in players) {
            session.submit(WhodunitAction.StartCharacterReveal(player.id))
            session.submit(WhodunitAction.CompleteCharacterReveal(player.id))
        }
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))

        val originalState = session.hostState!!.value.state
        assertThat(originalState.phase).isInstanceOf(WhodunitPhase.Round::class)
        assertThat(originalState.public.revealedClues).hasSize(1)
        assertThat(originalState.public.timer).isNotNull()

        val fs = InMemorySnapshotFileSystem()
        val store = buildStore(fs)

        val snapshot = GameSnapshot(
            sessionId = sessionId,
            gameId = WhodunitIds.GameId,
            engineVersion = engineVersion,
            createdAt = Instant.fromEpochSeconds(1_700_000_000),
            phaseId = originalState.phase.id,
            payload = codec.encode(originalState),
        )

        assertThat(store.save(snapshot) is Result.Success).isTrue()
        val loaded = store.load(sessionId)
        assertThat(loaded).isInstanceOf(Result.Success::class)
        val loadedSnapshot = (loaded as Result.Success).data
        val decoded = codec.decode(loadedSnapshot.payload)

        // Full state equality — the encoder/decoder + file backing must
        // preserve every field, including private/host-only buckets.
        assertThat(decoded).isEqualTo(originalState)
    }

    @Test
    fun tied_revote_state_with_isSecondRound_round_trips_correctly() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 222L
        val sessionId = SessionId("tied-revote-roundtrip")
        val (session, _) = buildSession(payload, WhodunitIds.ClassicVoteModeId, players, seed, sessionId)
        val codec = WhodunitDefinition(json).snapshotCodec()

        // Drive into a 2-2 tied vote and open the revote so the state captures
        // VoteState.Collecting(isSecondRound = true).
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
        val killer = session.hostState!!.value.state.hostOnly.killerId
        val innocents = players.filter { it.id != killer }
        val targetA = innocents[0].id
        val targetB = innocents[1].id
        val ballot =
            (session.publicState.value.state.public.voteState as VoteState.Collecting).ballotPlayerIds
        session.submit(WhodunitAction.CastVote(ballot[0], targetA))
        session.submit(WhodunitAction.CastVote(ballot[1], targetA))
        session.submit(WhodunitAction.CastVote(ballot[2], targetB))
        session.submit(WhodunitAction.CastVote(ballot[3], targetB))
        session.submit(WhodunitAction.CloseVote)
        session.submit(WhodunitAction.OpenVote)

        val tiedRevoteState = session.hostState!!.value.state
        val collecting = tiedRevoteState.public.voteState as VoteState.Collecting
        assertThat(collecting.isSecondRound).isTrue()

        val fs = InMemorySnapshotFileSystem()
        val store = buildStore(fs)

        val snapshot = GameSnapshot(
            sessionId = sessionId,
            gameId = WhodunitIds.GameId,
            engineVersion = engineVersion,
            createdAt = Instant.fromEpochSeconds(1_700_000_001),
            phaseId = tiedRevoteState.phase.id,
            payload = codec.encode(tiedRevoteState),
        )
        store.save(snapshot)

        val loaded = (store.load(sessionId) as Result.Success).data
        val decoded = codec.decode(loaded.payload)

        // Full equality plus an explicit assertion on the marker so a future
        // diff that drops the field surfaces here directly.
        assertThat(decoded).isEqualTo(tiedRevoteState)
        val decodedCollecting = decoded.public.voteState as VoteState.Collecting
        assertThat(decodedCollecting.isSecondRound).isTrue()
    }

    @Test
    fun listUnfinished_reflects_saved_and_deleted_sessions() = runTest {
        val fs = InMemorySnapshotFileSystem()
        val store = buildStore(fs)
        val payload = loadCase()
        val codec = WhodunitDefinition(json).snapshotCodec()
        val (session, _) = buildSession(
            payload, WhodunitIds.ClassicVoteModeId, fourPlayers(), seed = 1L,
            sessionId = SessionId("first"),
        )
        session.submit(WhodunitAction.AssignRoles(1L))
        val s1 = session.hostState!!.value.state

        store.save(
            GameSnapshot(
                sessionId = SessionId("first"),
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_000),
                phaseId = s1.phase.id,
                payload = codec.encode(s1),
            ),
        )
        store.save(
            GameSnapshot(
                sessionId = SessionId("second"),
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_002),
                phaseId = s1.phase.id,
                payload = codec.encode(s1),
            ),
        )

        val before = (store.listUnfinished() as Result.Success).data
        assertThat(before.map { it.raw }.toSet()).isEqualTo(setOf("first", "second"))

        store.delete(SessionId("first"))
        val after = (store.listUnfinished() as Result.Success).data
        assertThat(after.map { it.raw }).containsExactly("second")
    }

    @Test
    fun loading_unknown_session_returns_not_found() = runTest {
        val store = buildStore(InMemorySnapshotFileSystem())
        val result = store.load(SessionId("never-saved"))
        assertThat(result).isInstanceOf(Result.Failure::class)
    }
}


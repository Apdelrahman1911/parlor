package com.parlor.games.whodunit.snapshot

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
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
import com.parlor.games.whodunit.ui.flow.loadResumedSession
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Phase 6.2 reconstruction: walk the full resume path end-to-end. Save a real
 * mid-game session → load via [SnapshotStore] → decode via
 * [WhodunitDefinition.snapshotCodec] → boot a *new* [PassAndPlaySessionController]
 * with `restoredState` → assert the resumed controller's host state equals the
 * pre-save state and that the next action advances the phase as expected.
 *
 * This is the contract that backs the Home "Resume" tile.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class WhodunitResumeReconstructionTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val engineVersion = SemVer(1, 0, 0)

    private fun fourPlayers() = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
        Player(PlayerId("p3"), "Cara", seat = 2),
        Player(PlayerId("p4"), "Diego", seat = 3),
    )

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

    @Test
    fun resumed_controller_state_equals_saved_state_and_advances_correctly() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 9999L
        val sessionId = SessionId("resume-roundtrip")
        val sessionConfig = SessionConfig(
            sessionId = sessionId,
            caseId = CaseId("last-dinner"),
            modeId = WhodunitIds.ClassicVoteModeId,
            players = players,
            randomSeed = seed,
        )

        // 1) Set up the original session and drive it into RulesBriefing (a phase
        // resume must restore — not Setup, which the flow auto-advances).
        val originalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val definitionA = WhodunitDefinition(json)
        val original = PassAndPlaySessionController(
            definition = definitionA,
            config = sessionConfig,
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                random = RandomSource.seeded(seed),
                case = payload,
            ),
            scope = originalScope,
        )
        original.submit(WhodunitAction.AssignRoles(seed))
        original.ackIntroForAll(players)
        original.submit(WhodunitAction.AdvanceFromIntro)
        // Now in RulesBriefing card 0. Advance two cards to capture a non-trivial
        // briefingCardIndex (so a regression that drops public state surfaces).
        original.submit(WhodunitAction.AdvanceBriefingCard(1))
        original.submit(WhodunitAction.AdvanceBriefingCard(2))
        val savedState = original.hostState!!.value.state
        assertThat(savedState.phase).isInstanceOf(WhodunitPhase.RulesBriefing::class)
        assertThat(savedState.public.briefingCardIndex).isEqualTo(2)
        original.close()

        // 2) Persist through the real store.
        val fs = InMemorySnapshotFileSystem()
        val store: SnapshotStore = FileBackedSnapshotStore(fileSystem = fs, json = json)
        val codec = WhodunitDefinition(json).snapshotCodec()
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_001),
                phaseId = savedState.phase.id,
                payload = codec.encode(savedState),
            ),
        )

        // 3) Confirm listUnfinished surfaces this session — what Home queries.
        val listed = (store.listUnfinished() as Result.Success).data
        assertThat(listed.map { it.raw }).contains(sessionId.raw)

        // 4) Cold-start resume: load, decode, build a NEW controller with
        // restoredState. The original controller is gone — this is the path
        // tapping the Home tile exercises.
        val loadedSnapshot = (store.load(sessionId) as Result.Success).data
        val decoded = codec.decode(loadedSnapshot.payload)

        val resumedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val definitionB = WhodunitDefinition(json)
        val resumed = PassAndPlaySessionController(
            definition = definitionB,
            config = sessionConfig.copy(
                // The flow rebuilds SessionConfig from the decoded state's
                // sessionId / caseId / modeId / players / hostOnly.randomSeed —
                // we mirror that here so the test reflects the real path.
                sessionId = loadedSnapshot.sessionId,
                caseId = CaseId(decoded.public.caseId.raw),
                modeId = decoded.public.modeId,
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

        // 5) Boot state equals what we saved — phase, public, private, host-only.
        val bootState = resumed.hostState!!.value.state
        assertThat(bootState).isEqualTo(savedState)
        assertThat(bootState.phase).isInstanceOf(WhodunitPhase.RulesBriefing::class)
        assertThat(bootState.public.briefingCardIndex).isEqualTo(2)

        // 6) Submitting the *next* action advances from the restored phase, not
        // from Setup. This proves the reducer sees the resumed state.
        resumed.submit(WhodunitAction.AdvanceBriefingCard(3))
        val afterAdvance = resumed.hostState!!.value.state
        assertThat(afterAdvance.public.briefingCardIndex).isEqualTo(3)
        // RulesBriefing's last card is 3 (4 cards total, 0-indexed). Advancing
        // beyond should move us forward; the briefing transitions out — but
        // since the per-case briefing card count is fixed for The Last Dinner,
        // we just assert the index advanced past the saved value.
        assertThat(afterAdvance.public.briefingCardIndex > savedState.public.briefingCardIndex).isEqualTo(true)
        resumed.close()
    }

    @Test
    fun corrupt_snapshot_payload_does_not_blow_up_decoder() = runTest {
        val codec = WhodunitDefinition(json).snapshotCodec()
        val result = runCatching { codec.decode("not a real whodunit state".encodeToByteArray()) }
        // We expect *some* failure — a serialization exception or similar. The
        // contract is "don't silently succeed with garbage."
        assertThat(result.isFailure).isEqualTo(true)
    }

    @Test
    fun resume_rejects_another_game_before_decoding_its_payload() = runTest {
        val sessionId = SessionId("wrong-game")
        val store: SnapshotStore = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), json)
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = GameId("mafia"),
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_020),
                phaseId = "not-whodunit",
                payload = "not a Whodunit payload".encodeToByteArray(),
            ),
        )

        val result = loadResumedSession(store, WhodunitDefinition(json), sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun resume_rejects_a_future_engine_version_before_decoding_its_payload() = runTest {
        val sessionId = SessionId("future-engine")
        val store: SnapshotStore = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), json)
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = SemVer(2, 0, 0),
                createdAt = Instant.fromEpochSeconds(1_700_000_021),
                phaseId = "not-whodunit",
                payload = "not a Whodunit payload".encodeToByteArray(),
            ),
        )

        val result = loadResumedSession(store, WhodunitDefinition(json), sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun deleting_snapshot_removes_it_from_listUnfinished() = runTest {
        val payload = loadCase()
        val players = fourPlayers()
        val seed = 314L
        val sessionId = SessionId("delete-test")
        val sessionConfig = SessionConfig(
            sessionId = sessionId,
            caseId = CaseId("last-dinner"),
            modeId = WhodunitIds.ClassicVoteModeId,
            players = players,
            randomSeed = seed,
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val definition = WhodunitDefinition(json)
        val controller = PassAndPlaySessionController(
            definition = definition,
            config = sessionConfig,
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                random = RandomSource.seeded(seed),
                case = payload,
            ),
            scope = scope,
        )
        controller.submit(WhodunitAction.AssignRoles(seed))

        val store: SnapshotStore = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), json)
        val codec = WhodunitDefinition(json).snapshotCodec()
        val state = controller.hostState!!.value.state
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = WhodunitIds.GameId,
                engineVersion = engineVersion,
                createdAt = Instant.fromEpochSeconds(1_700_000_010),
                phaseId = state.phase.id,
                payload = codec.encode(state),
            ),
        )

        assertThat(
            (store.listUnfinished() as Result.Success).data.map { it.raw },
        ).containsExactly(sessionId.raw)

        // PostGame entry would normally trigger delete; we test the contract.
        store.delete(sessionId)
        assertThat(
            (store.listUnfinished() as Result.Success).data,
        ).isEqualTo(emptyList())
        controller.close()
    }
}

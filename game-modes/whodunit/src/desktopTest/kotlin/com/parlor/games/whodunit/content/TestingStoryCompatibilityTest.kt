package com.parlor.games.whodunit.content

import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
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
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.snapshot.InMemorySnapshotFileSystem
import com.parlor.games.whodunit.snapshot.WHODUNIT_SNAPSHOT_ENGINE_VERSION
import com.parlor.games.whodunit.ui.flow.ResumedSession
import com.parlor.games.whodunit.ui.flow.loadResumedSession
import com.parlor.games.whodunit.ui.flow.validateResumedSessionForCase
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Real content/codec/store boundaries; the filesystem is synthetic, not an iOS durability test. */
@OptIn(ExperimentalResourceApi::class)
class TestingStoryCompatibilityTest {
    private val fixture = TestingStoryFixtures()

    @Test
    fun correctedStoriesDoNotReuseAnyOriginalCanonicalContentDigest() = runTest {
        val reused = mutableListOf<String>()
        fixture.originalDigests.forEach { (caseId, oldDigest) ->
            if (fixture.loadCase(caseId).envelope.contentIdentity().digest == oldDigest) {
                reused += caseId
            }
        }
        // This fails for all four uncorrected bundles, also checking that the
        // archived digest constants are the production Kotlin canonical digests.
        assertEquals(emptyList(), reused)
    }

    @Test
    fun identityLessPreClueSavesCannotSilentlyBindToInstalledStoryProse() = runTest {
        val acceptedWithoutIdentity = mutableListOf<String>()
        fixture.originalDigests.keys.forEach { caseId ->
            val case = fixture.loadCase(caseId)
            fixture.modes.forEach { mode ->
                val assigned = fixture.assignedState(case, mode, seed = 91L)
                assertTrue(assigned.public.revealedClues.isEmpty())
                val sessionId = SessionId("identityless-$caseId-${mode.raw}")
                val snapshot = GameSnapshot(
                    sessionId = sessionId,
                    gameId = WhodunitIds.GameId,
                    engineVersion = WHODUNIT_SNAPSHOT_ENGINE_VERSION,
                    createdAt = fixture.clock.now(),
                    phaseId = assigned.phase.id,
                    payload = fixture.definition.snapshotCodec().encode(assigned),
                    metadata = mapOf("playMode" to "PassAndPlay"),
                )
                val store = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), fixture.json)
                assertIs<Result.Success<Unit>>(store.save(snapshot))
                val loaded = assertIs<Result.Success<ResumedSession>>(
                    loadResumedSession(store, fixture.definition, sessionId),
                ).data
                assertEquals(null, loaded.contentIdentity)
                assertEquals(assigned, loaded.state)
                // The identical state with known provenance remains valid. A
                // different structural failure must not make this test green.
                assertEquals(Result.Success(Unit), validateResumedSessionForCase(
                    loaded.copy(contentIdentity = case.envelope.contentIdentity()), case,
                ))
                val result = validateResumedSessionForCase(loaded, case)
                if (result != Result.Failure(DataError.CorruptedData)) {
                    acceptedWithoutIdentity += "$caseId/${mode.raw}: $result"
                }
                val retained = assertIs<Result.Success<GameSnapshot>>(store.load(sessionId)).data
                assertEquals(snapshot.metadata, retained.metadata)
                assertContentEquals(snapshot.payload, retained.payload)
            }
        }
        assertEquals(emptyList(), acceptedWithoutIdentity)
    }

    @Test
    fun currentSaveRoundTripsButOldOrCrossedIdentityIsRetainedAndRejected() = runTest {
        fixture.originalDigests.forEach { (caseId, oldDigest) ->
            val case = fixture.loadCase(caseId)
            val current = case.envelope.contentIdentity()
            val identities = listOf(
                current,
                WhodunitContentIdentity("1.0.0", oldDigest),
                current.copy(version = "1.0.0"),
                current.copy(digest = oldDigest),
            )
            fixture.modes.forEach { mode ->
                val assigned = fixture.assignedState(case, mode, seed = 91L)
                identities.forEachIndexed { index, identity ->
                    val sessionId = SessionId("content-$caseId-${mode.raw}-$index")
                    val snapshot = GameSnapshot(
                        sessionId = sessionId,
                        gameId = WhodunitIds.GameId,
                        engineVersion = WHODUNIT_SNAPSHOT_ENGINE_VERSION,
                        createdAt = fixture.clock.now(),
                        phaseId = assigned.phase.id,
                        payload = fixture.definition.snapshotCodec().encode(assigned),
                        metadata = mapOf(
                            "playMode" to "PassAndPlay",
                            "caseVersion" to identity.version,
                            "caseDigest" to identity.digest,
                        ),
                    )
                    val store = FileBackedSnapshotStore(InMemorySnapshotFileSystem(), fixture.json)
                    assertIs<Result.Success<Unit>>(store.save(snapshot))
                    val loaded = assertIs<Result.Success<ResumedSession>>(
                        loadResumedSession(store, fixture.definition, sessionId),
                    ).data
                    assertEquals(assigned, loaded.state)
                    assertEquals(identity, loaded.contentIdentity)
                    val validation = validateResumedSessionForCase(loaded, case)
                    if (index == 0) {
                        assertEquals(Result.Success(Unit), validation)
                    } else {
                        assertEquals(Result.Failure(DataError.CorruptedData), validation)
                    }

                    // Failed compatibility is not permission to rewrite or delete a save.
                    val retained = assertIs<Result.Success<GameSnapshot>>(store.load(sessionId)).data
                    assertEquals(snapshot.metadata, retained.metadata)
                    assertContentEquals(snapshot.payload, retained.payload)
                    assertEquals(listOf(sessionId), assertIs<Result.Success<List<SessionId>>>(
                        store.listUnfinished(),
                    ).data)
                    assertIs<Result.Success<Unit>>(store.delete(sessionId))
                    assertEquals(emptyList(), assertIs<Result.Success<List<SessionId>>>(
                        store.listUnfinished(),
                    ).data)
                }
            }
        }
    }

    @Test
    fun lanOffersMatchOnlyTheExactLoadedRevisionForAllCorrectedCasesAndModes() = runTest {
        fixture.originalDigests.forEach { (caseId, oldDigest) ->
            val case = fixture.loadCase(caseId)
            val identity = case.envelope.contentIdentity()
            fixture.modes.forEach { mode ->
                val offer = HostMessage.SessionStarting(
                    startId = "start-testing-content-0001",
                    caseId = caseId,
                    modeId = mode.raw,
                    players = fixture.players,
                    sessionNonce = 91L,
                    header = SessionEnvelopeHeader(
                        protocol = ProtocolVersion(),
                        sessionId = SessionId("session-testing-content-0001"),
                        gameId = WhodunitIds.GameId,
                        gameVersion = 1,
                        messageId = "start-testing-content-0001",
                        sequence = 0L,
                    ),
                    caseVersion = identity.version,
                    caseDigest = identity.digest,
                )
                assertTrue(offer.matches(case.envelope), "$caseId/${mode.raw}")
                assertFalse(offer.copy(caseVersion = "1.0.0", caseDigest = oldDigest).matches(case.envelope))
                assertFalse(offer.copy(caseVersion = "1.0.0").matches(case.envelope))
                assertFalse(offer.copy(caseDigest = oldDigest).matches(case.envelope))
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
internal class TestingStoryFixtures {
    val json = Json { ignoreUnknownKeys = false }
    val definition = WhodunitDefinition(json)
    val clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000))
    val players = (1..6).map { Player(PlayerId("p$it"), "Player $it", it - 1) }
    val modes = listOf(WhodunitIds.ClassicVoteModeId, WhodunitIds.EliminationModeId)

    // Baseline 3625d0663ba6eb51338cbd5f9dc45f859ec18846; these are contentIdentity(),
    // not hashes of resource bytes. They deliberately keep old-save incompatibility executable.
    val originalDigests = mapOf(
        "last-dinner" to "a8593bdab776bb5dc1723b6fb562f37009aa2e02ddd11fc7e848284916452f26",
        "layla-halabi" to "b98ca82b8cc2334e4f241f6bcefad96c7846f15096f3a043c4126911404345a5",
        "jasmine-ring" to "5cd26321aecef56a82afb56ec61f8ebad77329de39f413cb59b26de766d0d714",
        "khan-el-khalili" to "0e690e0129c9b76fb772952b4e888e59e81610597b9294c52e8f656144393b9c",
    )

    suspend fun loadCase(id: String): ValidatedCase<WhodunitCase> {
        val raw = Res.readBytes("files/cases/$id.json").decodeToString()
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(definition)),
        )
        return assertIs<Result.Success<ValidatedCase<WhodunitCase>>>(
            validator.validate(raw, WhodunitPayloadValidator(json)),
        ).data
    }

    fun initialState(case: ValidatedCase<WhodunitCase>, mode: ModeId, seed: Long): WhodunitState =
        definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("testing-story-${case.envelope.caseId}-${mode.raw}-$seed"),
                caseId = CaseId(case.envelope.caseId),
                modeId = mode,
                players = players,
                randomSeed = seed,
            ),
        )

    fun assignedState(case: ValidatedCase<WhodunitCase>, mode: ModeId, seed: Long): WhodunitState =
        WhodunitReducer.reduce(
            initialState(case, mode, seed),
            WhodunitAction.AssignRoles(seed),
            context(case, seed),
        ).newState

    fun context(case: ValidatedCase<WhodunitCase>, seed: Long) = WhodunitReducerContext(
        clock = clock,
        random = RandomSource.seeded(seed),
        case = case,
    )
}

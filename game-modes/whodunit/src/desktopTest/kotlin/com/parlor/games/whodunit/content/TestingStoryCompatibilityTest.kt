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
    fun correctedStoriesDoNotReuseAnyRecordedEarlierCanonicalContentDigest() = runTest {
        assertEquals(bundledWhodunitCaseIds.toSet(), fixture.retiredIdentities.keys)
        assertEquals(13, fixture.retiredIdentities.values.sumOf { it.size })
        val reused = mutableListOf<String>()
        fixture.retiredIdentities.forEach { (caseId, retired) ->
            val current = fixture.loadCase(caseId).envelope.contentIdentity()
            if (retired.any { it.digest == current.digest }) {
                reused += caseId
            }
        }
        // Keep the first four 1.0.0 identities, all seven pre-follow-up
        // identities and both pre-final-editorial identities. Do not replace old-save coverage.
        assertEquals(emptyList(), reused)
    }

    @Test
    fun identityLessPreClueSavesCannotSilentlyBindToInstalledStoryProse() = runTest {
        val acceptedWithoutIdentity = mutableListOf<String>()
        bundledWhodunitCaseIds.forEach { caseId ->
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
        fixture.retiredIdentities.forEach { (caseId, retired) ->
            val case = fixture.loadCase(caseId)
            val current = case.envelope.contentIdentity()
            val identities = listOf(current) + retired.flatMap { old ->
                listOf(old, current.copy(version = old.version), current.copy(digest = old.digest))
            }
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
        fixture.retiredIdentities.forEach { (caseId, retired) ->
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
                retired.forEach { old ->
                    assertFalse(offer.copy(caseVersion = old.version, caseDigest = old.digest).matches(case.envelope))
                    assertFalse(offer.copy(caseVersion = old.version).matches(case.envelope))
                    assertFalse(offer.copy(caseDigest = old.digest).matches(case.envelope))
                }
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

    // Full canonical identities at 85ded00435b4f8327ce32d3b7f5062c4438ae174,
    // before the independently reviewed follow-up prose corrections. These
    // are not resource-byte hashes and must not be updated with current prose.
    private val previousIdentities = mapOf(
        "last-dinner" to WhodunitContentIdentity(
            "1.0.1", "f3b71cc74aaee28dcf7845b750ca773229047388a391ba9294be89efab173a05",
        ),
        "layla-halabi" to WhodunitContentIdentity(
            "1.0.1", "953d5b1dae0aa26b9901e0e044f279b94fd64d4a6d8a66371a9c6577672a3cfc",
        ),
        "jasmine-ring" to WhodunitContentIdentity(
            "1.0.1", "d0aab1123c08f7fb919f00f7ecc8eeb9e1ed2d5daf03df169ea90489c63199c7",
        ),
        "khan-el-khalili" to WhodunitContentIdentity(
            "1.0.1", "c9979500738d7b15812b286300714db1943824aa44c29656dc6bebea72e979a0",
        ),
        "iskenderia-corniche" to WhodunitContentIdentity(
            "1.0.0", "b492eddff0ccfdc3142a60ff2ee6da22566cbb39ab8717c2219e8875ac66733d",
        ),
        "saidi-inheritance" to WhodunitContentIdentity(
            "1.0.0", "5d333bfb59ebf49586f5ca15a26ba55206b6d8aa9de505ca8c3e4738ffebf8b6",
        ),
        "zamalek-ramadan" to WhodunitContentIdentity(
            "1.0.0", "b3fa57ea89c675bc3c5185a64adcbf0a99b4d640d6dff6fa206b58ed2c070d91",
        ),
    )

    // Canonical identities from committed 1f809b87c15a4deb079809bc14718a58cf0fe451,
    // before the explicit final testing-chronology choices. Preserve these saved-game boundaries.
    private val preFinalEditorialIdentities = mapOf(
        "jasmine-ring" to WhodunitContentIdentity(
            "1.0.2", "f03f94cc92f2f0c2388eab34e45857cb7dcfca2dd86f2aced5ff23d9292017cf",
        ),
        "iskenderia-corniche" to WhodunitContentIdentity(
            "1.0.1", "e27cddb6fb9b6bf035ce6cd9fcb8000dbcb589ae0333cda5c7d7ac83598fb2f7",
        ),
    )

    val retiredIdentities = previousIdentities.mapValues { (caseId, previous) ->
        buildList {
            add(previous)
            originalDigests[caseId]?.let { add(WhodunitContentIdentity("1.0.0", it)) }
            preFinalEditorialIdentities[caseId]?.let(::add)
        }
    }

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

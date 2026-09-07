package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.OfflineRemoteCaseDataSource
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.snapshot.WHODUNIT_SNAPSHOT_ENGINE_VERSION
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.KoinIsolatedContext
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** Actual recovery UI/repository/codec; synthetic records, not platform durability evidence. */
@OptIn(ExperimentalTestApi::class, ExperimentalResourceApi::class)
class WhodunitRecoveryInteractionTest {
    @Test
    fun englishIncompatibleSaveIsExplainedAndKeptUntilExplicitDiscard(): Unit =
        verifyRecovery(AppLanguage.English)

    @Test
    fun arabicIncompatibleSaveIsExplainedAndKeptUntilExplicitDiscard(): Unit =
        verifyRecovery(AppLanguage.Arabic)

    @Test
    fun englishIdentityLessSaveIsKeptUntilExplicitDiscard(): Unit =
        verifyRecovery(AppLanguage.English, includeContentIdentity = false)

    @Test
    fun arabicIdentityLessSaveIsKeptUntilExplicitDiscard(): Unit =
        verifyRecovery(AppLanguage.Arabic, includeContentIdentity = false)

    @Test
    fun englishRetiredSoloSaveIsKeptUntilExplicitDiscard(): Unit =
        verifyRecovery(AppLanguage.English, persistedMode = "Solo")

    @Test
    fun arabicRetiredSoloSaveIsKeptUntilExplicitDiscard(): Unit =
        verifyRecovery(AppLanguage.Arabic, persistedMode = "Solo")

    private fun verifyRecovery(
        language: AppLanguage,
        includeContentIdentity: Boolean = true,
        persistedMode: String = "PassAndPlay",
    ) {
        val fixture = Fixture(includeContentIdentity, persistedMode)
        runBlocking { fixture.prepare() }
        val application = koinApplication {
            modules(module {
                single<CaseRepository> { fixture.repository }
                single<PayloadValidator<WhodunitCase>>(named("whodunit")) { fixture.payloadValidator }
                single<SnapshotStore> { fixture.store }
                single { fixture.definition }
            })
        }
        try {
            runComposeUiTest {
                var mounted by mutableStateOf(true)
                var exits = 0
                val toastState = ParlorToastState()
                val arabic = language == AppLanguage.Arabic
                val explanation = if (arabic) {
                    "هذا التحقيق المحفوظ غير متوافق مع إصدار القصة الحالي أو أن بياناته تالفة. " +
                        "لم يُحذف. يمكنك حذفه وبدء تحقيق جديد من المكتبة."
                } else {
                    "This saved investigation is incompatible with this story version or is damaged. " +
                        "It has been kept. Discard it to start a new investigation from the library."
                }
                val retry = if (arabic) "حاول فتح التحقيق المحفوظ مرة أخرى." else
                    "Try to open this saved investigation again."
                val back = if (arabic) "العودة إلى المكتبة." else "Return to the library."
                val discard = if (arabic) "احذف هذا التحقيق المحفوظ نهائيًا." else
                    "Permanently delete this saved investigation."
                setContent {
                    KoinIsolatedContext(application) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(1f, 1.3f),
                            LocalParlorToastState provides toastState,
                        ) {
                            ProvideAppLanguage(language) {
                                ParlorTheme(reducedMotion = true) {
                                    if (mounted) {
                                        WhodunitGameFlow(
                                            onBackToLibrary = { exits++; mounted = false },
                                            resumeSessionId = fixture.selected.sessionId,
                                            modifier = Modifier.size(320.dp, 640.dp),
                                        )
                                    } else {
                                        Text("Library", Modifier.testTag("recovery-library"))
                                    }
                                }
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(explanation).fetchSemanticsNodes().size == 1 }
                runBlocking { fixture.assertBothKept() }
                val reads = fixture.store.loads.get()
                onNodeWithContentDescription(retry).performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) { fixture.store.loads.get() > reads }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(explanation).fetchSemanticsNodes().size == 1 }
                runBlocking { fixture.assertBothKept() }

                onNodeWithContentDescription(back).performScrollTo().performClick()
                onNodeWithTag("recovery-library").assertExists()
                runOnIdle { assertEquals(1, exits) }
                runBlocking { fixture.assertBothKept() }

                // A new presentation must still offer the same preserved save, not a new game.
                runOnIdle { mounted = true }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(explanation).fetchSemanticsNodes().size == 1 }
                onNodeWithContentDescription(discard).performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) { !mounted }
                onNodeWithTag("recovery-library").assertExists()
                runOnIdle { assertEquals(2, exits) }
                runBlocking {
                    assertEquals(Result.Failure(DataError.NotFound), fixture.backing.load(fixture.selected.sessionId))
                    assertEquals(Result.Success(fixture.other), fixture.backing.load(fixture.other.sessionId))
                    assertEquals(1, fixture.store.deletes.get())
                    assertEquals(0, fixture.store.saves.get())
                }
            }
        } finally {
            application.close()
        }
    }

    private class Fixture(
        private val includeContentIdentity: Boolean,
        private val persistedMode: String,
    ) {
        private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
        val definition = WhodunitDefinition(json)
        val payloadValidator = WhodunitPayloadValidator(json)
        private val validator = DefaultCaseValidator(
            json, 1, SemVer(1, 0, 0), DefaultGameRegistry(listOf(definition)),
        )
        val repository = DefaultCaseRepository(
            OfflineRemoteCaseDataSource(),
            InMemoryCachedCaseDataSource(),
            BundledWhodunitCases(
                listOf("last-dinner"),
                { id -> Res.readBytes("files/cases/$id.json").decodeToString(throwOnInvalidSequence = true) },
                json,
            ),
            validator,
            json,
        )
        val backing = InMemorySnapshotStore()
        val store = ObservedStore(backing)
        lateinit var selected: GameSnapshot
        lateinit var other: GameSnapshot

        suspend fun prepare() {
            val current = assertIs<Result.Success<ValidatedCase<WhodunitCase>>>(
                repository.loadCase(CaseId("last-dinner"), payloadValidator),
            ).data
            // A fully valid synthetic prior version, not a corrupt payload or real player record.
            val oldEnvelope = current.envelope.copy(version = SemVer(0, 9, 0))
            val old = assertIs<Result.Success<ValidatedCase<WhodunitCase>>>(
                validator.validate(json.encodeToString(oldEnvelope), payloadValidator),
            ).data
            val players = (1..6).map { Player(PlayerId("p$it"), "Player $it", it - 1) }
            val config = SessionConfig(
                SessionId("incompatible-save"), CaseId("last-dinner"),
                WhodunitIds.ClassicVoteModeId, players, SEED,
            )
            val state = definition.reducer().reduce(
                definition.createInitialState(config), WhodunitAction.AssignRoles(SEED),
                WhodunitReducerContext(FakeClock(Instant.fromEpochMilliseconds(0)), RandomSource.seeded(SEED), old),
            ).newState
            WhodunitStateValidator.requireValidForCase(state, old)
            val identity = old.envelope.contentIdentity()
            selected = GameSnapshot(
                config.sessionId, WhodunitIds.GameId, WHODUNIT_SNAPSHOT_ENGINE_VERSION,
                Instant.fromEpochMilliseconds(0), state.phase.id, definition.snapshotCodec().encode(state),
                buildMap {
                    put("playMode", persistedMode)
                    if (includeContentIdentity) {
                        put("caseVersion", identity.version)
                        put("caseDigest", identity.digest)
                    }
                },
            )
            other = selected.copy(sessionId = SessionId("unrelated-preserved-save"))
            backing.save(selected)
            backing.save(other)
            if (persistedMode == "Solo") {
                assertEquals(Result.Failure(DataError.CorruptedData),
                    loadResumedSession(backing, definition, selected.sessionId))
                return
            }
            val resumed = assertIs<Result.Success<ResumedSession>>(
                loadResumedSession(backing, definition, selected.sessionId),
            ).data
            if (includeContentIdentity) {
                assertIs<Result.Success<Unit>>(validateResumedSessionForCase(resumed, old))
            } else {
                assertEquals(Result.Failure(DataError.CorruptedData), validateResumedSessionForCase(resumed, old))
            }
            assertEquals(Result.Failure(DataError.CorruptedData), validateResumedSessionForCase(resumed, current))
        }

        suspend fun assertBothKept() {
            assertEquals(Result.Success(selected), backing.load(selected.sessionId))
            assertEquals(Result.Success(other), backing.load(other.sessionId))
            assertEquals(0, store.deletes.get())
            assertEquals(0, store.saves.get())
        }
    }

    private class ObservedStore(private val backing: SnapshotStore) : SnapshotStore by backing {
        val loads = AtomicInteger()
        val deletes = AtomicInteger()
        val saves = AtomicInteger()

        override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> {
            loads.incrementAndGet()
            return backing.load(sessionId)
        }

        override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> {
            deletes.incrementAndGet()
            return backing.delete(sessionId)
        }

        override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> {
            saves.incrementAndGet()
            return backing.save(snapshot)
        }
    }

    private companion object {
        const val SEED = 73L
    }
}

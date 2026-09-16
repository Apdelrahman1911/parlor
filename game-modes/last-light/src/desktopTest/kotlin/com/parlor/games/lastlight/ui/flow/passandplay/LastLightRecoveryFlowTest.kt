package com.parlor.games.lastlight.ui.flow.passandplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.SessionId
import com.parlor.core.random.SessionSeedSource
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.core.time.FakeClock
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackCue
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackOutput
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackSettings
import com.parlor.games.lastlight.ui.feedback.LocalLastLightFeedbackOutput
import com.parlor.storage.snapshot.SnapshotStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Exercises the real public route: failed recovery must never fall through into a fresh match. */
@OptIn(ExperimentalTestApi::class)
class LastLightRecoveryFlowTest {
    @Test
    fun retry_and_back_preserve_an_unreadable_save_without_starting_another_match() {
        val fixture = RecoveryRouteFixture()
        val oldLocale = Locale.getDefault()
        try {
            runComposeUiTest {
                setContent { fixture.Content() }
                waitUntil { fixture.store.loads.get() == 1 }
                onNodeWithText("This match could not be opened").assertIsDisplayed()
                onNodeWithText("Retry").performScrollTo().performClick()
                waitUntil { fixture.store.loads.get() == 2 }
                assertEquals(0, fixture.seedCalls.get())
                assertEquals(0, fixture.store.saves.get())
                assertEquals(0, fixture.store.deletes.get())
                onNodeWithText("Back to games").performScrollTo().performClick()
                runOnIdle {
                    assertEquals(1, fixture.exits.get())
                    assertTrue(fixture.store.retained.get())
                }
            }
        } finally {
            fixture.application.close()
            Locale.setDefault(oldLocale)
        }
    }

    @Test
    fun explicit_discard_waits_for_success_and_exposes_a_retryable_delete_failure() {
        val fixture = RecoveryRouteFixture()
        val oldLocale = Locale.getDefault()
        fixture.store.failDelete.set(true)
        try {
            runComposeUiTest {
                setContent { fixture.Content() }
                waitUntil { fixture.store.loads.get() == 1 }
                onNodeWithText("Discard saved match").performScrollTo().performClick()
                waitUntil { fixture.store.deletes.get() == 1 }
                onNodeWithText("The saved match could not be removed. It has been kept so you can retry.")
                    .performScrollTo().assertIsDisplayed()
                assertEquals(0, fixture.exits.get())
                assertTrue(fixture.store.retained.get())
                fixture.store.failDelete.set(false)
                onNodeWithText("Discard saved match").performScrollTo().performClick()
                waitUntil { fixture.exits.get() == 1 }
                assertFalse(fixture.store.retained.get())
                assertEquals(2, fixture.store.deletes.get())
                assertEquals(0, fixture.seedCalls.get())
                assertEquals(0, fixture.store.saves.get())
            }
        } finally {
            fixture.application.close()
            Locale.setDefault(oldLocale)
        }
    }

    @Test
    fun retry_reserves_recovery_before_launch_and_rejects_a_same_frame_discard_callback() {
        val fixture = RecoveryRouteFixture()
        val oldLocale = Locale.getDefault()
        val releaseLoad = CompletableDeferred<Unit>()
        fixture.store.retryGate.set(releaseLoad)
        fixture.store.recoverable.set(fixture.validSnapshot())
        try {
            runComposeUiTest {
                setContent { fixture.Content() }
                waitUntil { fixture.store.loads.get() == 1 }
                val retry = requireNotNull(onNodeWithText("Retry").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
                val discard = requireNotNull(
                    onNodeWithText("Discard saved match").fetchSemanticsNode().config[SemanticsActions.OnClick].action,
                )
                // Invoke the old callbacks without giving Compose a frame to disable the buttons.
                runOnIdle { retry(); discard() }
                waitUntil { fixture.store.loads.get() == 2 }
                assertEquals(0, fixture.store.deletes.get())
                releaseLoad.complete(Unit)
                waitUntil(timeoutMillis = 5_000) { fixture.store.saves.get() > 0 }
                assertEquals(0, fixture.store.deletes.get())
                assertEquals(0, fixture.seedCalls.get())
                assertEquals(0, fixture.exits.get())
                assertTrue(fixture.store.retained.get())
            }
        } finally {
            releaseLoad.complete(Unit)
            fixture.application.close()
            Locale.setDefault(oldLocale)
        }
    }
}

private class RecoveryRouteFixture {
    val store = UnreadableLocalStore()
    val seedCalls = AtomicInteger()
    val exits = AtomicInteger()
    private val feedback = SilentLocalFeedback()
    val application: KoinApplication = koinApplication {
        modules(module {
            single { LastLightDefinition() }
            single<SnapshotStore> { store }
            single<Clock> { FakeClock(Instant.fromEpochMilliseconds(1_000)) }
            single<SessionSeedSource> { SessionSeedSource { seedCalls.incrementAndGet().toLong() } }
        })
    }

    @Composable
    fun Content() {
        KoinIsolatedContext(application) {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLastLightProcessVisibility provides LastLightProcessVisibility(true, 0L),
                LocalLastLightFeedbackOutput provides feedback,
            ) {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        LastLightGameFlow(
                            onBackToHome = { exits.incrementAndGet() },
                            resumeSessionId = SessionId("unreadable-local-match"),
                            modifier = Modifier.size(360.dp, 740.dp),
                        )
                    }
                }
            }
        }
    }

    fun validSnapshot(): GameSnapshot {
        val definition = LastLightDefinition()
        val config = createLocalLastLightSessionConfig(
            players = lastLightLocalPlayers(listOf("Ali", "Basma")),
            randomSeed = 88L,
            restoredSessionId = SessionId("unreadable-local-match"),
        )
        val state = definition.createInitialState(config)
        return GameSnapshot(
            sessionId = config.sessionId,
            gameId = LastLightIds.GameId,
            engineVersion = LastLightSnapshotRecovery.VERSION,
            createdAt = Instant.fromEpochMilliseconds(1_000),
            phaseId = state.phase.id,
            payload = definition.snapshotCodec().encode(state),
            metadata = mapOf(LastLightSnapshotRecovery.PLAY_MODE_KEY to LastLightSnapshotRecovery.PASS_AND_PLAY_MODE),
        )
    }
}

private class UnreadableLocalStore : SnapshotStore {
    val loads = AtomicInteger()
    val saves = AtomicInteger()
    val deletes = AtomicInteger()
    val retained = AtomicBoolean(true)
    val failDelete = AtomicBoolean(false)
    val retryGate = AtomicReference<CompletableDeferred<Unit>?>(null)
    val recoverable = AtomicReference<GameSnapshot?>(null)

    override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> {
        if (loads.incrementAndGet() > 1) {
            retryGate.get()?.await()
            recoverable.get()?.let { return Result.Success(it) }
        }
        return Result.Failure(DataError.CorruptedData)
    }

    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> {
        saves.incrementAndGet()
        return EmptyOk
    }

    override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> {
        deletes.incrementAndGet()
        if (failDelete.get()) return Result.Failure(DataError.IoError("fixture_delete"))
        retained.set(false)
        return EmptyOk
    }

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> =
        Result.Success(if (retained.get()) listOf(SessionId("unreadable-local-match")) else emptyList())
}

private class SilentLocalFeedback : LastLightFeedbackOutput {
    override fun prepare() = Unit
    override fun play(cue: LastLightFeedbackCue, settings: LastLightFeedbackSettings) = Unit
    override fun setForeground(value: Boolean) = Unit
    override fun stopSound() = Unit
    override fun close() = Unit
}

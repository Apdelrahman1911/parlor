package com.parlor.games.whodunit.ui.flow.multiplayer

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.session.SubmitError
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.TestingStoryFixtures
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.WhodunitMultiplayerHostFlow
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.testing.ControlledStartRoom
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.MultiplayerOpenMode
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Actual game host flow/runtime/controller/projections, Desktop Compose provider,
 * and production start validation over a synthetic, pre-admitted room. Language
 * input is a provider seam, not an in-game Settings route or iOS/LAN evidence.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class WhodunitHostLanguageContinuityTest {
    @Test
    fun languageChangesAndRemountRetainActualStartedHost(): Unit = verifyHost(exerciseLifecycle = false)

    @Test
    fun suspendedAndResumingHostRetainsIdentityAndGuardsCommands(): Unit = verifyHost(exerciseLifecycle = true)

    private fun verifyHost(exerciseLifecycle: Boolean) {
        val stories = TestingStoryFixtures()
        val case = runBlocking { stories.loadCase("last-dinner") }
        val players = stories.players
        val lane = HostLane(players)
        val application = koinApplication {
            modules(module {
                single<Clock> { stories.clock }
                single { stories.definition }
            })
        }
        val originalLocale = Locale.getDefault()
        try {
            val owned = lane.open(
                MultiplayerSessionRoute.host(
                    WhodunitIds.GameId,
                    players.first().displayName,
                    case.envelope.caseId,
                    WhodunitIds.ClassicVoteModeId.raw,
                ),
            )
            // The real flow below, not this fixture, must create the runtime.
            assertTrue(owned.runtime.value == null, "Host runtime was created before presentation")
            runComposeUiTest {
                var language by mutableStateOf<AppLanguage?>(AppLanguage.English)
                var mounted by mutableStateOf(true)
                val direction = AtomicReference<LayoutDirection?>()
                val mounts = AtomicInteger()
                val disposals = AtomicInteger()
                val exits = AtomicInteger()
                val retries = AtomicInteger()
                val toast = ParlorToastState()
                setContent {
                    KoinIsolatedContext(application) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(1f),
                            LocalParlorToastState provides toast,
                        ) {
                            ProvideAppLanguage(language) {
                                ParlorTheme(reducedMotion = true) {
                                    if (mounted) {
                                        val currentDirection = LocalLayoutDirection.current
                                        SideEffect { direction.set(currentDirection) }
                                        DisposableEffect(owned) {
                                            mounts.incrementAndGet()
                                            onDispose { disposals.incrementAndGet() }
                                        }
                                        WhodunitMultiplayerHostFlow(
                                            case = case,
                                            modeId = WhodunitIds.ClassicVoteModeId,
                                            players = players,
                                            ownedSession = owned,
                                            sessionOwner = lane.owner,
                                            onBackToLibrary = { exits.incrementAndGet() },
                                            onRetryStart = { retries.incrementAndGet() },
                                            modifier = Modifier.size(360.dp, 760.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                waitUntil(5_000) {
                    lane.pump()
                    owned.runtime.value is WhodunitHostRuntime && lane.room.offeredPeerCount == players.size - 1
                }
                val runtime = owned.runtime.value as WhodunitHostRuntime
                val controller = runtime.session
                val canonical = checkNotNull(controller.canonicalState)
                assertEquals(HostStartGateState.Starting, runtime.startGate.value)
                assertTrue(canonical.value.phase == WhodunitPhase.Setup, "Assignment bypassed the real start barrier")
                assertEquals(0, lane.room.readyPeerCount)
                assertEquals(0, lane.room.committedPeerCount)
                val contentIdentity = case.envelope.contentIdentity()
                lane.runQueued {
                    lane.room.releaseReady(runtime.bridge.protocol) { offer ->
                        offer.caseId == case.envelope.caseId &&
                            offer.modeId == WhodunitIds.ClassicVoteModeId.raw &&
                            offer.caseVersion == contentIdentity.version && offer.caseDigest == contentIdentity.digest
                    }
                }
                waitUntil(5_000) {
                    lane.pump()
                    runtime.startGate.value == HostStartGateState.Started &&
                        canonical.value.phase == WhodunitPhase.PublicIntro &&
                        players.first().id in canonical.value.public.introAcknowledged &&
                        lane.room.snapshottedPeerCount == players.size - 1 &&
                        onAllNodesWithText(case.envelope.title).fetchSemanticsNodes().isNotEmpty()
                }
                val originalState = canonical.value
                assertEquals(players.size - 1, lane.room.committedPeerCount)
                assertTrue(originalState.privatePerPlayer.size == players.size, "Actual role assignment did not complete")

                fun retained(expectedState: WhodunitState) {
                    val current = (lane.owner.state.value as? ProcessMultiplayerState.Active)?.session
                    assertTrue(current === owned, "Process session identity changed")
                    assertTrue(owned.runtime.value === runtime, "Actual game runtime identity changed")
                    assertTrue(runtime.session === controller, "Canonical controller identity changed")
                    assertTrue(controller.canonicalState === canonical, "Canonical StateFlow identity changed")
                    assertTrue(canonical.value === expectedState, "Canonical state reference changed")
                    assertTrue(canonical.value == expectedState, "Canonical state value changed")
                    assertTrue(runtime.seed == owned.hostSeed, "Retained seed ownership changed")
                    assertTrue(canonical.value.hostOnly.randomSeed == owned.hostSeed, "Canonical seed ownership changed")
                    assertTrue(canonical.value.hostOnly == originalState.hostOnly, "Host-only assignment changed")
                    assertTrue(canonical.value.privatePerPlayer == originalState.privatePerPlayer, "Private assignment changed")
                    assertTrue(runtime.scope.coroutineContext[Job]?.isActive == true, "Host runtime was disposed")
                    assertEquals(1, lane.opens)
                    assertEquals(1, lane.room.admissionClosures)
                    assertEquals(0, lane.room.leaves)
                    assertEquals(0, exits.get())
                    assertEquals(0, retries.get())
                    lane.room.requireNoDrops()
                }

                listOf(AppLanguage.Arabic, AppLanguage.English, null, AppLanguage.Arabic, null).forEach { next ->
                    runOnIdle { language = next }
                    val expected = next?.layoutDirection ?: AppLanguage.fromTag(originalLocale.toLanguageTag()).layoutDirection
                    waitUntil(5_000) {
                        lane.pump()
                        direction.get() == expected &&
                            onAllNodesWithText(case.envelope.title).fetchSemanticsNodes().isNotEmpty()
                    }
                    runOnIdle {
                        retained(originalState)
                        assertEquals(1, mounts.get())
                        assertEquals(0, disposals.get())
                        val expectedLocale = next?.tag?.let(Locale::forLanguageTag) ?: originalLocale
                        assertTrue(Locale.getDefault() == expectedLocale, "Desktop locale selection did not settle")
                    }
                }

                runOnIdle { mounted = false }
                waitUntil(5_000) { lane.pump(); disposals.get() == 1 }
                runOnIdle { retained(originalState); mounted = true }
                waitUntil(5_000) {
                    lane.pump()
                    mounts.get() == 2 && onAllNodesWithText(case.envelope.title).fetchSemanticsNodes().isNotEmpty()
                }
                lane.pump()
                runOnIdle { retained(originalState); assertEquals(1, disposals.get()) }

                if (exerciseLifecycle) {
                    // PublicIntro has no running discussion timer; Pause is intentionally
                    // Round-only. This test must not invent an Intro pause transition.
                    listOf(RoomLifecycleState.Suspended(120_000L), RoomLifecycleState.Resuming(120_000L)).forEach { state ->
                        lane.room.lifecycleState.value = state
                        lane.pump()
                        val result = lane.runQueued {
                            controller.submit(WhodunitAction.AcknowledgeIntro(players.first().id))
                        }
                        assertEquals(Result.Failure(SubmitError.SessionSuspended), result)
                        runOnIdle { language = if (language == AppLanguage.Arabic) AppLanguage.English else AppLanguage.Arabic }
                        waitUntil(5_000) { lane.pump(); direction.get() == language?.layoutDirection }
                        runOnIdle { retained(originalState) }
                        assertFalse(canonical.value.public.paused, "PublicIntro unexpectedly became a timed pause")
                    }
                    lane.room.lifecycleState.value = RoomLifecycleState.Active
                    lane.pump()
                    val repeatedOwnAck = lane.runQueued {
                        controller.submit(WhodunitAction.AcknowledgeIntro(players.first().id))
                    }
                    assertTrue(repeatedOwnAck is Result.Success, "Active host failed to accept its idempotent own ACK")
                    if (repeatedOwnAck is Result.Success) {
                        assertFalse(repeatedOwnAck.data.stateChanged)
                        assertFalse(repeatedOwnAck.data.awaitingAuthority)
                    }
                    runOnIdle { retained(originalState) }
                }
            }
            assertTrue(Locale.getDefault() == originalLocale, "Disposed Desktop provider did not restore its locale")
        } finally {
            try {
                lane.close()
            } finally {
                try {
                    application.close()
                } finally {
                    Locale.setDefault(originalLocale)
                }
            }
        }
    }

    private class HostLane(players: List<com.parlor.engine.state.Player>) {
        private val scheduler = TestCoroutineScheduler()
        private val processJob = SupervisorJob()
        private val failure = AtomicReference<Throwable?>()
        private val processScope = CoroutineScope(
            processJob + StandardTestDispatcher(scheduler) + CoroutineExceptionHandler { _, error -> failure.set(error) },
        )
        val room = ControlledStartRoom(players, processScope)
        val owner = ProcessMultiplayerSessionOwner(processScope)
        var opens = 0
            private set

        fun open(route: MultiplayerSessionRoute): ProcessMultiplayerSession {
            val result = runQueued {
                owner.acquire(route, hostSeed = 91L) { mode ->
                    check(mode == MultiplayerOpenMode.Host)
                    opens++
                    Result.Success(room)
                }
            }
            check(result is Result.Success) { "Synthetic host acquisition failed" }
            val session = result.data
            val frozen = runQueued { session.freezeAdmissions() }
            check(frozen is Result.Success && frozen.data == room.members.value) { "Frozen roster differs" }
            return session
        }

        fun pump() {
            scheduler.runCurrent()
            failure.get()?.let { throw AssertionError("Retained host worker failed", it) }
            room.requireNoDrops()
        }

        fun <T> runQueued(block: suspend () -> T): T {
            val job = processScope.async { block() }
            scheduler.runCurrent()
            check(job.isCompleted) { "Synthetic operation did not settle without advancing virtual deadlines" }
            val result = job.getCompleted()
            failure.get()?.let { throw AssertionError("Retained host worker failed", it) }
            return result
        }

        fun close() {
            try {
                val active = (owner.state.value as? ProcessMultiplayerState.Active)?.session
                if (active != null) {
                    val leave = processScope.async { owner.finalLeave(active, SessionEndReason.Cancelled) }
                    scheduler.runCurrent()
                    check(leave.isCompleted) { "Owned host Leave did not settle" }
                    check(leave.getCompleted() is Result.Success) { "Owned host Leave failed" }
                    check(owner.state.value == ProcessMultiplayerState.Idle) { "Owner retained a departed room" }
                    check(active.runtime.value == null) { "Owner retained a closed runtime" }
                    check(active.scope.coroutineContext[Job]?.isCompleted == true) { "Host runtime jobs remain" }
                    check(room.leaves == 1) { "Owned physical room was not closed exactly once" }
                }
            } finally {
                processJob.cancel()
                scheduler.runCurrent()
                check(processJob.isCompleted && room.workerCount == 0) { "Task-owned fixture workers remain" }
            }
        }
    }
}

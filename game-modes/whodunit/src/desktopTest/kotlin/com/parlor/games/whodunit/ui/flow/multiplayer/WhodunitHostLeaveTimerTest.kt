package com.parlor.games.whodunit.ui.flow.multiplayer

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.result.Result
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.ui.flow.WhodunitDiscussionFixture
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.testing.ControlledStartRoom
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Real retained host/route/confirmation; synthetic pre-admitted transport, not physical LAN. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class WhodunitHostLeaveTimerTest {
    @Test
    fun classicHostConfirmationExcludesTimeAndConfirmedLeaveStopsTicker(): Unit = verifyHost()

    @Test
    fun eliminationHostConfirmationExcludesTimeAndConfirmedLeaveStopsTicker(): Unit =
        verifyHost(fixture = WhodunitDiscussionFixture(WhodunitIds.EliminationModeId))

    @Test
    fun lifecycleResumeBehindConfirmationDoesNotRestartHiddenDiscussion(): Unit = verifyHost(lifecycle = true)

    @Test
    fun stayAndLifecycleResumeDoNotReleaseManualPause(): Unit = verifyHost(lifecycle = true, manualPause = true)

    private fun verifyHost(
        fixture: WhodunitDiscussionFixture = WhodunitDiscussionFixture(),
        lifecycle: Boolean = false,
        manualPause: Boolean = false,
    ) {
        val lane = HostLane(fixture)
        val application = koinApplication {
            modules(fixture.bindings(), module { single { lane.owner } })
        }
        try {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var mounted by mutableStateOf(true)
                var backRequest by mutableStateOf(0L)
                var exits = 0
                val toast = ParlorToastState()
                setContent {
                    KoinIsolatedContext(application) {
                        CompositionLocalProvider(LocalDensity provides Density(1f), LocalParlorToastState provides toast) {
                            ProvideAppLanguage(AppLanguage.English) {
                                ParlorTheme(reducedMotion = true) {
                                    if (mounted) WhodunitHostSessionFlow(
                                        transport = lane.transport,
                                        caseId = fixture.case.envelope.caseId,
                                        modeId = fixture.mode,
                                        hostName = fixture.players.first().displayName,
                                        onBackToLibrary = { exits++; mounted = false },
                                        backRequestId = backRequest,
                                        modifier = Modifier.size(360.dp, 760.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 5_000L) {
                    frames(); lane.pump()
                    lane.owned.runtime.value is WhodunitHostRuntime && lane.room.offeredPeerCount == 5
                }
                val runtime = lane.owned.runtime.value as WhodunitHostRuntime
                assertEquals(fixture.players, runtime.players)
                val canonical = checkNotNull(runtime.session.canonicalState)
                assertEquals(WhodunitPhase.Setup, canonical.value.phase)
                assertEquals(HostStartGateState.Starting, runtime.startGate.value)
                val identity = fixture.case.envelope.contentIdentity()
                lane.runQueued {
                    lane.room.releaseReady(runtime.bridge.protocol) { offer ->
                        offer.caseId == fixture.case.envelope.caseId && offer.modeId == fixture.mode.raw &&
                            offer.caseVersion == identity.version && offer.caseDigest == identity.digest
                    }
                }
                waitUntil(timeoutMillis = 5_000L) {
                    frames(); lane.pump()
                    runtime.startGate.value == HostStartGateState.Started &&
                        canonical.value.phase == WhodunitPhase.PublicIntro
                }
                lane.runQueued {
                    // Test-only host progression through the same reducer/start barrier;
                    // this is not a claim that peers sent these readiness inputs.
                    fixture.advanceToDiscussion(canonical.value.public.roleAssignmentGeneration) { action ->
                        assertIs<Result.Success<*>>(runtime.session.submit(action))
                    }
                }
                awaitText("DISCUSSION")
                val initial = canonical.value
                assertEquals(180, assertNotNull(initial.public.timer).remainingSeconds)
                advanceActiveTime(lane, 3_000L)
                assertEquals(177, assertNotNull(canonical.value.public.timer).remainingSeconds)
                if (manualPause) {
                    lane.runQueued { runtime.session.submit(WhodunitAction.Pause) }
                    awaitText("PAUSED")
                }

                repeat(2) { iteration ->
                    if (manualPause || iteration > 0) runOnIdle { backRequest += 2 } else click(OPEN)
                    awaitText("End the room?")
                    val frozen = canonical.value
                    mainClock.advanceTimeBy(CONFIRMATION_MS); lane.advanceBy(CONFIRMATION_MS)
                    assertEquals(frozen, canonical.value)
                    if (lifecycle) {
                        lane.room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
                        frames(); lane.pump()
                        assertTrue(canonical.value.public.paused)
                        lane.room.lifecycleState.value = RoomLifecycleState.Resuming(120_000L)
                        frames(); lane.pump()
                        assertTrue(canonical.value.public.paused)
                        lane.room.lifecycleState.value = RoomLifecycleState.Active
                        frames(); lane.pump()
                        assertEquals(manualPause, canonical.value.public.paused)
                        mainClock.advanceTimeBy(CONFIRMATION_MS); lane.advanceBy(CONFIRMATION_MS)
                        assertEquals(frozen, canonical.value)
                    }
                    assertTrue(lane.owned.runtime.value === runtime)
                    assertTrue(runtime.session.canonicalState === canonical)
                    assertEquals(0, lane.room.leaves)
                    assertEquals(0, exits)
                    click(STAY)
                    frames()
                    advanceActiveTime(lane, 3_000L)
                    assertEquals(
                        assertNotNull(frozen.public.timer).remainingSeconds - if (manualPause) 0 else 3,
                        assertNotNull(canonical.value.public.timer).remainingSeconds,
                    )
                    assertEquals(initial.hostOnly, canonical.value.hostOnly)
                    assertEquals(initial.privatePerPlayer, canonical.value.privatePerPlayer)
                    assertEquals(assertNotNull(initial.public.timer).timerId, assertNotNull(canonical.value.public.timer).timerId)
                    assertEquals(manualPause, canonical.value.public.paused)
                }

                runOnIdle { backRequest++ }
                awaitText("End the room?")
                click("End the multiplayer room for everyone and return home.")
                waitUntil { frames(); lane.pump(); exits == 1 }
                assertEquals(ProcessMultiplayerState.Idle, lane.owner.state.value)
                assertEquals(1, lane.room.leaves)
                assertEquals(null, lane.owned.runtime.value)
                assertFalse(assertNotNull(runtime.scope.coroutineContext[Job]).isActive)
                val afterLeave = canonical.value
                mainClock.advanceTimeBy(CONFIRMATION_MS)
                assertEquals(afterLeave, canonical.value)
                assertEquals(1, exits)
                lane.room.requireNoDrops()
            }
        } finally {
            try { lane.close() } finally { application.close() }
        }
    }

    private fun ComposeUiTest.frames() { repeat(2) { mainClock.advanceTimeByFrame() } }
    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000L) {
        frames(); onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun ComposeUiTest.click(description: String) { onNodeWithContentDescription(description).performClick(); frames() }

    private fun ComposeUiTest.advanceActiveTime(lane: HostLane, millis: Long) {
        // CMP 1.10.3 does not support runComposeUiTest(effectContext=...).
        // Couple the UI clock and retained process scheduler at every frame so
        // the next tick observes the real controller's updated public flow.
        repeat(((millis + 15L) / 16L).toInt()) {
            mainClock.advanceTimeByFrame()
            lane.advanceBy(16L)
        }
    }

    private class HostLane(fixture: WhodunitDiscussionFixture) {
        private val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        private val job = SupervisorJob()
        private val failure = AtomicReference<Throwable?>()
        private val scope = CoroutineScope(job + dispatcher + CoroutineExceptionHandler { _, error -> failure.set(error) })
        val room = ControlledStartRoom(fixture.players, scope)
        val owner = ProcessMultiplayerSessionOwner(scope)
        private val route = MultiplayerSessionRoute.host(
            WhodunitIds.GameId, fixture.players.first().displayName, fixture.case.envelope.caseId, fixture.mode.raw,
        )
        val owned = assertIs<Result.Success<*>>(runQueued {
            owner.acquire(route, hostSeed = WhodunitDiscussionFixture.SEED) { Result.Success(room) }
        }).let { (owner.state.value as ProcessMultiplayerState.Active).session }
        val transport = object : RoomTransport {
            override val capability = TransportCapability(false, 0, 1_048_576)
            override suspend fun host(config: HostConfig) = error("Retained route must not reopen its room")
            override suspend fun join(code: String, displayName: String) = error("Host route cannot join")
        }

        init {
            assertEquals(fixture.players.drop(1).map { it.id }, room.members.value.map { it.playerId })
            assertIs<Result.Success<*>>(runQueued { owned.freezeAdmissions() })
        }

        fun pump() {
            scheduler.runCurrent()
            failure.get()?.let { throw AssertionError("Owned host worker failed", it) }
            room.requireNoDrops()
        }

        fun advanceBy(millis: Long) { scheduler.advanceTimeBy(millis); pump() }

        fun <T> runQueued(block: suspend () -> T): T {
            val result = scope.async { block() }
            pump()
            check(result.isCompleted) { "Host fixture operation did not settle" }
            return result.getCompleted()
        }

        fun close() {
            try {
                if (owner.state.value is ProcessMultiplayerState.Active) {
                    assertIs<Result.Success<*>>(runQueued { owner.finalLeave(owned, SessionEndReason.Cancelled) })
                }
            } finally {
                job.cancel(); pump()
                check(job.isCompleted && room.workerCount == 0) { "Owned host test workers remain" }
            }
        }
    }

    private companion object {
        const val CONFIRMATION_MS = 360_000L
        const val OPEN = "Open options to leave this game."
        const val STAY = "Close the leave options and continue playing."
    }
}

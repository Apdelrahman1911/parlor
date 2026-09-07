package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication

/** Production local entry, confirmation, ticker and saves under the Compose virtual clock. */
@OptIn(ExperimentalTestApi::class)
class WhodunitLocalLeaveTimerTest {
    @Test
    fun classicConfirmationTimeIsExcludedAndStayRestartsOnlyOneTicker(): Unit =
        verifyStay(WhodunitDiscussionFixture())

    @Test
    fun eliminationConfirmationTimeIsExcludedAndStayRestartsOnlyOneTicker(): Unit =
        verifyStay(WhodunitDiscussionFixture(WhodunitIds.EliminationModeId))

    @Test
    fun stayDoesNotReleaseAnExistingSessionPause(): Unit = withLocal(
        actions = listOf(WhodunitAction.Pause),
    ) { fixture, controls ->
        val paused = fixture.savedState()
        assertTrue(paused.public.paused)
        runOnIdle { controls.backRequest++ }
        awaitText(LOCAL_TITLE)
        mainClock.advanceTimeBy(CONFIRMATION_MS)
        assertEquals(paused, fixture.savedState())
        click(STAY)
        awaitText("PAUSED")
        mainClock.advanceTimeBy(5_000L)
        assertEquals(paused, fixture.savedState())
        click("Resume the game.")
        frames()
        mainClock.advanceTimeBy(3_000L)
        awaitSavedSeconds(fixture, paused.public.timer!!.remainingSeconds - 3)
        assertFalse(fixture.savedState().public.paused)
        assertEquals(paused.public.timer!!.remainingSeconds - 3, fixture.savedState().public.timer!!.remainingSeconds)
    }

    @Test
    fun stayDoesNotReleaseAnExistingPerTimerPause(): Unit = withLocal(
        actions = listOf(WhodunitAction.PauseDiscussionTimer),
    ) { fixture, _ ->
        val paused = fixture.savedState()
        click(OPEN)
        awaitText(LOCAL_TITLE)
        mainClock.advanceTimeBy(CONFIRMATION_MS)
        click(STAY)
        frames()
        mainClock.advanceTimeBy(5_000L)
        assertEquals(paused, fixture.savedState())
        assertTrue(fixture.savedState().public.timer!!.paused)
    }

    @Test
    fun failedSaveKeepsTimeFrozenAndSuccessfulSaveResumesWithoutCatchingUp(): Unit = withLocal { fixture, controls ->
        // An unchanged, already-durable state is intentionally deduplicated by
        // the real writer. Fail a new ticker state before testing flush retry.
        val durableBeforeFailure = fixture.savedState()
        fixture.store.failSaves.set(true)
        mainClock.advanceTimeBy(3_000L)
        waitUntil(timeoutMillis = 5_000L) {
            mainClock.advanceTimeBy(0L)
            fixture.attemptedState().public.timer!!.remainingSeconds == durableBeforeFailure.public.timer!!.remainingSeconds - 3
        }
        click(OPEN)
        awaitText(LOCAL_TITLE)
        val frozen = fixture.attemptedState()
        val attempts = fixture.store.attempts.get()
        click(SAVE)
        waitUntil(timeoutMillis = 5_000L) { mainClock.advanceTimeBy(0L); fixture.store.attempts.get() > attempts }
        awaitText(LOCAL_TITLE)
        mainClock.advanceTimeBy(CONFIRMATION_MS)
        assertEquals(frozen, fixture.attemptedState())
        assertEquals(durableBeforeFailure, fixture.savedState())
        assertEquals(0, controls.exits)
        fixture.store.failSaves.set(false)
        click(SAVE)
        awaitText("Synthetic library")
        assertEquals(1, controls.exits)
        mainClock.advanceTimeBy(CONFIRMATION_MS)
        assertEquals(frozen, fixture.savedState())
        runOnIdle { controls.mounted = true }
        awaitGame(fixture)
        assertEquals(frozen, fixture.savedState())
        mainClock.advanceTimeBy(3_000L)
        awaitSavedSeconds(fixture, frozen.public.timer!!.remainingSeconds - 3)
        assertEquals(frozen.public.timer!!.remainingSeconds - 3, fixture.savedState().public.timer!!.remainingSeconds)
        assertEquals(frozen.hostOnly, fixture.savedState().hostOnly)
        assertEquals(frozen.privatePerPlayer, fixture.savedState().privatePerPlayer)
    }

    @Test
    fun lastSecondCannotExpireBehindConfirmationButExpiresAfterStay(): Unit = withLocal(
        actions = listOf(WhodunitAction.TimerTicked(1)),
    ) { fixture, _ ->
        click(OPEN)
        awaitText(LOCAL_TITLE)
        val frozen = fixture.savedState()
        assertEquals(1, frozen.public.timer!!.remainingSeconds)
        mainClock.advanceTimeBy(CONFIRMATION_MS)
        assertEquals(frozen, fixture.savedState())
        click(STAY)
        frames()
        mainClock.advanceTimeBy(1_100L)
        waitUntil(timeoutMillis = 5_000L) { mainClock.advanceTimeBy(0L); fixture.savedState().public.timer == null }
        assertEquals(null, fixture.savedState().public.timer)
        assertEquals(2, assertIs<WhodunitPhase.Round>(fixture.savedState().phase).index)
    }

    private fun verifyStay(fixture: WhodunitDiscussionFixture) = withLocal(fixture) { current, controls ->
        val initial = current.savedState()
        mainClock.advanceTimeBy(3_000L)
        awaitSavedSeconds(current, initial.public.timer!!.remainingSeconds - 3)
        assertEquals(initial.public.timer!!.remainingSeconds - 3, current.savedState().public.timer!!.remainingSeconds)
        repeat(3) { iteration ->
            if (iteration == 0) click(OPEN) else runOnIdle { controls.backRequest += 2 }
            awaitText(LOCAL_TITLE)
            val frozen = current.savedState()
            val writes = current.store.attempts.get()
            // Longer than the entire authored discussion: no timer expiry or catch-up.
            mainClock.advanceTimeBy(CONFIRMATION_MS)
            assertEquals(frozen, current.savedState())
            assertEquals(writes, current.store.attempts.get())
            assertEquals(0, controls.exits)
            click(STAY)
            frames()
            mainClock.advanceTimeBy(3_000L)
            awaitSavedSeconds(current, frozen.public.timer!!.remainingSeconds - 3)
            assertEquals(frozen.public.timer!!.remainingSeconds - 3, current.savedState().public.timer!!.remainingSeconds)
            assertEquals(frozen.public.timer!!.timerId, current.savedState().public.timer!!.timerId)
            assertEquals(initial.hostOnly, current.savedState().hostOnly)
            assertEquals(initial.privatePerPlayer, current.savedState().privatePerPlayer)
        }
    }

    private fun withLocal(
        fixture: WhodunitDiscussionFixture = WhodunitDiscussionFixture(),
        actions: List<WhodunitAction> = emptyList(),
        verify: ComposeUiTest.(WhodunitDiscussionFixture, Controls) -> Unit,
    ) {
        fixture.prepareLocal(actions)
        val application = koinApplication { modules(fixture.bindings()) }
        try {
            runComposeUiTest {
                mainClock.autoAdvance = false
                val controls = Controls()
                val toast = ParlorToastState()
                setContent {
                    KoinIsolatedContext(application) {
                        CompositionLocalProvider(LocalDensity provides Density(1f), LocalParlorToastState provides toast) {
                            ProvideAppLanguage(AppLanguage.English) {
                                ParlorTheme(reducedMotion = true) {
                                    if (controls.mounted) {
                                        WhodunitGameFlow(
                                            onBackToLibrary = { controls.exits++; controls.mounted = false },
                                            resumeSessionId = fixture.sessionId,
                                            backRequestId = controls.backRequest,
                                            modifier = Modifier.size(360.dp, 760.dp),
                                        )
                                    } else Text("Synthetic library")
                                }
                            }
                        }
                    }
                }
                awaitGame(fixture)
                verify(fixture, controls)
            }
        } finally {
            application.close()
        }
    }

    private class Controls {
        var mounted by mutableStateOf(true)
        var backRequest by mutableStateOf(0L)
        var exits = 0
    }

    private fun ComposeUiTest.awaitGame(fixture: WhodunitDiscussionFixture) {
        if (fixture.savedState().public.paused) awaitText("PAUSED") else awaitText("DISCUSSION")
    }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000L) {
        frames()
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.frames() { repeat(2) { mainClock.advanceTimeByFrame() } }
    private fun ComposeUiTest.click(description: String) { onNodeWithContentDescription(description).performClick(); frames() }

    private fun ComposeUiTest.awaitSavedSeconds(fixture: WhodunitDiscussionFixture, seconds: Int) =
        waitUntil(timeoutMillis = 5_000L) {
            // The real writer encodes/saves on Dispatchers.Default, outside
            // Compose virtual time. Drain current-time continuations but do not
            // charge another discussion frame while waiting for that write.
            mainClock.advanceTimeBy(0L)
            fixture.savedState().public.timer?.remainingSeconds == seconds
        }

    private companion object {
        const val CONFIRMATION_MS = 360_000L
        const val OPEN = "Open options to leave this game."
        const val STAY = "Close the leave options and continue playing."
        const val SAVE = "Save this game and return to the home screen."
        const val LOCAL_TITLE = "Return home?"
    }
}

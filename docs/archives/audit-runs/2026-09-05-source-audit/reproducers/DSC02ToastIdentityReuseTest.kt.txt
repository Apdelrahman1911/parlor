package com.parlor.designsystem.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit fixture: unchanged production toast host and state. No manual dismiss. */
@OptIn(ExperimentalTestApi::class)
class DSC02ToastIdentityReuseTest {
    @Test
    fun newlyEnqueuedToastAfterAutomaticDismissMustGetFreshVisibilityAndTimer() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = ParlorToastState(defaultDurationMs = 1_000L)
        state.show("First synthetic notification")
        val producer = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            setContent {
                ParlorTheme(reducedMotion = true) { ParlorToastHost(state) }
            }
            onNodeWithText("First synthetic notification").assertIsDisplayed()
            // Force a legal event schedule: a fresh independent notification
            // arrives immediately after the host's own automatic expiry update,
            // before a frame can remove the old keyed composition. This watcher
            // only calls the production public show API; it never calls dismiss.
            val enqueued = producer.launch(start = CoroutineStart.UNDISPATCHED) {
                state.toasts.first { it.isEmpty() }
                state.show("Replacement synthetic notification")
            }
            mainClock.advanceTimeBy(1_350L)
            waitForIdle()
            runOnIdle {
                assertTrue(enqueued.isCompleted, "Production auto-dismiss must have fired")
                assertEquals(listOf("Replacement synthetic notification"), state.toasts.value.map { it.text })
            }
            onNodeWithText("Replacement synthetic notification").assertIsDisplayed()
            mainClock.advanceTimeBy(1_500L)
            runOnIdle { assertTrue(state.toasts.value.isEmpty(), "Replacement must get its own expiry") }
        } finally {
            producer.cancel()
        }
    }
}

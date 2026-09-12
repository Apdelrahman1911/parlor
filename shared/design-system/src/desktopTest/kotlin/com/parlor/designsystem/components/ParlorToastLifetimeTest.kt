package com.parlor.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercise production timers and composition with no manual dismissal. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ParlorToastLifetimeTest {
    @Test
    fun replacement_after_automatic_dismiss_gets_fresh_visibility_and_timer() =
        assertReplacementLifetime("Replacement synthetic notification")

    @Test
    fun identical_text_after_automatic_dismiss_is_a_new_lifetime() =
        assertReplacementLifetime("First synthetic notification")

    @Test
    fun replacement_state_owner_with_same_numeric_id_gets_its_own_lifetime() = runComposeUiTest {
        mainClock.autoAdvance = false
        val first = ParlorToastState(defaultDurationMs = 1_000L)
        val replacement = ParlorToastState(defaultDurationMs = 1_000L)
        first.show("Original owner")
        replacement.show("Replacement owner")
        assertEquals(first.toasts.value.single().id, replacement.toasts.value.single().id)
        var owner by mutableStateOf(first)
        setContent { ParlorTheme(reducedMotion = true) { ParlorToastHost(owner) } }
        onNodeWithText("Original owner").assertIsDisplayed()
        mainClock.advanceTimeBy(600L)
        runOnIdle { owner = replacement }
        // With a paused test clock, switch the collected flow on the first
        // frame, then render its first emission before inspecting semantics.
        repeat(2) { mainClock.advanceTimeByFrame() }
        onNodeWithText("Replacement owner").assertIsDisplayed()
        mainClock.advanceTimeBy(600L)
        onNodeWithText("Replacement owner").assertIsDisplayed()
        runOnIdle { assertEquals(1, replacement.toasts.value.size) }
        mainClock.advanceTimeBy(900L)
        runOnIdle { assertTrue(replacement.toasts.value.isEmpty()) }
    }

    private fun assertReplacementLifetime(replacement: String) = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = ParlorToastState(defaultDurationMs = 1_000L)
        state.show("First synthetic notification")
        val producer = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
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
                state.show(replacement)
            }
            mainClock.advanceTimeBy(1_350L)
            waitForIdle()
            runOnIdle {
                assertTrue(enqueued.isCompleted, "Production auto-dismiss must have fired")
                assertEquals(listOf(replacement), state.toasts.value.map { it.text })
            }
            onNodeWithText(replacement).assertIsDisplayed()
            mainClock.advanceTimeBy(1_500L)
            runOnIdle { assertTrue(state.toasts.value.isEmpty(), "Replacement must get its own expiry") }
        } finally {
            producer.cancel()
        }
    }
}

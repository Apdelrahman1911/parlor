package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WordFrameRestorationTest {
    @Test
    fun restoration_after_authority_advanced_does_not_apply_an_old_phases_scroll() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })
        var showing by mutableStateOf(true)
        var phase by mutableStateOf("old-phase")
        setContent {
            if (showing) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    GameUiFrame {
                        WordFrame(Modifier, phase) {
                            Text("Public phase header", Modifier.testTag("phase-header"))
                            Box(Modifier.height(1200.dp))
                            Text("Last public action", Modifier.testTag("last-action"))
                        }
                    }
                }
            }
        }
        onNodeWithTag("last-action").performScrollTo().assertIsDisplayed()
        var saved: Map<String, List<Any?>> = emptyMap()
        runOnIdle { saved = registry.performSave(); showing = false }
        waitForIdle()
        // The authoritative game advanced while its UI was absent, before recreation.
        runOnIdle {
            phase = "new-phase"
            registry = SaveableStateRegistry(restoredValues = saved) { true }
            showing = true
        }
        onNodeWithTag("phase-header").assertIsDisplayed()
        onNodeWithTag("last-action").assertIsNotDisplayed()
    }

    @Test
    fun same_phase_recreation_restores_scroll_but_new_phase_starts_at_the_header() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })
        var showing by mutableStateOf(true)
        var phase by mutableStateOf("first-phase")
        setContent {
            if (showing) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    GameUiFrame {
                        WordFrame(Modifier, phase) {
                            Text("Public phase header", Modifier.testTag("phase-header"))
                            Box(Modifier.height(1200.dp))
                            Text("Last public action", Modifier.testTag("last-action"))
                        }
                    }
                }
            }
        }
        onNodeWithTag("last-action").performScrollTo().assertIsDisplayed()
        onNodeWithTag("phase-header").assertIsNotDisplayed()
        var saved: Map<String, List<Any?>> = emptyMap()
        runOnIdle { saved = registry.performSave(); showing = false }
        waitForIdle()
        // Exercise actual saveable providers without Desktop's unimplemented platformEncodeDecode stub.
        runOnIdle {
            registry = SaveableStateRegistry(restoredValues = saved) { true }
            showing = true
        }
        onNodeWithTag("last-action").assertIsDisplayed()
        onNodeWithTag("phase-header").assertIsNotDisplayed()
        runOnIdle { phase = "second-phase" }
        onNodeWithTag("phase-header").assertIsDisplayed()
        onNodeWithTag("last-action").assertIsNotDisplayed()
    }
}

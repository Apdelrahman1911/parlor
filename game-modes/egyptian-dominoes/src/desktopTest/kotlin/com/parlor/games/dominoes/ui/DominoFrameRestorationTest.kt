package com.parlor.games.dominoes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DominoFrameRestorationTest {
    @Test
    fun same_phase_recreation_restores_scroll_but_new_phase_starts_at_the_header() = runComposeUiTest {
        var phase by mutableStateOf("first-phase")
        val restoration = StateRestorationTester(this)
        restoration.setContent {
            GameUiFrame {
                DominoFrame(Modifier, phase) {
                    Text("Public phase header", Modifier.testTag("phase-header"))
                    Box(Modifier.height(1200.dp))
                    Text("Last public action", Modifier.testTag("last-action"))
                }
            }
        }
        onNodeWithTag("last-action").performScrollTo().assertIsDisplayed()
        onNodeWithTag("phase-header").assertIsNotDisplayed()
        restoration.emulateSaveAndRestore()
        onNodeWithTag("last-action").assertIsDisplayed()
        runOnIdle { phase = "second-phase" }
        onNodeWithTag("phase-header").assertIsDisplayed()
        onNodeWithTag("last-action").assertIsNotDisplayed()
    }
}

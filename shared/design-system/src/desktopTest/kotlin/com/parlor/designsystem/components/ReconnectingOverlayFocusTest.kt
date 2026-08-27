package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReconnectingOverlayFocusTest {
    @Test
    fun overlay_excludes_covered_controls_from_keyboard_focus_until_hidden() =
        runComposeUiTest {
            val overlayVisible = mutableStateOf(true)
            setContent {
                ParlorTheme(reducedMotion = true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .coveredByReconnectingOverlay(overlayVisible.value),
                        ) {
                            ParlorButton(
                                label = "Covered action",
                                contentDescription = COVERED_ACTION_DESCRIPTION,
                                onClick = {},
                                modifier = Modifier.testTag(COVERED_ACTION_TAG),
                            )
                        }
                        if (overlayVisible.value) {
                            ReconnectingOverlay(
                                title = "Reconnecting",
                                leaveLabel = "Leave",
                                leaveContentDescription = LEAVE_ACTION_DESCRIPTION,
                                onLeave = {},
                            )
                        }
                    }
                }
            }

            onNodeWithTag(COVERED_ACTION_TAG).assertDoesNotExist()
            onRoot().performKeyInput { pressKey(Key.Tab) }
            onNodeWithContentDescription(LEAVE_ACTION_DESCRIPTION).assertIsFocused()

            runOnIdle { overlayVisible.value = false }

            onRoot().performKeyInput { pressKey(Key.Tab) }
            onNodeWithTag(COVERED_ACTION_TAG).assertIsFocused()
        }

    private companion object {
        const val COVERED_ACTION_TAG = "covered-action"
        const val COVERED_ACTION_DESCRIPTION = "Covered reconnecting content action"
        const val LEAVE_ACTION_DESCRIPTION = "Leave the reconnecting screen"
    }
}

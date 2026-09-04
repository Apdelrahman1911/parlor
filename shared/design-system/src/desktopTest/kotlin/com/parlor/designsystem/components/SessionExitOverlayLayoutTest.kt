package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SessionExitOverlayLayoutTest {
    @Test
    fun safe_content_starts_below_the_measured_exit_control() = runComposeUiTest {
        setContent {
            ParlorTheme(reducedMotion = true) {
                SessionExitOverlay(
                    visible = true,
                    onClick = {},
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .parlorSafeContentPadding(0.dp),
                    ) {
                        Box(modifier = Modifier.size(8.dp).testTag(CONTENT_TAG))
                    }
                }
            }
        }

        val exitBottom = onNode(hasClickAction()).getUnclippedBoundsInRoot().bottom
        val contentTop = onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot().top

        assertTrue(contentTop >= exitBottom)
    }

    private companion object {
        const val CONTENT_TAG = "safe-content"
    }
}

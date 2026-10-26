package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveOptionsTest {
    @Test
    fun phone_and_enlarged_text_stack_without_changing_reading_order() {
        verifyLayout(width = 320, fontScale = 1f, stacked = true)
        verifyLayout(width = 700, fontScale = 2f, stacked = true)
    }

    @Test
    fun wide_layout_follows_ltr_and_rtl_reading_order() {
        verifyLayout(width = 700, fontScale = 1f, stacked = false)
        verifyLayout(width = 700, fontScale = 1f, stacked = false, direction = LayoutDirection.Rtl)
    }

    private fun verifyLayout(
        width: Int,
        fontScale: Float,
        stacked: Boolean,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                ParlorTheme(reducedMotion = true) {
                    Box(Modifier.size(width.dp, 600.dp)) {
                        AdaptiveOptions(first = { Text("First") }, second = { Text("Second") })
                    }
                }
            }
        }
        val first = onNodeWithText("First").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Second").fetchSemanticsNode().boundsInRoot
        if (stacked) {
            assertTrue(second.top >= first.bottom)
        } else {
            assertEquals(first.top, second.top)
            if (direction == LayoutDirection.Ltr) assertTrue(first.right < second.left)
            else assertTrue(second.right < first.left)
        }
    }
}

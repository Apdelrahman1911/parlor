package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ContextRibbonLayoutTest {
    @Test
    fun compact_large_text_status_has_full_width_and_instruction_follows_in_both_directions() {
        verifyRibbon(LayoutDirection.Ltr, "PRIVATE SCREEN", "DO NOT PASS")
        verifyRibbon(LayoutDirection.Rtl, "شاشة خاصة", "لا تمرّر الجهاز")
    }

    private fun verifyRibbon(direction: LayoutDirection, label: String, detail: String) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 2f),
                LocalLayoutDirection provides direction,
            ) {
                ParlorTheme(reducedMotion = true) {
                    Box(Modifier.size(320.dp, 640.dp).padding(horizontal = 32.dp)) {
                        ContextRibbon(label = label, detail = detail, tone = ParlorContextTone.Private)
                    }
                }
            }
        }
        val labelBounds = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val detailBounds = onNodeWithText(detail, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(labelBounds.width >= 200f, "Status must not be squeezed into half a phone-width column")
        assertTrue(detailBounds.top >= labelBounds.bottom, "Instruction should follow, not compete for label width")
    }
}

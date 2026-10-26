package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScreenHeaderLayoutTest {
    @Test
    fun enlarged_title_uses_full_phone_width_below_navigation_and_back_stays_operable() = runComposeUiTest {
        var backs = 0
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ParlorTheme(reducedMotion = true) {
                    Box(Modifier.size(320.dp, 740.dp)) {
                        ScreenHeader(
                            title = "How will you play?",
                            eyebrow = "GAME SETUP",
                            onBack = { backs++ },
                            backContentDescription = "Back to games",
                        )
                    }
                }
            }
        }
        val titleNode = onNodeWithText("How will you play?").fetchSemanticsNode()
        assertTrue(titleNode.config.contains(SemanticsProperties.Heading))
        val title = titleNode.boundsInRoot
        val back = onNodeWithContentDescription("Back to games").fetchSemanticsNode().boundsInRoot
        assertEquals(320f, title.width)
        assertTrue(title.top >= back.bottom)
        onNodeWithContentDescription("Back to games").performClick()
        runOnIdle { assertEquals(1, backs) }
    }
}

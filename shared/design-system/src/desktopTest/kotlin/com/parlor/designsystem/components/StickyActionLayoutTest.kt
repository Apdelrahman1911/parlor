package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StickyActionLayoutTest {
    @Test
    fun measured_two_button_bar_never_overlaps_content_at_large_text_scale() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                    ParlorTheme(reducedMotion = true) {
                        StickyActionLayout(
                            modifier = Modifier.size(width = 320.dp, height = 640.dp),
                            content = {
                                Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
                            },
                            actions = {
                                ParlorButton(
                                    label = "A long primary action that wraps at large text scale",
                                    contentDescription = "Primary",
                                    onClick = {},
                                    modifier = Modifier.testTag(FIRST_ACTION_TAG),
                                )
                                ParlorButton(
                                    label = "A long secondary action that also wraps",
                                    contentDescription = "Secondary",
                                    onClick = {},
                                    modifier = Modifier.testTag(SECOND_ACTION_TAG),
                                )
                            },
                        )
                    }
                }
            }

            val contentBounds = onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
            val firstActionBounds = onNodeWithTag(FIRST_ACTION_TAG).getUnclippedBoundsInRoot()
            val secondActionBounds = onNodeWithTag(SECOND_ACTION_TAG).getUnclippedBoundsInRoot()

            assertTrue(
                contentBounds.bottom > contentBounds.top,
                "Expected non-empty content bounds: content=$contentBounds, " +
                    "first=$firstActionBounds, second=$secondActionBounds",
            )
            assertTrue(
                firstActionBounds.bottom - firstActionBounds.top > ParlorButtonMinimumHeight,
                "Expected the first large-text label to wrap: $firstActionBounds",
            )
            assertTrue(
                secondActionBounds.bottom - secondActionBounds.top > ParlorButtonMinimumHeight,
                "Expected the second large-text label to wrap: $secondActionBounds",
            )
            assertTrue(
                contentBounds.bottom <= firstActionBounds.top,
                "Content $contentBounds overlaps the first action $firstActionBounds",
            )
            assertTrue(
                firstActionBounds.bottom <= secondActionBounds.top,
                "First action $firstActionBounds overlaps the second action $secondActionBounds",
            )
        }

    private companion object {
        const val CONTENT_TAG = "sticky-content"
        const val FIRST_ACTION_TAG = "sticky-primary-action"
        const val SECOND_ACTION_TAG = "sticky-secondary-action"
    }
}

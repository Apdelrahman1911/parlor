package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ParlorImePaddingTest {
    @Test
    fun keyboard_and_safe_area_are_consumed_once_and_restore_after_dismissal() = runComposeUiTest {
        val keyboardHeight = mutableStateOf(0.dp)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides direction.value,
            ) {
                Box(Modifier.size(width = 320.dp, height = 640.dp)) {
                    Box(
                        Modifier.fillMaxSize()
                            .parlorImePadding(WindowInsets(bottom = keyboardHeight.value)),
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .windowInsetsPadding(WindowInsets(top = 20.dp, bottom = 34.dp)),
                        ) {
                            Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
                        }
                    }
                }
            }
        }

        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
            listOf(0.dp, 300.dp, 360.dp, 0.dp, 300.dp, 0.dp).forEach { height ->
                runOnIdle {
                    direction.value = layoutDirection
                    keyboardHeight.value = height
                }
                val content = onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
                assertEquals(20.dp, content.top)
                assertEquals(640.dp - maxOf(height, 34.dp), content.bottom)
                assertEquals(320.dp, content.right - content.left)
            }
        }
    }

    private companion object {
        const val CONTENT_TAG = "ime-safe-content"
    }
}

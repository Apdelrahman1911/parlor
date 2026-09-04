package com.parlor.designsystem.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class ParlorToastHostInsetsTest {
    @Test
    fun `toast host keeps horizontal and bottom safe areas without adding a top inset`() {
        val density = Density(1f)
        val insets = toastHostSafeAreaInsets(
            navigationBars = WindowInsets(left = 3, top = 5, right = 7, bottom = 11),
            displayCutout = WindowInsets(left = 13, top = 17, right = 19, bottom = 23),
        )

        assertEquals(13, insets.getLeft(density, LayoutDirection.Ltr))
        assertEquals(0, insets.getTop(density))
        assertEquals(19, insets.getRight(density, LayoutDirection.Ltr))
        assertEquals(23, insets.getBottom(density))
    }
}

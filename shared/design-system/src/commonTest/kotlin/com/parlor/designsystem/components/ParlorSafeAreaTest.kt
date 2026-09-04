package com.parlor.designsystem.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ParlorSafeAreaTest {
    @Test
    fun `visual spacing is added outside every safe-area edge`() {
        val density = Density(1f)
        val insets = addVisualSpacing(
            safeArea = WindowInsets(left = 7, top = 11, right = 13, bottom = 17),
            horizontal = 19.dp,
            top = 23.dp,
            bottom = 29.dp,
        )

        assertEquals(26, insets.getLeft(density, LayoutDirection.Ltr))
        assertEquals(34, insets.getTop(density))
        assertEquals(32, insets.getRight(density, LayoutDirection.Ltr))
        assertEquals(46, insets.getBottom(density))
    }
}

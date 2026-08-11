package com.parlor.designsystem.components

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenHeaderDirectionTest {
    @Test
    fun back_chevron_points_toward_the_logical_previous_edge() {
        assertEquals("‹", backChevronGlyph(LayoutDirection.Ltr))
        assertEquals("›", backChevronGlyph(LayoutDirection.Rtl))
    }
}

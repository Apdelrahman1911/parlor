package com.parlor.designsystem.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionExitOverlayTest {
    @Test
    fun `content starts after the measured affordance and both visual margins`() {
        assertEquals(
            104.dp,
            sessionExitContentTop(edgeSpacing = 16.dp, affordanceHeight = 72.dp),
        )
    }
}

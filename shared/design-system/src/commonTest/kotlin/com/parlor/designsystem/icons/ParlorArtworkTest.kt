package com.parlor.designsystem.icons

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ParlorArtworkTest {
    @Test
    fun artwork_is_cached_non_directional_and_uses_one_reviewed_viewport() {
        assertEquals(6, ParlorArtwork.entries.size)
        assertEquals(6, ParlorArtwork.entries.map { it.imageVector.name }.toSet().size)
        for (artwork in ParlorArtwork.entries) {
            val image = artwork.imageVector
            assertSame(image, artwork.imageVector)
            assertFalse(image.autoMirror)
            assertEquals(24.dp, image.defaultWidth)
            assertEquals(24.dp, image.defaultHeight)
            assertEquals(24f, image.viewportWidth)
            assertEquals(24f, image.viewportHeight)
        }
    }
}

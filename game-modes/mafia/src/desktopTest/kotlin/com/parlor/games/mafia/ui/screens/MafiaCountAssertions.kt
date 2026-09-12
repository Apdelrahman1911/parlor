package com.parlor.games.mafia.ui.screens

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Check the actual count frame and pixels, not a reconstructed paragraph's unused space. */
internal fun SemanticsNodeInteraction.assertReadableMafiaCount() {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertEquals(1, layouts.size)
    val layout = layouts.single()
    assertEquals(1, layout.lineCount, "A tally count must fit on one line")
    val measured = fetchSemanticsNode().size
    // Compose 1.10.3's plain-String Text semantics reconstruct a MultiParagraph
    // at the incoming maxWidth, unlike the intrinsic-width Paragraph it paints.
    // hasVisualOverflow/getBoundingBox on that reconstruction are not draw bounds.
    // The numeric text's intrinsic width still measures all its actual glyphs.
    assertTrue(
        measured.width >= ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth),
        "Count lacks its full intrinsic width: $measured / ${layout.multiParagraph.intrinsics.maxIntrinsicWidth}",
    )
    assertTrue(measured.height >= ceil(layout.multiParagraph.height))
    val foreground = layout.layoutInput.style.color
    val pixels = captureToImage().toPixelMap()
    assertTrue(
        (0 until pixels.height).any { y ->
            (0 until pixels.width).any { x ->
                val pixel = pixels[x, y]
                abs(pixel.red - foreground.red) < 0.05f &&
                    abs(pixel.green - foreground.green) < 0.05f &&
                    abs(pixel.blue - foreground.blue) < 0.05f
            }
        },
        "The displayed numeric frame contains no rendered foreground pixels",
    )
}

package com.parlor.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Decorative, non-directional motifs. Identity is independent of accent and language. */
enum class ParlorArtwork(val imageVector: ImageVector) {
    CaseFile(artworkVector("CaseFile") {
        moveTo(5f, 3f)
        lineTo(14f, 3f)
        lineTo(19f, 8f)
        lineTo(19f, 12f)
        moveTo(5f, 3f)
        lineTo(5f, 21f)
        lineTo(10f, 21f)
        moveTo(14f, 3f)
        lineTo(14f, 8f)
        lineTo(19f, 8f)
        moveTo(8f, 8f)
        lineTo(11f, 8f)
        moveTo(8f, 11f)
        lineTo(12f, 11f)
        moveTo(18f, 17f)
        arcTo(3f, 3f, 0f, true, true, 12f, 17f)
        arcTo(3f, 3f, 0f, true, true, 18f, 17f)
        moveTo(17.2f, 19.2f)
        lineTo(21f, 23f)
    }),
    Moon(artworkVector("Moon") {
        moveTo(20f, 15f)
        curveTo(16f, 16f, 11f, 13f, 11f, 8f)
        curveTo(11f, 6f, 11.7f, 4f, 13f, 3f)
        curveTo(7f, 2.5f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 21f, 12f, 21f)
        curveTo(15.5f, 21f, 18.5f, 19f, 20f, 15f)
        close()
    }),
    Cards(artworkVector("Cards") {
        moveTo(8f, 3f)
        lineTo(19f, 3f)
        lineTo(19f, 21f)
        lineTo(8f, 21f)
        close()
        moveTo(5f, 5f)
        lineTo(3f, 19f)
        lineTo(5f, 19.5f)
        moveTo(14f, 8f)
        curveTo(12f, 10f, 11f, 12f, 11f, 14f)
        curveTo(11f, 17f, 16f, 17f, 16f, 14f)
        curveTo(16f, 12f, 14f, 12f, 14f, 8f)
        close()
    }),
    Tiles(artworkVector("Tiles") {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        lineTo(18f, 21f)
        lineTo(6f, 21f)
        close()
        moveTo(6f, 12f)
        lineTo(18f, 12f)
        pip(12f, 7.5f)
        pip(10f, 15f)
        pip(14f, 18f)
    }),
    Wink(artworkVector("Wink") {
        moveTo(2f, 10f)
        curveTo(5f, 6f, 9f, 6f, 12f, 10f)
        curveTo(9f, 14f, 5f, 14f, 2f, 10f)
        close()
        pip(7f, 10f)
        moveTo(15f, 11f)
        curveTo(17f, 9f, 20f, 9f, 22f, 11f)
        moveTo(7f, 17f)
        curveTo(10f, 21f, 16f, 21f, 19f, 17f)
    }),
    Conversation(artworkVector("Conversation") {
        moveTo(3f, 4f)
        lineTo(21f, 4f)
        lineTo(21f, 17f)
        lineTo(9f, 17f)
        lineTo(5f, 21f)
        lineTo(5f, 17f)
        lineTo(3f, 17f)
        close()
        pip(7f, 10.5f)
        pip(12f, 10.5f)
        pip(17f, 10.5f)
    }),
}

private fun artworkVector(name: String, builder: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = "Parlor.Artwork.$name",
    defaultWidth = ARTWORK_VIEWPORT.dp,
    defaultHeight = ARTWORK_VIEWPORT.dp,
    viewportWidth = ARTWORK_VIEWPORT,
    viewportHeight = ARTWORK_VIEWPORT,
    autoMirror = false,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = builder,
    )
}.build()

private fun PathBuilder.pip(x: Float, y: Float) {
    moveTo(x - 0.5f, y)
    arcTo(0.5f, 0.5f, 0f, true, true, x + 0.5f, y)
    arcTo(0.5f, 0.5f, 0f, true, true, x - 0.5f, y)
    close()
}

private const val ARTWORK_VIEWPORT = 24f

package com.parlor.games.dominoes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
internal fun DominoTileFace(left: Int, right: Int, modifier: Modifier, covered: Boolean = false, vertical: Boolean = false) {
    Canvas(modifier) { drawDominoTile(left, right, covered, vertical) }
}

/** Vector-drawn ivory, recessed pips, a brass pin and a raised edge; all artwork is code-native. */
internal fun DrawScope.drawDominoTile(left: Int, right: Int, covered: Boolean, vertical: Boolean = false) {
    val length = if (vertical) size.height else size.width
    val thickness = if (vertical) size.width else size.height
    rotate(if (vertical) 90f else 0f) {
        val start = center - Offset(length / 2, thickness / 2)
        val face = Size(length, thickness * .94f)
        val corner = CornerRadius(thickness * .13f)
        drawRoundRect(Color.Black.copy(alpha = .25f), start + Offset(thickness * .05f, thickness * .12f), face, corner)
        drawRoundRect(Color(0xFFBBA780), start + Offset(0f, thickness * .07f), face, corner)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFFAE8), Color(0xFFDFD1B2)), start, start + Offset(length, thickness)),
            start, face, corner)
        drawRoundRect(Color.White.copy(alpha = .7f), start + Offset(thickness * .025f, thickness * .025f),
            Size(face.width - thickness * .05f, face.height - thickness * .05f), corner, style = Stroke(thickness * .025f))
        if (covered) {
            drawTileBack(start, length, thickness)
        } else {
            drawLine(Color(0xFFBAA983), Offset(center.x, start.y + thickness * .15f),
                Offset(center.x, start.y + thickness * .8f), thickness * .035f)
            drawCircle(Color(0xFF9A814D), thickness * .045f, center - Offset(0f, thickness * .03f))
            drawPips(left, start + Offset(length * .25f, thickness * .47f), thickness)
            drawPips(right, start + Offset(length * .75f, thickness * .47f), thickness)
        }
    }
}

private fun DrawScope.drawTileBack(start: Offset, length: Float, thickness: Float) {
    val inset = thickness * .16f
    drawRoundRect(Color(0xFF6E624B).copy(alpha = .65f), start + Offset(inset, inset),
        Size(length - inset * 2, thickness * .94f - inset * 2), CornerRadius(inset * .6f), style = Stroke(thickness * .025f))
    val diamond = Path().apply {
        moveTo(center.x, center.y - thickness * .23f)
        lineTo(center.x + thickness * .24f, center.y)
        lineTo(center.x, center.y + thickness * .19f)
        lineTo(center.x - thickness * .24f, center.y)
        close()
    }
    drawPath(diamond, Color(0xFF86714A), style = Stroke(thickness * .035f))
}

private fun DrawScope.drawPips(number: Int, origin: Offset, unit: Float) {
    val points = buildList {
        if (number % 2 == 1) add(Offset.Zero)
        if (number >= 2) { add(Offset(-1f, -1f)); add(Offset(1f, 1f)) }
        if (number >= 4) { add(Offset(1f, -1f)); add(Offset(-1f, 1f)) }
        if (number == 6) { add(Offset(-1f, 0f)); add(Offset(1f, 0f)) }
    }
    points.forEach { point ->
        val position = origin + point * (unit * .24f)
        drawCircle(Color(0xFFB5A88F), unit * .085f, position + Offset(0f, unit * .018f))
        drawCircle(Color(0xFF252923), unit * .075f, position)
        drawCircle(Color(0xFF4C5245), unit * .045f, position - Offset(unit * .009f, unit * .008f))
    }
}

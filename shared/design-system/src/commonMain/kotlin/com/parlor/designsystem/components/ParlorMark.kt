package com.parlor.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parlor.designsystem.theme.ParlorTheme

/** Decorative Parlor flame seal. Callers provide any spoken brand label. */
@Composable
fun ParlorMark(
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.accentEmber.copy(alpha = MARK_SURFACE_ALPHA))
            .border(ParlorTheme.borders.hairline, colors.borderAccent, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ParlorTheme.iconSize.l)) {
            val flame = Path().apply {
                moveTo(size.width * 0.57f, size.height * 0.04f)
                cubicTo(
                    size.width * 0.64f,
                    size.height * 0.30f,
                    size.width * 0.39f,
                    size.height * 0.39f,
                    size.width * 0.44f,
                    size.height * 0.60f,
                )
                cubicTo(
                    size.width * 0.47f,
                    size.height * 0.73f,
                    size.width * 0.58f,
                    size.height * 0.77f,
                    size.width * 0.63f,
                    size.height * 0.74f,
                )
                cubicTo(
                    size.width * 0.58f,
                    size.height * 0.58f,
                    size.width * 0.68f,
                    size.height * 0.47f,
                    size.width * 0.76f,
                    size.height * 0.35f,
                )
                cubicTo(
                    size.width * 0.94f,
                    size.height * 0.56f,
                    size.width * 0.90f,
                    size.height * 0.92f,
                    size.width * 0.53f,
                    size.height * 0.96f,
                )
                cubicTo(
                    size.width * 0.20f,
                    size.height * 0.98f,
                    size.width * 0.07f,
                    size.height * 0.72f,
                    size.width * 0.19f,
                    size.height * 0.48f,
                )
                cubicTo(
                    size.width * 0.27f,
                    size.height * 0.33f,
                    size.width * 0.45f,
                    size.height * 0.23f,
                    size.width * 0.57f,
                    size.height * 0.04f,
                )
                close()
            }
            drawPath(flame, color = colors.accentEmber)
        }
    }
}

private const val MARK_SURFACE_ALPHA = 0.12f

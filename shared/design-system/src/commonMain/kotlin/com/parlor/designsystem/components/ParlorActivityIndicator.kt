package com.parlor.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Indeterminate activity status that remains semantically indeterminate while
 * replacing continuous rotation with a static open ring under reduced motion.
 */
@Composable
fun ParlorActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = ParlorTheme.colors.accentEmber,
    trackColor: Color = ParlorTheme.colors.borderSubtle,
    strokeWidth: Dp = ParlorTheme.borders.strong,
) {
    val semanticModifier = modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    if (ParlorTheme.reducedMotion) {
        Canvas(modifier = semanticModifier) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(width = size.width - stroke, height = size.height - stroke)
            val style = Stroke(width = stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = style,
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = STATIC_ACTIVITY_SWEEP_DEGREES,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = style,
            )
        }
    } else {
        CircularProgressIndicator(
            modifier = semanticModifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth,
        )
    }
}

private const val STATIC_ACTIVITY_SWEEP_DEGREES: Float = 270f

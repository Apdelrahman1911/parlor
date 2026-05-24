package com.parlor.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * A flickering candle flame used in place of generic Material spinners.
 *
 * Anatomy (bottom-up):
 *  - A faint ember bloom halo so the flame "lights the room."
 *  - The outer flame body — a tear-drop quadratic curve, ember-coloured.
 *  - The inner flame core — a smaller tear-drop in parchment colour.
 *  - The wick — a 2px line beneath the flame.
 *  - A slow flicker that scales the flame vertically and warps its tip.
 *
 * Reduced-motion users see the flame at rest with no flicker.
 */
@Composable
fun CandleFlame(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val colors = ParlorTheme.colors
    val reduced = ParlorTheme.reducedMotion
    val flicker: Float = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "candle-spinner")
        val v by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flicker",
        )
        v
    }
    val sway: Float = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "candle-sway")
        val v by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sway",
        )
        v
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val flameTopY = h * 0.10f * (1f / flicker.coerceAtLeast(0.6f))
            val flameBaseY = h * 0.78f
            val flameHalfW = w * 0.22f

            val warpedTipX = cx + sway * w * 0.04f

            // Bloom halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentEmber.copy(alpha = 0.22f * flicker),
                        Color.Transparent,
                    ),
                    center = Offset(cx, h * 0.45f),
                    radius = w * 0.55f,
                ),
                center = Offset(cx, h * 0.45f),
                radius = w * 0.55f,
            )

            // Outer flame body
            val outerPath = Path().apply {
                moveTo(cx, flameBaseY)
                quadraticBezierTo(
                    x1 = cx - flameHalfW,
                    y1 = h * 0.55f,
                    x2 = warpedTipX,
                    y2 = flameTopY,
                )
                quadraticBezierTo(
                    x1 = cx + flameHalfW,
                    y1 = h * 0.55f,
                    x2 = cx,
                    y2 = flameBaseY,
                )
                close()
            }
            drawPath(
                path = outerPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.accentEmberGlow,
                        colors.accentEmber,
                        colors.accentEmberDeep,
                    ),
                    startY = flameTopY,
                    endY = flameBaseY,
                ),
            )

            // Inner flame core (smaller, brighter)
            val innerHalfW = flameHalfW * 0.45f
            val innerTopY = h * 0.30f
            val innerBaseY = h * 0.70f
            val innerPath = Path().apply {
                moveTo(cx, innerBaseY)
                quadraticBezierTo(
                    x1 = cx - innerHalfW,
                    y1 = h * 0.55f,
                    x2 = warpedTipX,
                    y2 = innerTopY,
                )
                quadraticBezierTo(
                    x1 = cx + innerHalfW,
                    y1 = h * 0.55f,
                    x2 = cx,
                    y2 = innerBaseY,
                )
                close()
            }
            drawPath(
                path = innerPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.accentParchment.copy(alpha = 0.92f),
                        colors.accentEmberGlow.copy(alpha = 0.85f),
                    ),
                    startY = innerTopY,
                    endY = innerBaseY,
                ),
            )

            // Wick — 2px dark line below the flame
            drawRect(
                color = colors.textPrimary.copy(alpha = 0.45f),
                topLeft = Offset(cx - 1.dp.toPx(), flameBaseY),
                size = Size(2.dp.toPx(), h * 0.12f),
            )
        }
    }
}

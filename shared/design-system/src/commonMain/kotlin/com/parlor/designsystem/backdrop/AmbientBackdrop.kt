package com.parlor.designsystem.backdrop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.math.min

/**
 * The cozy-noir ambient backdrop per docs/DESIGN_TOKENS.md §8.
 *
 * Layers (bottom-up):
 *  1. `surfaceCanvas` (warm near-black base)
 *  2. A radial ember bloom offset toward (40%, 60%) of the viewport
 *  3. Subtle pulsing on the bloom (candle flicker) — disabled by reduced-motion
 *
 * Vignette is added by [HeroBackdrop] for hero screens.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    bloomIntensity: Float = 0.10f,
    content: @Composable () -> Unit = {},
) {
    val colors = ParlorTheme.colors
    val motion = ParlorTheme.motion
    val reduceMotion = ParlorTheme.reducedMotion

    val flicker: Float = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "candle-flicker")
        val pulse by transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.durationEmberCycle, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flicker",
        )
        pulse
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceCanvas),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) * 0.80f
            val center = Offset(size.width * 0.40f, size.height * 0.60f)
            val effectiveAlpha = (bloomIntensity * flicker).coerceIn(0f, 1f)

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentEmber.copy(alpha = effectiveAlpha),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
            )
        }
        content()
    }
}

/**
 * Hero variant — adds a vignette inset from edges. Used on the Home tile,
 * reveal stage, and other dramatic frames.
 */
@Composable
fun HeroBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    AmbientBackdrop(modifier = modifier, bloomIntensity = 0.14f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.45f),
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = min(size.width, size.height) * 0.85f,
                ),
            )
        }
        content()
    }
}

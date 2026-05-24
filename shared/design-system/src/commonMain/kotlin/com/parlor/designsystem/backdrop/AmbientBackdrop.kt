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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.math.min
import kotlin.random.Random

/**
 * The cozy-noir ambient backdrop. Composes (bottom-up):
 *
 *  1. `surfaceCanvas` — warm near-black base.
 *  2. **Halation** — a very wide, very soft warm glow that spreads the
 *     ember through the lower-left quadrant. Doesn't move; gives the
 *     image a "filmed at night" overexposed-highlight feel.
 *  3. **Ember bloom** — a smaller, brighter radial gradient with a slow
 *     candle flicker (skipped when [reducedMotion] is true).
 *  4. **Paper grain** — a fine, static noise pattern with low alpha.
 *     The grain is pre-rasterized into an `ImageBitmap`-equivalent list of
 *     points per `size`, so flicker recomposes do *not* regenerate it.
 *     Subtle but it's the difference between "gradient" and "old film
 *     still."
 *  5. *(HeroBackdrop only)* **Two-tone vignette** — a faint brass inner
 *     ring + black outer ring. Old-photograph feel; pulls the eye to the
 *     centre where the hero content sits.
 *
 * All four atmospheric layers are honoured in light mode too — the colors
 * shift but the structure stays. Reduced-motion users get every layer
 * minus the flicker animation.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    bloomIntensity: Float = 0.10f,
    paperGrain: Boolean = true,
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
            initialValue = 0.92f,
            targetValue = 1.08f,
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
        // Halation — wide, soft, static. Drawn first so the brighter
        // ember sits on top of it. Slightly cooler hue than the ember
        // itself so the two layers read as separate light sources.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val halationRadius = min(size.width, size.height) * 1.4f
            val halationCenter = Offset(size.width * 0.40f, size.height * 0.55f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentEmberDeep.copy(alpha = 0.18f),
                        colors.accentEmberDeep.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = halationCenter,
                    radius = halationRadius,
                ),
            )
        }

        // Ember bloom — narrower, brighter, flickers in dark mode.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) * 0.80f
            val center = Offset(size.width * 0.40f, size.height * 0.60f)
            val effectiveAlpha = (bloomIntensity * flicker).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentEmber.copy(alpha = effectiveAlpha),
                        colors.accentEmberDeep.copy(alpha = effectiveAlpha * 0.4f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
            )
        }

        // Paper grain — static, fine, low-alpha noise. Lives in its own
        // Canvas so the flicker animation doesn't invalidate the noise
        // computation each frame.
        if (paperGrain) {
            PaperGrain(
                tint = if (colors.surfaceCanvas.luminance() > 0.5f) {
                    // On light surfaces use a slightly darker grain so it reads.
                    Color.Black.copy(alpha = 0.04f)
                } else {
                    colors.accentParchment.copy(alpha = 0.025f)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        content()
    }
}

/**
 * Hero variant. Two-tone vignette added on top of the standard ambient
 * layers, and a slightly higher bloom intensity so hero screens earn
 * their name.
 */
@Composable
fun HeroBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val colors = ParlorTheme.colors
    AmbientBackdrop(
        modifier = modifier,
        bloomIntensity = 0.16f,
        paperGrain = true,
    ) {
        // Two-tone vignette: a brass inner ring darkens the deep edges to
        // black. The inner ring's tint adds an "old photograph" warmth.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = min(size.width, size.height)
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            // Inner warm halo at the rim of the safe area
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.accentBrass.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxR * 0.7f,
                ),
            )
            // Outer dark vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.55f),
                    ),
                    center = center,
                    radius = maxR * 0.95f,
                ),
            )
        }
        content()
    }
}

/**
 * Static paper-grain layer. Generates a seeded list of noise points and
 * draws them at low alpha so the surface picks up a film-still texture
 * without being dominated by the noise itself.
 *
 * Cached per [Size] via [remember] — the expensive part is the seeded
 * point list, not the per-frame draw.
 */
@Composable
private fun PaperGrain(
    tint: Color,
    modifier: Modifier = Modifier,
    density: Float = 0.0006f,   // points per pixel² — tuned to read as texture, not snow
    seed: Long = 0xC0FFEE_C0FFEEL,
) {
    Canvas(modifier = modifier) {
        val points = grainPoints(size, density, seed)
        for (p in points) {
            drawCircle(
                color = tint.copy(alpha = tint.alpha * p.alpha),
                radius = p.radius,
                center = Offset(p.x, p.y),
            )
        }
    }
}

private data class GrainPoint(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/**
 * Cached cheap-PRNG-driven noise. The cache is keyed by size + seed so
 * resizing the window recomputes; flicker recomposes do not.
 */
private val grainCache = mutableMapOf<GrainKey, List<GrainPoint>>()

private data class GrainKey(val w: Int, val h: Int, val seed: Long, val densityBp: Int)

private fun grainPoints(size: Size, density: Float, seed: Long): List<GrainPoint> {
    val w = size.width.toInt()
    val h = size.height.toInt()
    if (w <= 0 || h <= 0) return emptyList()
    val densityBp = (density * 1_000_000f).toInt()
    val key = GrainKey(w, h, seed, densityBp)
    grainCache[key]?.let { return it }
    val rng = Random(seed xor ((w.toLong() shl 32) or h.toLong()))
    val area = w.toLong() * h.toLong()
    val count = (area * density).toInt().coerceIn(64, 12_000)
    val pts = ArrayList<GrainPoint>(count)
    repeat(count) {
        pts.add(
            GrainPoint(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                radius = 0.35f + rng.nextFloat() * 0.45f,
                alpha = 0.35f + rng.nextFloat() * 0.65f,
            ),
        )
    }
    // Bound cache so resizing doesn't leak memory unboundedly.
    if (grainCache.size > 8) grainCache.clear()
    grainCache[key] = pts
    return pts
}

private fun DrawScope.unused() = Unit  // silences unused-import warnings during refactor

private fun Color.luminance(): Float {
    fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).let { it * it }
    return 0.2126f * lin(red) + 0.7152f * lin(green) + 0.0722f * lin(blue)
}

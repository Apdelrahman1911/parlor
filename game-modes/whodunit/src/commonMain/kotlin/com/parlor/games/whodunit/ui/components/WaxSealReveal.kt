package com.parlor.games.whodunit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_gate_a11y
import com.parlor.games.whodunit.resources.reveal_gate_reduce_motion_hint
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

/**
 * The signature Whodunit interaction. Press-and-hold a wax-seal stamp for
 * `holdMs` ms. The seal:
 *  - pulses with a warm ember glow under the press;
 *  - draws a progress arc around the rim showing how much hold remains;
 *  - on release (early) springs back to rest;
 *  - on completion *crackles* — a quick pop scale + an ember flare — then
 *    invokes [onRevealed].
 *
 * Composed in concentric layers (outer → inner):
 *  1. Halation glow that grows with progress.
 *  2. Progress arc — a brass ring that fills clockwise from 12 o'clock.
 *  3. Spread of melted wax around the seal (irregular soft edge).
 *  4. Wax body — radial gradient ember→deep so the seal looks domed.
 *  5. Rim notches — twelve tiny "wax stamp" ticks pressed into the rim.
 *  6. Embossed motif — a "P" monogram drawn with light + shadow strokes.
 *  7. Specular highlight — top-left ellipse that reads as candlelight
 *     catching the curve.
 *  8. Crack overlay (only at completion) — a hairline split across the
 *     seal that fades in for ~200 ms before [onRevealed] fires.
 *
 * Reduced-motion path: render the seal at rest and treat any tap as a
 * full reveal — no hold gesture, no animation.
 */
@Composable
fun WaxSealReveal(
    label: String,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMs: Long = 1500L,
) {
    val colors = ParlorTheme.colors
    val reduced = ParlorTheme.reducedMotion
    val a11y = stringResource(Res.string.reveal_gate_a11y)
    val reduceMotionHint = stringResource(Res.string.reveal_gate_reduce_motion_hint)

    var pressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var cracked by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = when {
            cracked -> 1.18f
            pressing -> 1.06f
            else -> 1f
        },
        animationSpec = if (cracked) {
            spring(dampingRatio = 0.4f, stiffness = 700f)
        } else {
            tween(durationMillis = 240)
        },
        label = "wax-seal-press-scale",
    )
    val crackProgress by animateFloatAsState(
        targetValue = if (cracked) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "wax-seal-crack",
    )

    LaunchedEffect(pressing) {
        if (!pressing) {
            if (!cracked) progress = 0f
            return@LaunchedEffect
        }
        val tickMs = 16L
        val total = holdMs
        var elapsed = 0L
        while (pressing && elapsed < total) {
            delay(tickMs)
            elapsed += tickMs
            progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
        }
        if (pressing && elapsed >= total) {
            cracked = true
            delay(220L)
            onRevealed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .scale(pressScale)
                .pointerInput(reduced) {
                    detectTapGestures(
                        onPress = {
                            if (reduced) return@detectTapGestures
                            pressing = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressing = false
                            }
                        },
                        onTap = {
                            if (reduced) onRevealed()
                        },
                    )
                }
                .semantics { contentDescription = a11y },
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val full = size.minDimension
                val center = Offset(size.width / 2f, size.height / 2f)
                val sealR = full * 0.38f
                val spreadR = sealR * 1.14f

                // 1. Halation — grows with progress.
                val halationAlpha = 0.18f + 0.42f * progress
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accentEmberGlow.copy(alpha = halationAlpha),
                            colors.accentEmber.copy(alpha = halationAlpha * 0.4f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = full * 0.50f,
                    ),
                    center = center,
                    radius = full * 0.50f,
                )

                // 3. Spread of melted wax — softer, larger, slightly cooler.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accentEmberDeep.copy(alpha = 0.95f),
                            colors.accentEmberDeep.copy(alpha = 0.55f),
                            colors.accentEmberDeep.copy(alpha = 0.0f),
                        ),
                        center = center,
                        radius = spreadR,
                    ),
                    center = center,
                    radius = spreadR,
                )

                // 4. Wax body — domed via three-stop radial.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accentEmberGlow,
                            colors.accentEmber,
                            colors.accentEmberDeep,
                        ),
                        center = Offset(center.x - sealR * 0.15f, center.y - sealR * 0.20f),
                        radius = sealR * 1.4f,
                    ),
                    center = center,
                    radius = sealR,
                )

                // 5. Rim notches — 12 tiny indents around the perimeter.
                val notchCount = 12
                val notchInner = sealR * 0.93f
                val notchOuter = sealR * 1.01f
                for (i in 0 until notchCount) {
                    val angle = (i.toFloat() / notchCount) * 2f * kotlin.math.PI.toFloat()
                    val cosA = cos(angle)
                    val sinA = sin(angle)
                    drawLine(
                        color = colors.accentEmberDeep.copy(alpha = 0.6f),
                        start = Offset(center.x + cosA * notchInner, center.y + sinA * notchInner),
                        end = Offset(center.x + cosA * notchOuter, center.y + sinA * notchOuter),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 6. Embossed "P" monogram.
                val monogramThickness = sealR * 0.13f
                val monogramH = sealR * 0.95f
                // Vertical stem of the P
                drawLine(
                    color = colors.accentEmberDeep.copy(alpha = 0.55f),
                    start = Offset(center.x - sealR * 0.28f, center.y - monogramH / 2f),
                    end = Offset(center.x - sealR * 0.28f, center.y + monogramH / 2f),
                    strokeWidth = monogramThickness,
                    cap = StrokeCap.Round,
                )
                // Bowl of the P (upper half-loop)
                drawArc(
                    color = colors.accentEmberDeep.copy(alpha = 0.55f),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - sealR * 0.28f,
                        center.y - monogramH / 2f,
                    ),
                    size = androidx.compose.ui.geometry.Size(sealR * 0.62f, sealR * 0.62f),
                    style = Stroke(width = monogramThickness, cap = StrokeCap.Round),
                )
                // Highlight echo of the monogram — offset up-left, lighter.
                drawLine(
                    color = colors.accentEmberGlow.copy(alpha = 0.30f),
                    start = Offset(center.x - sealR * 0.28f - 1.5.dp.toPx(), center.y - monogramH / 2f - 1.5.dp.toPx()),
                    end = Offset(center.x - sealR * 0.28f - 1.5.dp.toPx(), center.y + monogramH / 2f - 1.5.dp.toPx()),
                    strokeWidth = monogramThickness * 0.4f,
                    cap = StrokeCap.Round,
                )

                // 7. Specular highlight — small bright ellipse top-left.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accentParchment.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        center = Offset(center.x - sealR * 0.40f, center.y - sealR * 0.40f),
                        radius = sealR * 0.32f,
                    ),
                    center = Offset(center.x - sealR * 0.40f, center.y - sealR * 0.40f),
                    radius = sealR * 0.32f,
                )

                // 2. Progress arc around the rim — drawn last so it sits
                //    above the wax body. From 12 o'clock clockwise.
                if (progress > 0f) {
                    val ringR = sealR * 1.18f
                    drawArc(
                        color = colors.accentBrass.copy(alpha = 0.85f),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - ringR, center.y - ringR),
                        size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // 8. Crack overlay — hairline split that fades in on completion.
                if (crackProgress > 0f) {
                    val crackAlpha = 0.85f * crackProgress
                    // Main diagonal crack
                    drawLine(
                        color = Color.Black.copy(alpha = crackAlpha),
                        start = Offset(center.x - sealR * 0.7f, center.y - sealR * 0.3f),
                        end = Offset(center.x + sealR * 0.6f, center.y + sealR * 0.5f),
                        strokeWidth = 1.6.dp.toPx(),
                    )
                    // Smaller branch crack
                    drawLine(
                        color = Color.Black.copy(alpha = crackAlpha * 0.7f),
                        start = Offset(center.x + sealR * 0.1f, center.y + sealR * 0.1f),
                        end = Offset(center.x + sealR * 0.55f, center.y - sealR * 0.4f),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                    // Inner ember leak from the crack
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accentEmberGlow.copy(alpha = 0.6f * crackProgress),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = sealR * 0.6f,
                        ),
                        center = center,
                        radius = sealR * 0.6f,
                    )
                }
            }
        }

        Text(
            text = label,
            style = ParlorTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (reduced) {
            Text(
                text = reduceMotionHint,
                style = ParlorTheme.typography.labelSmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

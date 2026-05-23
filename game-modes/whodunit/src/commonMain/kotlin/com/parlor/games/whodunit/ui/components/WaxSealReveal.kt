package com.parlor.games.whodunit.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import org.jetbrains.compose.resources.stringResource

/**
 * The signature Whodunit interaction. Press-and-hold a wax-seal icon for 1.5 s.
 * The seal pulses with a warm ember glow during the hold; on completion it
 * breaks and the dossier is revealed. A tap fallback (single tap then explicit
 * confirmation) appears for motor accessibility or when `reducedMotion` is on.
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
    val animatedScale by animateFloatAsState(
        targetValue = if (pressing) 1.08f else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "wax-seal-press-scale",
    )

    LaunchedEffect(pressing) {
        if (!pressing) {
            progress = 0f
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
            onRevealed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .scale(animatedScale)
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
            Canvas(modifier = Modifier.fillMaxWidth().size(160.dp)) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accentEmberGlow.copy(alpha = 0.20f + 0.30f * progress),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius * 1.4f,
                    ),
                    radius = radius * 1.4f,
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.accentEmber, colors.accentEmberDeep),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )

                drawCircle(
                    color = colors.accentEmberGlow.copy(alpha = 0.45f),
                    radius = radius * 0.45f,
                    center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                )
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

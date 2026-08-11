package com.parlor.games.whodunit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_gate_a11y
import com.parlor.games.whodunit.resources.reveal_gate_reduce_motion_hint
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The press-and-hold reveal gate, editorial direction.
 *
 * Anatomy is intentionally minimal:
 *  - A coral circle that gently scales up while pressed.
 *  - A hairline progress ring around it that fills clockwise from 12
 *    o'clock as the hold completes.
 *  - A label below.
 *
 * No textures, no gradients, no wax-seal metaphor. The hold mechanic
 * is preserved so accessibility and the existing flow keep working.
 * Reduced-motion users see the dot at rest and any tap reveals.
 */
@Composable
fun WaxSealReveal(
    label: String,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMs: Long = 1500L,
) {
    require(holdMs > 0L) { "holdMs must be positive" }
    val colors = ParlorTheme.colors
    val reduced = ParlorTheme.reducedMotion
    val motion = ParlorTheme.motion
    val a11y = stringResource(Res.string.reveal_gate_a11y)
    val reduceMotionHint = stringResource(Res.string.reveal_gate_reduce_motion_hint)

    var pressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    val completionGate = remember { RevealCompletionGate() }
    val currentOnRevealed by rememberUpdatedState(onRevealed)

    val pressScale by animateFloatAsState(
        targetValue = when {
            reduced -> 1f
            completed -> 1.10f
            pressing -> 1.04f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = motion.durationFast,
            easing = motion.easingStandard,
        ),
        label = "reveal-press-scale",
    )

    LaunchedEffect(pressing) {
        if (!pressing) {
            if (!completed) progress = 0f
            return@LaunchedEffect
        }
        val tickMs = 16L
        var elapsed = 0L
        while (pressing && elapsed < holdMs) {
            delay(tickMs)
            elapsed += tickMs
            progress = (elapsed.toFloat() / holdMs).coerceIn(0f, 1f)
        }
        if (pressing && elapsed >= holdMs) {
            if (completionGate.tryComplete()) completed = true
        }
    }

    // This effect is keyed to completion rather than `pressing`. Releasing the
    // pointer immediately after the ring fills must not cancel the delayed
    // callback and strand the ceremony in a completed-but-unadvanced state.
    LaunchedEffect(completed) {
        if (!completed) return@LaunchedEffect
        if (!reduced) delay(motion.durationFast.toLong())
        currentOnRevealed()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
    ) {
        val ringDiameter = ParlorTheme.iconSize.hero * 2.6f
        val dotDiameter = ParlorTheme.iconSize.hero * 1.6f
        val ringStrokeDp = ParlorTheme.borders.strong
        val trackColor = colors.borderSubtle
        val ringColor = colors.accentEmber
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ringDiameter)
                .pointerInput(reduced, completed, holdMs) {
                    detectTapGestures(
                        onPress = {
                            if (reduced || completed) return@detectTapGestures
                            pressing = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressing = false
                            }
                        },
                        onTap = {
                            if (reduced && completionGate.tryComplete()) completed = true
                        },
                    )
                }
                .semantics {
                    contentDescription = a11y
                    role = Role.Button
                    onClick(label = a11y) {
                        if (completed || !completionGate.tryComplete()) {
                            false
                        } else {
                            completed = true
                            true
                        }
                    }
                },
        ) {
            // Progress ring — hairline circular stroke that fills clockwise.
            Canvas(modifier = Modifier.size(ringDiameter)) {
                val stroke = ringStrokeDp.toPx()
                val inset = stroke / 2f
                val topLeft = Offset(inset, inset)
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                )
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
            }

            // Coral dot in the centre — scales on press, pops on complete.
            Box(
                modifier = Modifier
                    .size(dotDiameter)
                    .scale(pressScale)
                    .clip(CircleShape)
                    .background(ringColor),
            )
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

/** One-shot contract shared by hold and reduced-motion tap completion paths. */
internal class RevealCompletionGate {
    private var completed: Boolean = false

    fun tryComplete(): Boolean {
        if (completed) return false
        completed = true
        return true
    }
}

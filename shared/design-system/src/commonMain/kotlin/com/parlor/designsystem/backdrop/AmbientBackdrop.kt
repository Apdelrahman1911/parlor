package com.parlor.designsystem.backdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.math.max

/**
 * Editorial backdrop. A static, low-alpha stage light gives the selected
 * game's accent a presence without adding texture, animation, or visual noise.
 *
 * The composable is retained as a screen-level wrapper so every destination
 * receives the complete edge-to-edge viewport. Floating controls that must
 * avoid system chrome apply their own focused insets.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceCanvas)
            .drawWithCache {
                val stageLight = Brush.radialGradient(
                    colors = listOf(
                        colors.accentEmber.copy(alpha = 0.08f),
                        colors.transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = max(size.width, size.height) * 0.78f,
                )
                onDrawBehind { drawRect(stageLight) }
            },
    ) {
        content()
    }
}

/**
 * Hero variant. Kept as a named entry point because callers use it to mark
 * high-attention screens; the same quiet stage light preserves continuity.
 */
@Composable
fun HeroBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    AmbientBackdrop(modifier = modifier) { content() }
}

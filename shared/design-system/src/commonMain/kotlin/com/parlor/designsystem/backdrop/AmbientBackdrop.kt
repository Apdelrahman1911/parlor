package com.parlor.designsystem.backdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Editorial backdrop. Flat canvas. No textures, no halation, no
 * vignette, no candle flicker. Type and content carry the visual
 * weight; the backdrop's job is to stay out of the way.
 *
 * The composable is retained as a screen-level wrapper so callers can apply
 * the same edge-to-edge surface and system-bar insets consistently.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.surfaceCanvas),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            content()
        }
    }
}

/**
 * Hero variant. Same flat canvas as [AmbientBackdrop] for now — hero
 * emphasis comes from the hero card / typography, not the backdrop.
 */
@Composable
fun HeroBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    AmbientBackdrop(modifier = modifier) { content() }
}

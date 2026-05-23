package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A full-screen dimming layer. Used as the base for cover screens, the
 * pause overlay, and the privacy concern dialog backdrop. Always paired with
 * a clear visual transition to avoid surprising the eye.
 */
@Composable
fun ParlorScrim(
    alpha: Float = 0.75f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))),
        content = { content() },
    )
}

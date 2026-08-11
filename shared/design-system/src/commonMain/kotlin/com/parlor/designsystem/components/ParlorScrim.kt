package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Full-screen dimming layer. Used as the base for cover screens, the
 * pause overlay, and the privacy concern dialog backdrop. Reads the
 * `overlayScrim` token so light and dark modes both get appropriate
 * dimming alpha.
 *
 * The palette owns the dimming alpha so every overlay remains consistent in
 * both light and dark themes.
 */
@Composable
fun ParlorScrim(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.overlayScrim),
        content = { content() },
    )
}

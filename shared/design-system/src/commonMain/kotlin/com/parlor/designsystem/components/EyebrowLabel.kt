package com.parlor.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Small uppercase label used as a section eyebrow above the screen title:
 * "TONIGHT'S STORY", "STORY LIBRARY", "HOSTING", "JOIN A GAME", etc.
 *
 * Lives in the design system rather than per-screen so the typographic
 * treatment (size, letter-spacing, ember accent) stays consistent and any
 * future polish — small caps font, micro-icon, color tweak — happens in
 * one place.
 */
@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
) {
    Text(
        text = text.uppercase(),
        style = ParlorTheme.typography.labelSmall,
        color = if (accent) ParlorTheme.colors.accentEmber else ParlorTheme.colors.textSecondary,
        modifier = modifier,
    )
}

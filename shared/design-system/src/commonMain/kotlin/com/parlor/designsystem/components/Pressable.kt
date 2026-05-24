package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Surface-tier press feedback. The card's fill dims one surface tier
 * while the user holds (surfaceElevated → surfaceCanvas on dark mode,
 * surfaceElevated → surfaceInset on light). No scale, no glow, no
 * border colour change — just a quiet surface shift that reads as
 * "I felt the tap" without a flash.
 *
 * Pairs with a [clickable] for the actual tap handler. The caller
 * supplies the corner radius so the clip matches the surrounding
 * card / row chrome.
 *
 * Usage:
 * ```
 * Box(
 *     modifier = Modifier
 *         .clip(RoundedCornerShape(radii.card))
 *         .pressableSurface(onClick = ..., cornerRadius = radii.card)
 *         .padding(...)
 * ) { content }
 * ```
 */
@Composable
fun Modifier.pressableSurface(
    onClick: () -> Unit,
    cornerRadius: Dp = ParlorTheme.radii.card,
    restingColor: androidx.compose.ui.graphics.Color = ParlorTheme.colors.surfaceElevated,
    pressedColor: androidx.compose.ui.graphics.Color = pressedSurfaceTier(),
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val fill = if (isPressed) pressedColor else restingColor
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(fill)
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
}

@Composable
private fun pressedSurfaceTier(): androidx.compose.ui.graphics.Color {
    val colors = ParlorTheme.colors
    // On dark: drop one tier toward canvas. On light: drop toward
    // surfaceInset which is a touch deeper than surfaceCanvas.
    return if (colors.surfaceCanvas.luminance() > 0.5f) {
        colors.surfaceInset
    } else {
        colors.surfaceCanvas
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

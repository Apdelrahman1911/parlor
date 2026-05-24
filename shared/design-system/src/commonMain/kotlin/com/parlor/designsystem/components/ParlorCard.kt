@file:Suppress("LongParameterList")

package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.ElevationSpec

/**
 * Card primitive — warm-shadow body, brass rim highlight on hero, optional
 * inner texture. Every game module's surfaces should compose this rather
 * than M3 `Card` directly, so the cozy-noir treatment stays consistent.
 *
 * `hero = true` opts in to:
 *  - `surfaceHero` fill + `borderAccent` rim
 *  - a 1px brass-tinted highlight along the top edge (read as inner glow
 *    catching the candlelight)
 *  - a subtle inset darkening at the bottom edge (read as the card's
 *    shadow on the table it sits on)
 *
 * `bordered = false` removes the rim entirely — useful when a card sits
 * inside another card (avoids double-bordering).
 */
@Composable
fun ParlorCard(
    modifier: Modifier = Modifier,
    elevation: ElevationSpec = ParlorTheme.elevation.medium,
    cornerRadius: Dp = ParlorTheme.radii.card,
    contentPadding: Dp = ParlorTheme.spacing.xl,
    hero: Boolean = false,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = ParlorTheme.colors
    val surfaceColor = if (hero) colors.surfaceHero else colors.surfaceElevated
    val borderColor = if (hero) colors.borderAccent else colors.borderElevated
    val shape = RoundedCornerShape(cornerRadius)

    val base = modifier
        .shadow(
            elevation = elevation.yOffset,
            shape = shape,
            clip = false,
        )
        .clip(shape)
        .background(surfaceColor)
        .drawBehind {
            // Inner top highlight — 1px brass line catching the
            // candlelight. Stronger on hero cards.
            val highlightAlpha = if (hero) 0.42f else 0.20f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.accentBrass.copy(alpha = highlightAlpha),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = 4.dp.toPx(),
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, 4.dp.toPx()),
            )
            // Inner bottom shadow — the card pressing into its surface.
            val shadowAlpha = if (hero) 0.38f else 0.22f
            val shadowDepth = 6.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = shadowAlpha),
                    ),
                    startY = size.height - shadowDepth,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - shadowDepth),
                size = Size(size.width, shadowDepth),
            )
        }

    val withBorder = if (bordered) {
        base.border(width = 1.dp, color = borderColor, shape = shape)
    } else {
        base
    }

    Box(modifier = withBorder) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

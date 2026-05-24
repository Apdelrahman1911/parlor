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
import androidx.compose.ui.unit.Dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.ElevationSpec

/**
 * Card primitive — flat editorial. Single surface fill, hairline border,
 * no top highlight, no inset shadow, no atmospheric decoration. Hierarchy
 * comes from `hero = true` (uses [com.parlor.designsystem.tokens.ParlorColors.surfaceHero] +
 * [com.parlor.designsystem.tokens.ParlorColors.borderAccent]) and from the
 * typography inside the card.
 *
 * `bordered = false` removes the rim. Useful when a card sits inside
 * another card.
 *
 * `elevation` is accepted for source compatibility but ignored — flat
 * editorial cards do not cast shadows.
 */
@Composable
fun ParlorCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") elevation: ElevationSpec = ParlorTheme.elevation.medium,
    cornerRadius: Dp = ParlorTheme.radii.card,
    contentPadding: Dp = ParlorTheme.spacing.xl,
    hero: Boolean = false,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = ParlorTheme.colors
    val surfaceColor = if (hero) colors.surfaceHero else colors.surfaceElevated
    val borderColor = if (hero) colors.borderAccent else colors.borderSubtle
    val shape = RoundedCornerShape(cornerRadius)

    val base = modifier
        .clip(shape)
        .background(surfaceColor)

    val withBorder = if (bordered) {
        base.border(width = ParlorTheme.borders.hairline, color = borderColor, shape = shape)
    } else {
        base
    }

    Box(modifier = withBorder) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@file:Suppress("LongParameterList")

package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.ElevationSpec

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
    val baseModifier = modifier
        .shadow(
            elevation = elevation.yOffset,
            shape = RoundedCornerShape(cornerRadius),
            clip = false,
        )
        .clip(RoundedCornerShape(cornerRadius))
        .background(surfaceColor)
    val finalModifier = if (bordered) {
        baseModifier.border(
            width = 1.dp,
            color = borderColor,
            shape = RoundedCornerShape(cornerRadius),
        )
    } else {
        baseModifier
    }
    Surface(
        color = surfaceColor,
        modifier = finalModifier,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(contentPadding),
            content = { content() },
        )
    }
}

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
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.ElevationSpec

/**
 * The default card primitive — warm-shadow, soft brass rim, generous padding.
 * Every game module's cards should compose this rather than M3 `Card` directly,
 * so the cozy-noir treatment is consistent.
 */
@Composable
fun ParlorCard(
    modifier: Modifier = Modifier,
    elevation: ElevationSpec = ParlorTheme.elevation.medium,
    cornerRadius: Dp = ParlorTheme.radii.card,
    contentPadding: Dp = ParlorTheme.spacing.xl,
    content: @Composable () -> Unit,
) {
    val colors = ParlorTheme.colors
    Surface(
        color = colors.surfaceElevated,
        modifier = modifier
            .shadow(
                elevation = elevation.yOffset,
                shape = RoundedCornerShape(cornerRadius),
                clip = false,
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.surfaceElevated)
            .border(
                width = androidx.compose.ui.unit.Dp.Hairline,
                color = colors.borderElevated,
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(contentPadding),
            content = { content() },
        )
    }
}

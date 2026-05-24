package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Compact "5 connected · 1 away" chip for the in-game top bar. Tapping
 * it opens the full [PartyRosterSheet] with per-member detail.
 *
 * The chip is intentionally low-key: dot + numbers, no avatars. Mobile
 * gameplay screens are vertical-space starved; a permanent panel would
 * crowd the canvas.
 */
@Composable
fun PartyRosterChip(
    connectedCount: Int,
    awayCount: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(colors.surfaceElevated)
            .border(
                width = ParlorTheme.borders.hairline,
                color = colors.borderSubtle,
                shape = RoundedCornerShape(ParlorTheme.radii.pill),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = ParlorTheme.spacing.m,
                vertical = ParlorTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        StatusDot(color = colors.semanticSuccess, modifier = Modifier)
        Text(
            text = label,
            style = ParlorTheme.typography.labelMedium,
            color = colors.textPrimary,
        )
        if (awayCount > 0) {
            StatusDot(color = colors.accentBrass, modifier = Modifier)
        }
    }
}

@Composable
private fun StatusDot(color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(ParlorTheme.spacing.s)
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(color),
    )
}

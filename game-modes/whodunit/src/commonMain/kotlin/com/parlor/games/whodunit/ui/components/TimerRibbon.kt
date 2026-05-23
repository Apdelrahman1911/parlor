package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.parlor.designsystem.theme.ParlorTheme

/**
 * The strip that shows discussion-timer state. Soft warning color when the
 * last 10 seconds are reached; subtle when plenty of time remains.
 */
@Composable
fun TimerRibbon(
    remainingSeconds: Int,
    totalSeconds: Int,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val urgent = remainingSeconds in 1..10
    val mm = remainingSeconds / 60
    val ss = (remainingSeconds % 60).toString().padStart(2, '0')

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.subtle))
            .background(if (urgent) colors.accentEmberDeep else colors.surfaceInset)
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.m),
    ) {
        Text(
            text = if (paused) "PAUSED" else "DISCUSSION",
            style = ParlorTheme.typography.labelSmall,
            color = if (urgent) colors.textPrimary else colors.textSecondary,
        )
        Text(
            text = "$mm:$ss",
            style = ParlorTheme.typography.timerMedium,
            color = if (urgent) colors.textPrimary else colors.textPrimary,
        )
        Text(
            text = "/ ${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}",
            style = ParlorTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
    }
}

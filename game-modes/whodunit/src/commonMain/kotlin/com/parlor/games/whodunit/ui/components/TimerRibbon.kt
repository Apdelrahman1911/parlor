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
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_discussion_paused_label
import com.parlor.games.whodunit.resources.round_discussion_timer_label
import org.jetbrains.compose.resources.stringResource

/**
 * Discussion-timer strip. Soft warning color when the last 10 seconds are
 * reached; subtle when plenty of time remains.
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
    val totalMm = totalSeconds / 60
    val totalSs = (totalSeconds % 60).toString().padStart(2, '0')

    val statusLabel = if (paused) {
        stringResource(Res.string.round_discussion_paused_label)
    } else {
        stringResource(Res.string.round_discussion_timer_label)
    }

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
            text = statusLabel,
            style = ParlorTheme.typography.labelSmall,
            color = if (urgent) colors.textPrimary else colors.textSecondary,
        )
        Text(
            text = "$mm:$ss",
            style = ParlorTheme.typography.timerMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "/ $totalMm:$totalSs",
            style = ParlorTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
    }
}

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_discussion_timer_description_format
import com.parlor.games.whodunit.resources.round_discussion_paused_label
import com.parlor.games.whodunit.resources.round_discussion_timer_label
import com.parlor.games.whodunit.resources.round_discussion_urgent_label
import com.parlor.games.whodunit.resources.timer_elapsed_format
import com.parlor.games.whodunit.resources.timer_total_format
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
    val urgent = !paused && remainingSeconds in 1..10
    val urgencyThreshold = !paused && remainingSeconds == 10
    val mm = remainingSeconds / 60
    val ss = (remainingSeconds % 60).toString().padStart(2, '0')
    val totalMm = totalSeconds / 60
    val totalSs = (totalSeconds % 60).toString().padStart(2, '0')

    val statusLabel = when {
        paused -> stringResource(Res.string.round_discussion_paused_label)
        urgent -> stringResource(Res.string.round_discussion_urgent_label)
        else -> stringResource(Res.string.round_discussion_timer_label)
    }
    val remainingTime = stringResource(Res.string.timer_elapsed_format, mm.toString(), ss)
    val totalTime = stringResource(Res.string.timer_elapsed_format, totalMm.toString(), totalSs)
    val visualTotalTime = stringResource(Res.string.timer_total_format, totalMm.toString(), totalSs)
    val timerDescription = stringResource(
        Res.string.round_discussion_timer_description_format,
        statusLabel,
        remainingTime,
        totalTime,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.subtle))
            .background(if (urgent) colors.accentEmber else colors.surfaceInset)
            .clearAndSetSemantics {
                // The visual label, current value, and total form one timer value;
                // presenting them as separate nodes makes it hard to follow.
                contentDescription = timerDescription
                if (urgencyThreshold) {
                    // Apply the live region only at the boundary. Leaving it
                    // active would announce every subsequent timer tick.
                    liveRegion = LiveRegionMode.Assertive
                }
            }
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.m),
    ) {
        Text(
            text = statusLabel,
            style = ParlorTheme.typography.labelSmall,
            color = if (urgent) colors.textOnAccent else colors.textSecondary,
        )
        Text(
            text = remainingTime,
            style = ParlorTheme.typography.timerMedium,
            color = if (urgent) colors.textOnAccent else colors.textPrimary,
        )
        Text(
            text = visualTotalTime,
            style = ParlorTheme.typography.bodyMedium,
            color = if (urgent) colors.textOnAccent else colors.textTertiary,
        )
    }
}

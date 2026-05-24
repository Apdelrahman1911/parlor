package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Strip a peer sees once they've acked but readiness is still
 * incomplete. Replaces the active button with a passive waiting state
 * so the peer can't double-ack and so they know whose ack the host is
 * waiting on (or whether they themselves are the holdup vs the host).
 *
 * The text comes pre-localized: callers pick "Waiting for host…" vs
 * "Waiting for N players" based on the readiness invariant.
 */
@Composable
fun WaitingForHostStrip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(colors.surfaceInset)
            .padding(
                horizontal = ParlorTheme.spacing.l,
                vertical = ParlorTheme.spacing.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(ParlorTheme.spacing.m),
            color = colors.accentEmber,
            strokeWidth = ParlorTheme.borders.regular,
        )
        Text(
            text = label,
            style = ParlorTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

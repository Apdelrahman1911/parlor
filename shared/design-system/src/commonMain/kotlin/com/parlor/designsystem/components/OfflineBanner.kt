package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Top-of-screen banner shown when the local device's transport reports
 * itself offline (peer bridge synthesises [com.parlor.networking.room.PeerEvent.SelfOffline]
 * when `sendToHost` returns NotConnected). Non-dismissable; auto-clears
 * when [com.parlor.networking.room.PeerEvent.SelfOnline] fires.
 *
 * Visual contract: full-width inset, brass accent (warning tone, not
 * danger — the offline state is recoverable), spinner on the start side
 * (flips to the right in RTL automatically because the Row uses
 * [Arrangement.spacedBy] without explicit alignment).
 */
@Composable
fun OfflineBanner(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accentBrass)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(
                horizontal = ParlorTheme.spacing.l,
                vertical = ParlorTheme.spacing.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        ParlorActivityIndicator(
            modifier = Modifier.size(ParlorTheme.spacing.m),
            color = colors.textOnAccent,
            trackColor = colors.textOnAccent.copy(alpha = OFFLINE_TRACK_ALPHA),
            strokeWidth = ParlorTheme.borders.regular,
        )
        Text(
            text = label,
            style = ParlorTheme.typography.labelMedium,
            color = colors.textOnAccent,
        )
    }
}

private const val OFFLINE_TRACK_ALPHA: Float = 0.35f

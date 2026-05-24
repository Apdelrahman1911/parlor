package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Full-screen overlay shown on a peer device when the bridge synthesises
 * [com.parlor.networking.room.PeerEvent.HostLost]. The current gameplay
 * UI stays mounted behind the overlay so reconnect lands cleanly without
 * recreating Compose state.
 *
 * The escape button is the user's only graceful exit when the host
 * cannot be reached. Layout never assumes LTR — children center on
 * both axes so RTL "just works".
 */
@Composable
fun ReconnectingOverlay(
    title: String,
    leaveLabel: String,
    leaveContentDescription: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.coverScreen)
            .padding(ParlorTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(ParlorTheme.spacing.xxl),
                color = colors.accentEmber,
                strokeWidth = ParlorTheme.borders.strong,
            )
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = colors.coverScreenTextPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = leaveLabel,
                contentDescription = leaveContentDescription,
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

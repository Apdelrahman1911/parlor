package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Host-facing recovery surface for a temporarily disconnected player.
 *
 * The caller replaces interactive gameplay with this surface while keeping
 * the process-owned session runtime alive. The host can wait for the rejoin
 * grace period or explicitly drop the seat through the game reducer; the
 * caller owns the confirmation step and lifecycle action.
 */
@Composable
fun HostDisconnectedOverlay(
    title: String,
    body: String,
    continueLabel: String,
    continueContentDescription: String,
    leaveLabel: String,
    leaveContentDescription: String,
    onContinue: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.coverScreen)
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Assertive
            }
            .parlorSafeContentPadding(ParlorTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = colors.coverScreenTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = colors.coverScreenTextSecondary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = continueLabel,
                contentDescription = continueContentDescription,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Primary,
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

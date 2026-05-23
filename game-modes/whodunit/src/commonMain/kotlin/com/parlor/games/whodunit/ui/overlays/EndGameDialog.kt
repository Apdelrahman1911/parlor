package com.parlor.games.whodunit.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorScrim
import com.parlor.designsystem.theme.ParlorTheme

/**
 * End-game dialog from the pause overlay. Two options per design doc §15:
 * reveal-now (plays the reveal sequence and ends) or end-without-reveal.
 */
@Composable
fun EndGameDialog(
    onRevealNow: () -> Unit,
    onEndQuietly: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ParlorScrim(alpha = 0.85f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.high,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xxl,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                    Text(
                        text = "End this game?",
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                    Text(
                        text = "The game cannot continue without all players. Choose how to close.",
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    ParlorButton(
                        label = "Reveal the Case Now",
                        contentDescription = "End the game and play the full reveal narrative.",
                        onClick = onRevealNow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParlorButton(
                        label = "End Without Reveal",
                        contentDescription = "End the game without revealing the killer.",
                        onClick = onEndQuietly,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParlorButton(
                        label = "Cancel",
                        contentDescription = "Return to pause without ending the game.",
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

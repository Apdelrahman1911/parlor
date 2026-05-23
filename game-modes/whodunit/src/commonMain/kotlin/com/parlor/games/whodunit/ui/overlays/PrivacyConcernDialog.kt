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
 * Privacy concern flow per design doc §15. Continue or reroll all roles.
 */
@Composable
fun PrivacyConcernDialog(
    onContinue: () -> Unit,
    onReroll: () -> Unit,
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
                        text = "Privacy concern",
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Did someone see a dossier they shouldn't have?",
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    ParlorButton(
                        label = "Continue Anyway",
                        contentDescription = "Continue the game with the existing roles.",
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParlorButton(
                        label = "Reroll All Roles",
                        contentDescription = "Reshuffle roles and restart character reveal. " +
                            "Anyone who saw their old role must forget it.",
                        onClick = onReroll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

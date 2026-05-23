package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.ModeId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds

/**
 * Mode Selection — the first screen of the Whodunit setup flow. Two large
 * cards, each readable from across a small table. The cinematic identity is
 * carried by the HeroBackdrop and the dramatic card elevation.
 */
@Composable
fun ModeSelectionScreen(
    onModeSelected: (ModeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "CHOOSE GAME MODE",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = "How do you want to play?",
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModeCard(
                    title = "Classic Vote",
                    body = "Investigate the full case. One vote at the end. " +
                        "Best for story and deduction.",
                    timeLine = "25–35 min",
                    playerLine = "4–6 players",
                    onClick = { onModeSelected(WhodunitIds.ClassicVoteModeId) },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    title = "Elimination",
                    body = "Vote after every round. Find the killer before " +
                        "they survive. Best for fast, tense games.",
                    timeLine = "15–25 min",
                    playerLine = "5–6 players",
                    onClick = { onModeSelected(WhodunitIds.EliminationModeId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    body: String,
    timeLine: String,
    playerLine: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParlorCard(
        modifier = modifier.fillMaxWidth(),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = "$timeLine  ·  $playerLine",
                style = ParlorTheme.typography.labelMedium,
                color = ParlorTheme.colors.accentEmber,
            )
            ParlorButton(
                label = "Choose $title",
                contentDescription = "Choose $title mode and proceed to player setup.",
                onClick = onClick,
            )
        }
    }
}

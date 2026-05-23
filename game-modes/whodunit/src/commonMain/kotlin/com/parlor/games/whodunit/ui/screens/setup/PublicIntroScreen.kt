package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Public intro — placed face-up at the table. Everyone reads together.
 * The prose comes from validated case content; the screen never composes it.
 */
@Composable
fun PublicIntroScreen(
    title: String,
    intro: String,
    bedrockClues: List<String>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = "READ ALOUD",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = title,
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = intro,
                style = ParlorTheme.typography.narration,
                color = ParlorTheme.colors.textNarration,
            )
            Text(
                text = "WHAT IS TRUE",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )
            bedrockClues.forEach { clue ->
                Text(
                    text = "·  $clue",
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
            }
            ParlorButton(
                label = "Continue",
                contentDescription = "Advance from the public intro to the rules briefing.",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

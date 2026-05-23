package com.parlor.games.whodunit.ui.screens.postgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Post-game menu — replay-with-new-killer, try-other-mode, back-to-library.
 * The first button is the largest, per design doc §17.
 */
@Composable
fun PostGameScreen(
    onReplaySameCase: () -> Unit,
    onTryOtherMode: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AFTER THE GAME",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = "What's next?",
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = "Play Again — New Killer",
                contentDescription = "Replay this case with a freshly randomized killer.",
                onClick = onReplaySameCase,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = "Try the Other Mode",
                contentDescription = "Switch between Classic Vote and Elimination.",
                onClick = onTryOtherMode,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = "Back to the Library",
                contentDescription = "Return to the games library.",
                onClick = onBackToLibrary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

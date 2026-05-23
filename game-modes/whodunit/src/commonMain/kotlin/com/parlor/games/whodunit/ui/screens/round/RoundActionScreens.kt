package com.parlor.games.whodunit.ui.screens.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme

/**
 * The four structured-action prompts. Each one is a single, minimal screen
 * that instructs the table how to proceed and offers a Done button to advance
 * the reducer. (The actual speaking/listening happens around the phone.)
 */
@Composable
fun AlibiRoundScreen(
    currentPlayerName: String,
    secondsPerPlayer: Int,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = "ALIBI ROUND",
        headline = "$currentPlayerName,",
        body = "tell us where you were when it happened. You have $secondsPerPlayer seconds.",
        buttonLabel = "Done — Next Player",
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
fun DirectedQuestionsScreen(
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = "QUESTIONS",
        headline = "Ask the table.",
        body = "Each player asks one direct question. The asked player must answer (and may lie).",
        buttonLabel = "Begin Discussion",
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
fun SilentAccusationScreen(
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = "SILENT ACCUSATION",
        headline = "Point.",
        body = "On the count of three, point at the player you suspect most. No talking.",
        buttonLabel = "Done",
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
fun MonologueScreen(
    currentPlayerName: String,
    secondsRemaining: Int,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = "FINAL MONOLOGUE",
        headline = "$currentPlayerName, your 30 seconds.",
        body = "Defend yourself. Make your case. Accuse someone. Time remaining: ${secondsRemaining}s.",
        buttonLabel = "Done — Next Speaker",
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
private fun StructuredPrompt(
    eyebrow: String,
    headline: String,
    body: String,
    buttonLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = eyebrow,
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = headline,
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = buttonLabel,
                contentDescription = buttonLabel,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

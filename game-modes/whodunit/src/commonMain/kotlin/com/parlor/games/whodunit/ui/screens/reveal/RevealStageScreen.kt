package com.parlor.games.whodunit.ui.screens.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict

/**
 * The dramatic climax. Shows the YES/NO verdict, then the reveal narrative.
 * Per design doc §12, the screen is paced — a single screen builds tension.
 */
@Composable
fun RevealStageScreen(
    verdict: Verdict,
    killerDisplayName: String,
    revealNarrative: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val (verdictLine, accentColor) = when (verdict) {
        is Verdict.PlayersWin -> "YES." to colors.semanticSuccess
        is Verdict.KillerWins -> "NO." to colors.semanticDanger
    }
    val subhead = when (verdict) {
        is Verdict.PlayersWin -> "The room has chosen correctly."
        is Verdict.KillerWins -> when (verdict.cause) {
            KillerWinCause.InnocentAccused -> "An innocent has been condemned."
            KillerWinCause.TieUnresolved -> "Indecision. The killer escapes."
            KillerWinCause.SurvivedToFinalTwo -> "The killer has survived to the final two."
        }
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "THE REVEAL",
                style = ParlorTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = verdictLine,
                style = ParlorTheme.typography.displayHero,
                color = accentColor,
            )
            Text(
                text = subhead,
                style = ParlorTheme.typography.displayMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.dramatic,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xxl,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                    Text(
                        text = "THE KILLER WAS",
                        style = ParlorTheme.typography.labelSmall,
                        color = colors.accentEmber,
                    )
                    Text(
                        text = killerDisplayName,
                        style = ParlorTheme.typography.displayLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(ParlorTheme.spacing.s))
                    Text(
                        text = revealNarrative,
                        style = ParlorTheme.typography.narration,
                        color = colors.textNarration,
                    )
                }
            }
            ParlorButton(
                label = "Continue",
                contentDescription = "Acknowledge the reveal and continue to post-game.",
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

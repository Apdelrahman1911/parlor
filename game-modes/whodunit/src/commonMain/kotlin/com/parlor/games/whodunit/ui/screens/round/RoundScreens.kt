package com.parlor.games.whodunit.ui.screens.round

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
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.ui.components.ClueCard
import com.parlor.games.whodunit.ui.components.TimerRibbon

/** Round title — short, theatrical card before any clue or discussion. */
@Composable
fun RoundTitleCardScreen(
    roundIndex: Int,
    title: String,
    tagline: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xxl),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ROUND $roundIndex",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                Text(
                    text = title,
                    style = ParlorTheme.typography.displayHero,
                    color = ParlorTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = tagline,
                    style = ParlorTheme.typography.narration,
                    color = ParlorTheme.colors.textNarration,
                    textAlign = TextAlign.Center,
                )
            }
            ParlorButton(
                label = "Reveal the Clue",
                contentDescription = "Reveal this round's clue.",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The clue-reveal screen for a given round. */
@Composable
fun ClueRevealScreen(
    clue: RevealedClue,
    onContinue: () -> Unit,
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
                text = "ROUND ${clue.roundIndex}  ·  CLUE",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            ClueCard(text = clue.text)
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = "Begin Discussion",
                contentDescription = "Start the discussion timer.",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Discussion timer screen — shows the timer ribbon and revealed clues. */
@Composable
fun DiscussionScreen(
    timer: PublicTimerState?,
    revealedClues: List<RevealedClue>,
    onAdvance: () -> Unit,
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
                text = "DISCUSSION",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            if (timer != null) {
                TimerRibbon(
                    remainingSeconds = timer.remainingSeconds,
                    totalSeconds = timer.totalSeconds,
                    paused = timer.paused,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = "What you know so far",
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            revealedClues.forEach { c ->
                Text(
                    text = "·  ${c.text}",
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = "Move On",
                contentDescription = "Advance from discussion to the next round or the vote.",
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

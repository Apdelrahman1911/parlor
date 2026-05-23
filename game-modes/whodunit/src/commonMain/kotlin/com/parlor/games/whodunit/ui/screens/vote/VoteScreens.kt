package com.parlor.games.whodunit.ui.screens.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.ui.components.CandlelitCover

/** Pass-and-tap vote: cover → voter taps a target → cover for next voter. */
@Composable
fun VoteBallotScreen(
    currentVoterName: String,
    candidates: List<Pair<PlayerId, String>>,
    onVote: (PlayerId) -> Unit,
    onAbstain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$currentVoterName, cast your vote.",
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap the name of the person you accuse.",
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(ParlorTheme.spacing.m))
            candidates.forEach { (id, name) ->
                CandidateRow(name = name, onClick = { onVote(id) })
            }
            Spacer(Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = "Abstain",
                contentDescription = "Decline to vote.",
                onClick = onAbstain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CandidateRow(name: String, onClick: () -> Unit) {
    val colors = ParlorTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderElevated, RoundedCornerShape(ParlorTheme.radii.card))
            .clickable(onClick = onClick)
            .padding(ParlorTheme.spacing.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = name,
            style = ParlorTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
    }
}

/** Cover between voters — "Pass to [next]". */
@Composable
fun VoteHandoffScreen(nextVoterName: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    CandidatePassCover(
        title = "Pass to $nextVoterName.",
        subtitle = "When you're alone, tap to vote.",
        onDismiss = onContinue,
        modifier = modifier,
    )
}

/** Tied-revote framing screen — debate then revote between the tied suspects. */
@Composable
fun TiedRevoteScreen(
    tiedNames: List<String>,
    debateSecondsRemaining: Int,
    onBeginRevote: () -> Unit,
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
                text = "TIED",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )
            Text(
                text = tiedNames.joinToString(separator = " · "),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "60 seconds. Each tied suspect defends themselves. Then revote.",
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${debateSecondsRemaining}s",
                style = ParlorTheme.typography.timerLarge,
                color = ParlorTheme.colors.accentEmber,
            )
            Spacer(Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = "Begin the Revote",
                contentDescription = "Open the revote between the tied suspects.",
                onClick = onBeginRevote,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CandidatePassCover(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CandlelitCover(title = title, subtitle = subtitle, onDismiss = onDismiss, modifier = modifier)
}

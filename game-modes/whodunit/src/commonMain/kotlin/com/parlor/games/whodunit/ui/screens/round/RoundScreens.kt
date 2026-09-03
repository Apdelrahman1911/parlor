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
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_begin_discussion
import com.parlor.games.whodunit.resources.round_begin_discussion_description
import com.parlor.games.whodunit.resources.round_clue_bullet_format
import com.parlor.games.whodunit.resources.round_clue_eyebrow_format
import com.parlor.games.whodunit.resources.round_discussion_eyebrow
import com.parlor.games.whodunit.resources.round_eyebrow_format
import com.parlor.games.whodunit.resources.round_known_so_far
import com.parlor.games.whodunit.resources.round_move_on
import com.parlor.games.whodunit.resources.round_move_on_description
import com.parlor.games.whodunit.resources.round_reveal_clue_button
import com.parlor.games.whodunit.resources.round_reveal_clue_description
import com.parlor.games.whodunit.ui.components.ClueCard
import com.parlor.games.whodunit.ui.components.TimerRibbon
import org.jetbrains.compose.resources.stringResource

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
                .padding(ParlorTheme.spacing.xxl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.round_eyebrow_format, roundIndex),
                accent = false,
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
                label = stringResource(Res.string.round_reveal_clue_button),
                contentDescription = stringResource(Res.string.round_reveal_clue_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.round_clue_eyebrow_format, clue.roundIndex),
                accent = false,
            )
            ClueCard(text = clue.text)
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = stringResource(Res.string.round_begin_discussion),
                contentDescription = stringResource(Res.string.round_begin_discussion_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
            EyebrowLabel(
                text = stringResource(Res.string.round_discussion_eyebrow),
                accent = false,
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
                text = stringResource(Res.string.round_known_so_far),
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            revealedClues.forEach { c ->
                Text(
                    text = stringResource(Res.string.round_clue_bullet_format, c.text),
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = stringResource(Res.string.round_move_on),
                contentDescription = stringResource(Res.string.round_move_on_description),
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_stage_continue
import com.parlor.games.whodunit.resources.reveal_stage_continue_description
import com.parlor.games.whodunit.resources.reveal_stage_eyebrow
import com.parlor.games.whodunit.resources.reveal_stage_killer_finaltwo_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_innocent_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_tie_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_was_label
import com.parlor.games.whodunit.resources.reveal_stage_no
import com.parlor.games.whodunit.resources.reveal_stage_players_win_subhead
import com.parlor.games.whodunit.resources.reveal_stage_yes
import org.jetbrains.compose.resources.stringResource

@Composable
fun RevealStageScreen(
    verdict: Verdict,
    killerDisplayName: String,
    revealNarrative: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val verdictLine = when (verdict) {
        is Verdict.PlayersWin -> stringResource(Res.string.reveal_stage_yes)
        is Verdict.KillerWins -> stringResource(Res.string.reveal_stage_no)
    }
    val accentColor = when (verdict) {
        is Verdict.PlayersWin -> colors.semanticSuccess
        is Verdict.KillerWins -> colors.semanticDanger
    }
    val subhead = when (verdict) {
        is Verdict.PlayersWin -> stringResource(Res.string.reveal_stage_players_win_subhead)
        is Verdict.KillerWins -> when (verdict.cause) {
            KillerWinCause.InnocentAccused ->
                stringResource(Res.string.reveal_stage_killer_innocent_subhead)
            KillerWinCause.TieUnresolved ->
                stringResource(Res.string.reveal_stage_killer_tie_subhead)
            KillerWinCause.SurvivedToFinalTwo ->
                stringResource(Res.string.reveal_stage_killer_finaltwo_subhead)
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
                text = stringResource(Res.string.reveal_stage_eyebrow),
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
                        text = stringResource(Res.string.reveal_stage_killer_was_label),
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
                label = stringResource(Res.string.reveal_stage_continue),
                contentDescription = stringResource(Res.string.reveal_stage_continue_description),
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package com.parlor.games.whodunit.ui.screens.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.pressableSurface
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_handoff_subtitle
import com.parlor.games.whodunit.resources.reveal_handoff_title_format
import com.parlor.games.whodunit.resources.vote_ballot_headline_format
import com.parlor.games.whodunit.resources.vote_ballot_instruction
import com.parlor.games.whodunit.resources.vote_ballot_refuse
import com.parlor.games.whodunit.resources.vote_ballot_refuse_description
import com.parlor.games.whodunit.resources.vote_tied_begin_revote
import com.parlor.games.whodunit.resources.vote_tied_begin_revote_description
import com.parlor.games.whodunit.resources.vote_tied_body
import com.parlor.games.whodunit.resources.vote_tied_eyebrow
import com.parlor.games.whodunit.ui.components.CandlelitCover
import org.jetbrains.compose.resources.stringResource

/**
 * Vote ballot. [onRefuse] is Phase 6.3's *refuse-to-vote* affordance — tally-
 * equivalent to abstain but UI-distinct so players who actively decline to
 * participate aren't conflated with players who simply have no opinion.
 */
@Composable
fun VoteBallotScreen(
    currentVoterName: String,
    candidates: List<Pair<PlayerId, String>>,
    onVote: (PlayerId) -> Unit,
    onRefuse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.vote_ballot_headline_format, currentVoterName),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.vote_ballot_instruction),
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
                label = stringResource(Res.string.vote_ballot_refuse),
                contentDescription = stringResource(Res.string.vote_ballot_refuse_description),
                onClick = onRefuse,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
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
            .pressableSurface(onClick = onClick, cornerRadius = ParlorTheme.radii.card)
            .border(
                width = ParlorTheme.borders.hairline,
                color = colors.borderElevated,
                shape = RoundedCornerShape(ParlorTheme.radii.card),
            )
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

@Composable
fun VoteHandoffScreen(
    nextVoterName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CandlelitCover(
        title = stringResource(Res.string.reveal_handoff_title_format, nextVoterName),
        subtitle = stringResource(Res.string.reveal_handoff_subtitle),
        onDismiss = onContinue,
        modifier = modifier,
    )
}

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
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.vote_tied_eyebrow))
            Text(
                text = tiedNames.joinToString(separator = " · "),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.vote_tied_body),
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
                label = stringResource(Res.string.vote_tied_begin_revote),
                contentDescription = stringResource(Res.string.vote_tied_begin_revote_description),
                onClick = onBeginRevote,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

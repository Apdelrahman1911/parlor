package com.parlor.games.mafia.ui.screens.vote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.vote_abstain
import com.parlor.games.mafia.resources.vote_announcement_continue
import com.parlor.games.mafia.resources.vote_announcement_continue_description
import com.parlor.games.mafia.resources.vote_announcement_eyebrow_format
import com.parlor.games.mafia.resources.vote_announcement_no_elimination
import com.parlor.games.mafia.resources.vote_announcement_revealed_role_format
import com.parlor.games.mafia.resources.vote_announcement_tally
import com.parlor.games.mafia.resources.vote_eyebrow_voter_format
import com.parlor.games.mafia.resources.vote_headline
import com.parlor.games.mafia.resources.vote_instructions
import com.parlor.games.mafia.resources.vote_revote_eyebrow_voter_format
import com.parlor.games.mafia.resources.vote_submit
import com.parlor.games.mafia.ui.screens.night.PickableTarget
import com.parlor.games.mafia.ui.screens.night.TargetPickerScreen
import org.jetbrains.compose.resources.stringResource

/**
 * Vote-casting screen — used in both Pass-and-Play (cycled per player) and
 * Local/P2P (rendered to whichever peer hasn't voted yet). The vote target
 * is PUBLIC by spec; the per-player handoff in PaP is just for input, not
 * privacy.
 */
@Composable
fun VoteCastScreen(
    voterName: String,
    candidates: List<PickableTarget>,
    revoteRound: Int,
    onCast: (PlayerId) -> Unit,
    onAbstain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eyebrow = if (revoteRound > 0) {
        stringResource(Res.string.vote_revote_eyebrow_voter_format, revoteRound, voterName)
    } else {
        stringResource(Res.string.vote_eyebrow_voter_format, voterName)
    }
    TargetPickerScreen(
        eyebrow = eyebrow,
        headline = stringResource(Res.string.vote_headline),
        instructions = stringResource(Res.string.vote_instructions),
        targets = candidates,
        submitLabel = stringResource(Res.string.vote_submit),
        onSubmit = { selected ->
            if (selected != null) onCast(selected) else onAbstain()
        },
        allowSkip = true,
        skipLabel = stringResource(Res.string.vote_abstain),
        modifier = modifier,
    )
}

/**
 * Vote announcement — public reveal of the tally and the eliminated player.
 */
@Composable
fun VoteAnnouncementScreen(
    day: Int,
    tally: Map<PlayerId, Int>,
    nameLookup: (PlayerId) -> String,
    eliminatedName: String?,
    eliminatedRole: String?,
    outcomeLine: String,
    onAcknowledged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.vote_announcement_eyebrow_format, day),
                textAlign = TextAlign.Center,
            )
            Text(
                text = eliminatedName ?: stringResource(Res.string.vote_announcement_no_elimination),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (eliminatedRole != null) {
                Text(
                    text = stringResource(Res.string.vote_announcement_revealed_role_format, eliminatedRole),
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = outcomeLine,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                ) {
                    EyebrowLabel(text = stringResource(Res.string.vote_announcement_tally), accent = false)
                    tally.entries
                        .sortedByDescending { it.value }
                        .forEach { (id, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = nameLookup(id),
                                    style = ParlorTheme.typography.bodyLarge,
                                    color = ParlorTheme.colors.textPrimary,
                                )
                                Text(
                                    text = count.toString(),
                                    style = ParlorTheme.typography.bodyLarge,
                                    color = ParlorTheme.colors.textSecondary,
                                )
                            }
                        }
                }
            }

            ParlorButton(
                label = stringResource(Res.string.vote_announcement_continue),
                contentDescription = stringResource(Res.string.vote_announcement_continue_description),
                onClick = onAcknowledged,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

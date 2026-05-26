package com.parlor.games.mafia.ui.screens.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_mafia_eyebrow_format
import com.parlor.games.mafia.resources.night_mafia_headline
import com.parlor.games.mafia.resources.night_mafia_instructions
import com.parlor.games.mafia.resources.night_mafia_revote_instructions
import com.parlor.games.mafia.resources.night_mafia_round_one_tally
import com.parlor.games.mafia.resources.night_mafia_skip
import com.parlor.games.mafia.resources.night_mafia_submit
import org.jetbrains.compose.resources.stringResource

/**
 * Mafia member kill-vote target picker. On round 2, surfaces the anonymized
 * tally from round 1 so the Mafia can silently realign without chat.
 */
@Composable
fun MafiaKillVoteScreen(
    voterName: String,
    targets: List<PickableTarget>,
    coordinationRound: Int,
    previousRoundTally: Map<PlayerId, Int>?,
    targetNameLookup: (PlayerId) -> String,
    onSubmit: (PlayerId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val instructions = if (coordinationRound >= 2) {
        stringResource(Res.string.night_mafia_revote_instructions)
    } else {
        stringResource(Res.string.night_mafia_instructions)
    }
    TargetPickerScreen(
        eyebrow = stringResource(Res.string.night_mafia_eyebrow_format, voterName),
        headline = stringResource(Res.string.night_mafia_headline),
        instructions = instructions,
        targets = targets,
        submitLabel = stringResource(Res.string.night_mafia_submit),
        onSubmit = onSubmit,
        allowSkip = true,
        skipLabel = stringResource(Res.string.night_mafia_skip),
        modifier = modifier,
        footer = if (coordinationRound >= 2 && !previousRoundTally.isNullOrEmpty()) {
            { PreviousTallyCard(previousRoundTally, targetNameLookup) }
        } else {
            null
        },
    )
}

@Composable
private fun PreviousTallyCard(
    tally: Map<PlayerId, Int>,
    targetNameLookup: (PlayerId) -> String,
) {
    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
        ) {
            EyebrowLabel(text = stringResource(Res.string.night_mafia_round_one_tally), accent = false)
            tally.entries
                .sortedByDescending { it.value }
                .forEach { (id, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = targetNameLookup(id),
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
}

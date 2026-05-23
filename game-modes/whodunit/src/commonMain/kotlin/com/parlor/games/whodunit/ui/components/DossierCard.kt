package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.dossier_acting_tips_label
import com.parlor.games.whodunit.resources.dossier_alibi_label
import com.parlor.games.whodunit.resources.dossier_backstory_label
import com.parlor.games.whodunit.resources.dossier_done
import com.parlor.games.whodunit.resources.dossier_done_description
import com.parlor.games.whodunit.resources.dossier_feeling_label
import com.parlor.games.whodunit.resources.dossier_freely_label
import com.parlor.games.whodunit.resources.dossier_goal_label
import com.parlor.games.whodunit.resources.dossier_hide_optional
import com.parlor.games.whodunit.resources.dossier_hide_optional_description
import com.parlor.games.whodunit.resources.dossier_killer_goal_fallback
import com.parlor.games.whodunit.resources.dossier_killer_must_hide_fallback
import com.parlor.games.whodunit.resources.dossier_motive_label
import com.parlor.games.whodunit.resources.dossier_must_hide_label
import com.parlor.games.whodunit.resources.dossier_secret_label
import com.parlor.games.whodunit.resources.dossier_show_optional
import com.parlor.games.whodunit.resources.dossier_show_optional_description
import com.parlor.games.whodunit.resources.dossier_suggested_label
import com.parlor.games.whodunit.resources.dossier_that_night_label
import com.parlor.games.whodunit.resources.dossier_you_are_eyebrow
import org.jetbrains.compose.resources.stringResource

/**
 * Dossier card. Must Read + Optional Details. Renders text from validated case
 * content — never composes prose itself.
 */
@Composable
fun DossierCard(
    character: Character,
    role: PlayerRole,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val killerGoalFallback = stringResource(Res.string.dossier_killer_goal_fallback)
    val killerMustHideFallback = stringResource(Res.string.dossier_killer_must_hide_fallback)

    val brief = when (role) {
        PlayerRole.Innocent -> Brief(
            verdict = character.innocentBrief.verdictLine,
            alibi = character.innocentBrief.alibi,
            goal = character.innocentBrief.goal,
            canSay = character.innocentBrief.canSayFreely,
            mustHide = character.innocentBrief.mustHide,
            extra = null,
        )
        PlayerRole.Killer -> Brief(
            verdict = character.guiltyBrief.verdictLine,
            alibi = character.guiltyBrief.fakeAlibi,
            goal = killerGoalFallback,
            canSay = character.guiltyBrief.panicMove,
            mustHide = killerMustHideFallback,
            extra = character.guiltyBrief.actingTips,
        )
    }

    var showOptional by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val secretLabel = stringResource(Res.string.dossier_secret_label)
    val motiveLabel = stringResource(Res.string.dossier_motive_label)
    val alibiLabel = stringResource(Res.string.dossier_alibi_label)
    val goalLabel = stringResource(Res.string.dossier_goal_label)
    val freelyLabel = stringResource(Res.string.dossier_freely_label)
    val mustHideLabel = stringResource(Res.string.dossier_must_hide_label)
    val actingTipsLabel = stringResource(Res.string.dossier_acting_tips_label)

    ParlorCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            Text(
                text = stringResource(Res.string.dossier_you_are_eyebrow),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = character.displayName,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = character.publicIdentity,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))

            Text(
                text = brief.verdict,
                style = ParlorTheme.typography.displayLarge,
                color = when (role) {
                    PlayerRole.Innocent -> ParlorTheme.colors.semanticSuccess
                    PlayerRole.Killer -> ParlorTheme.colors.semanticDanger
                },
            )
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))

            LabeledLine(secretLabel, character.privateSecret)
            LabeledLine(motiveLabel, character.publicMotive)
            LabeledLine(alibiLabel, brief.alibi)
            LabeledLine(goalLabel, brief.goal)
            LabeledLine(freelyLabel, brief.canSay)
            LabeledLine(mustHideLabel, brief.mustHide)

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.m))

            if (character.optionalDetails != null) {
                val toggleLabel = if (showOptional) {
                    stringResource(Res.string.dossier_hide_optional)
                } else {
                    stringResource(Res.string.dossier_show_optional)
                }
                val toggleDescription = if (showOptional) {
                    stringResource(Res.string.dossier_hide_optional_description)
                } else {
                    stringResource(Res.string.dossier_show_optional_description)
                }
                ParlorButton(
                    label = toggleLabel,
                    onClick = { showOptional = !showOptional },
                    contentDescription = toggleDescription,
                )
                if (showOptional) {
                    OptionalDetailsBlock(character)
                }
            }

            if (brief.extra != null) {
                Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
                LabeledLine(actingTipsLabel, brief.extra)
            }

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = stringResource(Res.string.dossier_done),
                onClick = onDone,
                contentDescription = stringResource(Res.string.dossier_done_description),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class Brief(
    val verdict: String,
    val alibi: String,
    val goal: String,
    val canSay: String,
    val mustHide: String,
    val extra: String?,
)

@Composable
private fun LabeledLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xxs)) {
        Text(
            text = label,
            style = ParlorTheme.typography.labelSmall,
            color = ParlorTheme.colors.accentEmber,
        )
        Text(
            text = value,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun OptionalDetailsBlock(character: Character) {
    val od = character.optionalDetails ?: return
    val backstoryLabel = stringResource(Res.string.dossier_backstory_label)
    val actingTipsLabel = stringResource(Res.string.dossier_acting_tips_label)
    val feelingLabel = stringResource(Res.string.dossier_feeling_label)
    val suggestedLabel = stringResource(Res.string.dossier_suggested_label)
    val thatNightLabel = stringResource(Res.string.dossier_that_night_label)
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        od.backstory?.let { LabeledLine(backstoryLabel, it) }
        od.actingTips?.let { LabeledLine(actingTipsLabel, it) }
        od.emotionalMotivation?.let { LabeledLine(feelingLabel, it) }
        od.suggestedBehavior?.let { LabeledLine(suggestedLabel, it) }
        od.extraNightDetails?.let { LabeledLine(thatNightLabel, it) }
    }
}

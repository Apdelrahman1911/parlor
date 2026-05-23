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

/**
 * The dossier display per design doc §8.
 *
 * **Must Read** is always visible (one screen). **Optional Details** is
 * expandable. Verdict line is the dramatic dossier centerpiece.
 *
 * The dossier text is **case content** — this component never composes prose
 * itself, only renders what the validated case payload provided.
 */
@Composable
fun DossierCard(
    character: Character,
    role: PlayerRole,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            goal = "Stay hidden. Steer suspicion. Survive.",
            canSay = character.guiltyBrief.panicMove,
            mustHide = "The truth.",
            extra = character.guiltyBrief.actingTips,
        )
    }

    var showOptional by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

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
                text = "YOU ARE",
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

            // Verdict — the dramatic centerpiece.
            Text(
                text = brief.verdict,
                style = ParlorTheme.typography.displayLarge,
                color = when (role) {
                    PlayerRole.Innocent -> ParlorTheme.colors.semanticSuccess
                    PlayerRole.Killer -> ParlorTheme.colors.semanticDanger
                },
            )
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))

            LabeledLine("Your secret.", character.privateSecret)
            LabeledLine("Your motive.", character.publicMotive)
            LabeledLine("Your alibi.", brief.alibi)
            LabeledLine("Your goal.", brief.goal)
            LabeledLine("What you can say freely.", brief.canSay)
            LabeledLine("What you must hide.", brief.mustHide)

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.m))

            if (character.optionalDetails != null) {
                ParlorButton(
                    label = if (showOptional) "Hide details" else "More about your character",
                    onClick = { showOptional = !showOptional },
                    contentDescription = if (showOptional) {
                        "Hide optional details."
                    } else {
                        "Show optional details about your character."
                    },
                )
                if (showOptional) {
                    OptionalDetailsBlock(character)
                }
            }

            if (brief.extra != null) {
                Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
                LabeledLine("Acting tips.", brief.extra)
            }

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = "I'm Done",
                onClick = onDone,
                contentDescription = "Hide the dossier and pass the phone.",
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
            text = label.uppercase(),
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
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        od.backstory?.let { LabeledLine("Backstory.", it) }
        od.actingTips?.let { LabeledLine("Acting tips.", it) }
        od.emotionalMotivation?.let { LabeledLine("How you feel underneath.", it) }
        od.suggestedBehavior?.let { LabeledLine("Suggested behavior.", it) }
        od.extraNightDetails?.let { LabeledLine("That night.", it) }
    }
}

package com.parlor.games.dominoes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.CaseId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoVariant
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_block_variant
import com.parlor.games.dominoes.resources.dom_default_variant
import com.parlor.games.dominoes.resources.dom_draw_variant
import com.parlor.games.dominoes.resources.dom_individual
import com.parlor.games.dominoes.resources.dom_teams
import com.parlor.games.dominoes.resources.dom_teams_hint
import com.parlor.games.dominoes.resources.dom_rules
import com.parlor.games.dominoes.resources.dom_settings
import com.parlor.games.dominoes.resources.dom_target
import com.parlor.games.dominoes.resources.dom_target_value
import org.jetbrains.compose.resources.stringResource

@Composable
fun DominoSettingsContent(caseId: CaseId, playerCount: Int, enabled: Boolean, onChange: (CaseId) -> Unit) {
    val settings = requireNotNull(DominoSettings.fromCaseId(caseId.raw))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.dom_settings), color = ParlorTheme.colors.textPrimary,
            style = ParlorTheme.typography.headingLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DominoVariant.entries.forEach { variant ->
                FilterChip(
                    selected = settings.variant == variant, enabled = enabled,
                    onClick = { onChange(settings.copy(variant = variant, competition = DominoCompetition.Individual).caseId) },
                    label = { Text(stringResource(when (variant) {
                        DominoVariant.Default -> Res.string.dom_default_variant
                        DominoVariant.Draw -> Res.string.dom_draw_variant
                        DominoVariant.Block -> Res.string.dom_block_variant
                    })) },
                )
            }
        }
        if (settings.variant == DominoVariant.Default && (playerCount == 4 || settings.competition == DominoCompetition.Teams)) {
            // Keep the Individual escape hatch visible if the roster shrinks after choosing Teams.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DominoCompetition.entries.forEach { competition ->
                    FilterChip(
                        selected = settings.competition == competition,
                        enabled = enabled && (competition == DominoCompetition.Individual || playerCount == 4),
                        onClick = { onChange(settings.copy(competition = competition).caseId) },
                        label = { Text(stringResource(if (competition == DominoCompetition.Teams)
                            Res.string.dom_teams else Res.string.dom_individual)) },
                    )
                }
            }
            Text(stringResource(Res.string.dom_teams_hint), color = ParlorTheme.colors.textSecondary)
        }
        Text(stringResource(Res.string.dom_target), color = ParlorTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DominoSettings.TARGETS.forEach { target ->
                FilterChip(
                    selected = settings.target == target, enabled = enabled,
                    onClick = { onChange(settings.copy(target = target).caseId) },
                    label = { Text(stringResource(Res.string.dom_target_value, target)) },
                )
            }
        }
        Text(stringResource(Res.string.dom_rules), color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyMedium)
    }
}

package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.CaseId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordTopic
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_impostor_option
import com.parlor.games.wordimpostor.resources.wi_impostors
import com.parlor.games.wordimpostor.resources.wi_majority
import com.parlor.games.wordimpostor.resources.wi_need_players
import com.parlor.games.wordimpostor.resources.wi_round_option
import com.parlor.games.wordimpostor.resources.wi_rounds
import com.parlor.games.wordimpostor.resources.wi_rules
import com.parlor.games.wordimpostor.resources.wi_settings
import com.parlor.games.wordimpostor.resources.wi_topic
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WordImpostorSettingsContent(caseId: CaseId, playerCount: Int, enabled: Boolean, onChange: (CaseId) -> Unit) {
    val settings = requireNotNull(WordImpostorSettings.fromCaseId(caseId.raw))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.wi_settings), color = ParlorTheme.colors.textPrimary,
            style = ParlorTheme.typography.headingLarge)
        Text(stringResource(Res.string.wi_topic), color = ParlorTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WordTopic.entries.forEach { topic ->
                FilterChip(
                    selected = settings.topic == topic, enabled = enabled,
                    onClick = { onChange(settings.copy(topic = topic).caseId) },
                    label = { Text(stringResource(WordContentResources.title(topic))) },
                )
            }
        }
        Text(stringResource(Res.string.wi_impostors), color = ParlorTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in 1..3) {
                FilterChip(
                    selected = settings.impostors == count, enabled = enabled,
                    onClick = { onChange(settings.copy(impostors = count).caseId) },
                    label = { Text(pluralStringResource(Res.plurals.wi_impostor_option, count, count)) },
                )
            }
        }
        Text(stringResource(Res.string.wi_majority), color = ParlorTheme.colors.textSecondary)
        if (!settings.supports(playerCount)) {
            Text(stringResource(Res.string.wi_need_players, settings.impostors * 2 + 1), color = ParlorTheme.colors.textSecondary)
        }
        Text(stringResource(Res.string.wi_rounds), color = ParlorTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WordImpostorSettings.ROUND_COUNTS.forEach { count ->
                FilterChip(
                    selected = settings.rounds == count, enabled = enabled,
                    onClick = { onChange(settings.copy(rounds = count).caseId) },
                    label = { Text(pluralStringResource(Res.plurals.wi_round_option, count, count)) },
                )
            }
        }
        Text(stringResource(Res.string.wi_rules), color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyMedium)
    }
}

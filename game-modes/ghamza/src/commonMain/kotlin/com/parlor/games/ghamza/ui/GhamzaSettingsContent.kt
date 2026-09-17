package com.parlor.games.ghamza.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.CaseId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_attempt_option
import com.parlor.games.ghamza.resources.gh_attempts
import com.parlor.games.ghamza.resources.gh_round_option
import com.parlor.games.ghamza.resources.gh_rounds
import com.parlor.games.ghamza.resources.gh_rules
import com.parlor.games.ghamza.resources.gh_settings
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

@Composable
fun GhamzaSettingsContent(caseId: CaseId, enabled: Boolean, onChange: (CaseId) -> Unit) {
    val settings = requireNotNull(GhamzaSettings.fromCaseId(caseId.raw))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.gh_settings), color = ParlorTheme.colors.textPrimary,
            style = ParlorTheme.typography.headingLarge)
        Text(stringResource(Res.string.gh_attempts), color = ParlorTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in 1..3) {
                FilterChip(selected = settings.attempts == count, enabled = enabled,
                    onClick = { onChange(settings.copy(attempts = count).caseId) },
                    label = { Text(pluralStringResource(Res.plurals.gh_attempt_option, count, count)) })
            }
        }
        Text(stringResource(Res.string.gh_rounds), color = ParlorTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhamzaSettings.ROUND_COUNTS.forEach { count ->
                FilterChip(selected = settings.rounds == count, enabled = enabled,
                    onClick = { onChange(settings.copy(rounds = count).caseId) },
                    label = { Text(pluralStringResource(Res.plurals.gh_round_option, count, count)) })
            }
        }
        Text(stringResource(Res.string.gh_rules), color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyMedium)
    }
}

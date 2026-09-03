package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.parlor.content.repository.CaseRepository
import com.parlor.content.schema.CaseSummary
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EmptyState
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.case_picker_back_description
import com.parlor.games.whodunit.resources.case_picker_continue
import com.parlor.games.whodunit.resources.case_picker_continue_description_format
import com.parlor.games.whodunit.resources.case_picker_empty
import com.parlor.games.whodunit.resources.case_picker_eyebrow
import com.parlor.games.whodunit.resources.case_picker_filter_all
import com.parlor.games.whodunit.resources.case_picker_filter_arabic
import com.parlor.games.whodunit.resources.case_picker_filter_english
import com.parlor.games.whodunit.resources.case_picker_language_arabic
import com.parlor.games.whodunit.resources.case_picker_language_english
import com.parlor.games.whodunit.resources.case_picker_language_format
import com.parlor.games.whodunit.resources.case_picker_mode_classic
import com.parlor.games.whodunit.resources.case_picker_mode_elimination
import com.parlor.games.whodunit.resources.case_picker_mode_unknown
import com.parlor.games.whodunit.resources.case_picker_modes_format
import com.parlor.games.whodunit.resources.case_picker_offline
import com.parlor.games.whodunit.resources.case_picker_players_format
import com.parlor.games.whodunit.resources.case_picker_subtitle
import com.parlor.games.whodunit.resources.case_picker_title
import com.parlor.games.whodunit.resources.library_load_error_format
import com.parlor.games.whodunit.resources.whodunit_list_separator
import com.parlor.games.whodunit.ui.dataErrorMessage
import org.jetbrains.compose.resources.stringResource

/** Bilingual, offline story shelf used by local and host setup. */
@Composable
fun WhodunitCasePickerScreen(
    repository: CaseRepository,
    onCasePicked: (CaseSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val casesResult by produceState<Result<List<CaseSummary>, DataError>?>(
        initialValue = null,
        key1 = repository,
    ) {
        value = repository.listCases(WhodunitIds.GameId)
    }
    var filter by remember { mutableStateOf(CaseFilter.All) }
    var selectedCaseId by remember { mutableStateOf<String?>(null) }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.case_picker_title),
                eyebrow = stringResource(Res.string.case_picker_eyebrow),
                subtitle = stringResource(Res.string.case_picker_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.case_picker_back_description),
            )

            when (val state = casesResult) {
                null -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CandleFlame(size = ParlorTheme.iconSize.xl)
                }
                is Result.Failure -> EmptyState(
                    title = stringResource(
                        Res.string.library_load_error_format,
                        dataErrorMessage(state.error),
                    ),
                    modifier = Modifier.weight(1f),
                )
                is Result.Success -> CaseLibrary(
                    cases = state.data,
                    filter = filter,
                    selectedCaseId = selectedCaseId,
                    onFilterSelected = { filter = it },
                    onCaseSelected = { selectedCaseId = it.caseId },
                    onContinue = onCasePicked,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CaseLibrary(
    cases: List<CaseSummary>,
    filter: CaseFilter,
    selectedCaseId: String?,
    onFilterSelected: (CaseFilter) -> Unit,
    onCaseSelected: (CaseSummary) -> Unit,
    onContinue: (CaseSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cases.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.case_picker_empty),
            modifier = modifier,
        )
        return
    }
    val visibleCases = cases.filter(filter::accepts)
    val selected = visibleCases.firstOrNull { it.caseId == selectedCaseId }
        ?: visibleCases.firstOrNull()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
    ) {
        FilterRow(filter = filter, onSelected = onFilterSelected)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            items(visibleCases, key = { it.caseId }) { summary ->
                CaseRow(
                    summary = summary,
                    selected = summary.caseId == selected?.caseId,
                    onClick = { onCaseSelected(summary) },
                )
            }
        }
        selected?.let { current ->
            ParlorButton(
                label = stringResource(Res.string.case_picker_continue),
                contentDescription = stringResource(
                    Res.string.case_picker_continue_description_format,
                    current.title,
                ),
                onClick = { onContinue(current) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilterRow(filter: CaseFilter, onSelected: (CaseFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        CaseFilter.entries.forEach { option ->
            val label = when (option) {
                CaseFilter.All -> stringResource(Res.string.case_picker_filter_all)
                CaseFilter.English -> stringResource(Res.string.case_picker_filter_english)
                CaseFilter.Arabic -> stringResource(Res.string.case_picker_filter_arabic)
            }
            val selected = option == filter
            val shape = RoundedCornerShape(ParlorTheme.radii.pill)
            Text(
                text = label,
                style = ParlorTheme.typography.labelMedium,
                color = if (selected) {
                    ParlorTheme.colors.textOnAccent
                } else {
                    ParlorTheme.colors.textSecondary
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (selected) {
                            ParlorTheme.colors.accentEmber
                        } else {
                            ParlorTheme.colors.surfaceElevated
                        },
                    )
                    .border(
                        ParlorTheme.borders.hairline,
                        if (selected) {
                            ParlorTheme.colors.accentEmber
                        } else {
                            ParlorTheme.colors.borderSubtle
                        },
                        shape,
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    )
                    .padding(
                        horizontal = ParlorTheme.spacing.s,
                        vertical = ParlorTheme.spacing.m,
                    ),
            )
        }
    }
}

@Composable
private fun CaseRow(
    summary: CaseSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val languageName = when (caseLanguageLabel(summary.language)) {
        CaseLanguageLabel.English -> stringResource(Res.string.case_picker_language_english)
        CaseLanguageLabel.Arabic -> stringResource(Res.string.case_picker_language_arabic)
        CaseLanguageLabel.Other -> summary.language
    }
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    ParlorCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        cornerRadius = ParlorTheme.radii.card,
        contentPadding = ParlorTheme.spacing.l,
        hero = selected,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.case_picker_language_format, languageName),
                    modifier = Modifier
                        .clip(shape)
                        .background(ParlorTheme.colors.surfaceHigher)
                        .padding(
                            horizontal = ParlorTheme.spacing.s,
                            vertical = ParlorTheme.spacing.xs,
                        ),
                    style = ParlorTheme.typography.labelSmall,
                    color = ParlorTheme.colors.textSecondary,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(ParlorTheme.iconSize.xs)
                            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                            .background(ParlorTheme.colors.accentEmber),
                    )
                }
            }
            Text(
                text = summary.title,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            summary.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                )
            }
            Text(
                text = stringResource(
                    Res.string.case_picker_players_format,
                    casePlayerRange(summary),
                ),
                style = ParlorTheme.typography.labelMedium,
                color = ParlorTheme.colors.accentEmber,
            )
            Text(
                text = stringResource(
                    Res.string.case_picker_modes_format,
                    localizedModeNames(summary.supportedModes),
                ),
                style = ParlorTheme.typography.bodySmall,
                color = ParlorTheme.colors.textTertiary,
            )
            Text(
                text = stringResource(Res.string.case_picker_offline),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.semanticSuccess,
            )
        }
    }
}

private fun casePlayerRange(summary: CaseSummary): String =
    if (summary.supportedPlayerCounts.min == summary.supportedPlayerCounts.max) {
        summary.supportedPlayerCounts.min.toString()
    } else {
        "${summary.supportedPlayerCounts.min}–${summary.supportedPlayerCounts.max}"
    }

internal enum class CaseLanguageLabel { English, Arabic, Other }

internal fun caseLanguageLabel(languageTag: String): CaseLanguageLabel =
    when (languageTag.substringBefore('-').lowercase()) {
        "en" -> CaseLanguageLabel.English
        "ar" -> CaseLanguageLabel.Arabic
        else -> CaseLanguageLabel.Other
    }

private enum class CaseFilter {
    All,
    English,
    Arabic,
    ;

    fun accepts(summary: CaseSummary): Boolean = when (this) {
        All -> true
        English -> caseLanguageLabel(summary.language) == CaseLanguageLabel.English
        Arabic -> caseLanguageLabel(summary.language) == CaseLanguageLabel.Arabic
    }
}

@Composable
private fun localizedModeNames(modeIds: List<String>): String {
    val classic = stringResource(Res.string.case_picker_mode_classic)
    val elimination = stringResource(Res.string.case_picker_mode_elimination)
    val unknown = stringResource(Res.string.case_picker_mode_unknown)
    val separator = stringResource(Res.string.whodunit_list_separator)
    return modeIds.joinToString(separator) { modeId ->
        when (modeId) {
            WhodunitIds.ClassicVoteModeId.raw -> classic
            WhodunitIds.EliminationModeId.raw -> elimination
            else -> unknown
        }
    }
}

package com.parlor.app.shell.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.case_picker_back
import com.parlor.app.resources.library_load_error_format
import com.parlor.app.resources.case_picker_back_description
import com.parlor.app.resources.case_picker_eyebrow
import com.parlor.app.resources.case_picker_empty
import com.parlor.app.resources.case_picker_loading
import com.parlor.app.resources.case_picker_modes_format
import com.parlor.app.resources.case_picker_players_format
import com.parlor.app.resources.case_picker_select_description
import com.parlor.app.resources.case_picker_subtitle
import com.parlor.app.resources.case_picker_title
import com.parlor.content.repository.CaseRepository
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EmptyState
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.pressableSurface
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import org.jetbrains.compose.resources.stringResource

/**
 * Discovers every Whodunit case the [CaseRepository] knows about and renders
 * each as a tap-able card. Used by both pass-and-play (Home → "Begin
 * investigation" → picker → game) and multi-device host flow (Home → "Host
 * a game" → name → picker → mode → lobby).
 *
 * No hardcoded case ids. Adding a new JSON to `composeResources/files/cases/`
 * plus a one-line entry in `WhodunitDiModule.knownCaseIds` makes that case
 * appear here without any UI code change.
 *
 * The list is fully scrollable; the cards stretch full-width on phones and
 * the title typography scales with the card height — the same component
 * renders cleanly at 360 dp wide or on desktop.
 */
@Composable
fun CasePickerScreen(
    repository: CaseRepository,
    onCasePicked: (CaseSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val casesResult by produceState<Result<List<CaseSummary>, *>?>(
        initialValue = null,
        key1 = repository,
    ) {
        value = repository.listCases(WhodunitIds.GameId)
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = ParlorTheme.spacing.l,
                    vertical = ParlorTheme.spacing.l,
                ),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
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
                    title = stringResource(Res.string.library_load_error_format).replace(
                        "%1\$s",
                        state.error.toString(),
                    ),
                    modifier = Modifier.weight(1f),
                )
                is Result.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyState(
                            title = stringResource(Res.string.case_picker_empty),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                        ) {
                            items(state.data, key = { it.caseId }) { summary ->
                                CaseRow(
                                    summary = summary,
                                    onClick = { onCasePicked(summary) },
                                )
                            }
                        }
                    }
                }
            }

            // Back is in the ScreenHeader chevron now. Bottom button row
            // removed; the screen header carries the back affordance.
        }
    }
}

@Composable
private fun CaseRow(summary: CaseSummary, onClick: () -> Unit) {
    val colors = ParlorTheme.colors
    val playersText = stringResource(Res.string.case_picker_players_format).replace(
        "%1\$s",
        "${summary.supportedPlayerCounts.min}–${summary.supportedPlayerCounts.max}",
    )
    val modesText = stringResource(Res.string.case_picker_modes_format).replace(
        "%1\$s",
        summary.supportedModes.joinToString(", "),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressableSurface(onClick = onClick, cornerRadius = ParlorTheme.radii.elevated)
            .border(
                width = ParlorTheme.borders.hairline,
                color = colors.borderElevated,
                shape = RoundedCornerShape(ParlorTheme.radii.elevated),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            Text(
                text = summary.title,
                style = ParlorTheme.typography.displayMedium,
                color = colors.textPrimary,
            )
            summary.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = ParlorTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                Text(
                    text = playersText,
                    style = ParlorTheme.typography.labelSmall,
                    color = colors.accentEmber,
                )
                Text(
                    text = modesText,
                    style = ParlorTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}


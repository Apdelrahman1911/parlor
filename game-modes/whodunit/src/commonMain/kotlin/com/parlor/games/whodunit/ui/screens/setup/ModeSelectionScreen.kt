package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.ModeId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_choose_mode_eyebrow
import com.parlor.games.whodunit.resources.setup_choose_mode_headline
import com.parlor.games.whodunit.resources.setup_mode_choose_description_format
import com.parlor.games.whodunit.resources.setup_mode_choose_format
import com.parlor.games.whodunit.resources.setup_mode_classic_body
import com.parlor.games.whodunit.resources.setup_mode_classic_meta
import com.parlor.games.whodunit.resources.setup_mode_classic_title
import com.parlor.games.whodunit.resources.setup_mode_elimination_body
import com.parlor.games.whodunit.resources.setup_mode_elimination_meta
import com.parlor.games.whodunit.resources.setup_mode_elimination_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ModeSelectionScreen(
    onModeSelected: (ModeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.setup_choose_mode_eyebrow),
                accent = false,
            )
            Text(
                text = stringResource(Res.string.setup_choose_mode_headline),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModeCard(
                    title = stringResource(Res.string.setup_mode_classic_title),
                    body = stringResource(Res.string.setup_mode_classic_body),
                    meta = stringResource(Res.string.setup_mode_classic_meta),
                    onClick = { onModeSelected(WhodunitIds.ClassicVoteModeId) },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    title = stringResource(Res.string.setup_mode_elimination_title),
                    body = stringResource(Res.string.setup_mode_elimination_body),
                    meta = stringResource(Res.string.setup_mode_elimination_meta),
                    onClick = { onModeSelected(WhodunitIds.EliminationModeId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    body: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonLabel = stringResource(Res.string.setup_mode_choose_format, title)
    val buttonDescription = stringResource(Res.string.setup_mode_choose_description_format, title)
    ParlorCard(
        modifier = modifier.fillMaxWidth(),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = meta,
                style = ParlorTheme.typography.labelMedium,
                color = ParlorTheme.colors.accentEmber,
            )
            ParlorButton(
                label = buttonLabel,
                contentDescription = buttonDescription,
                onClick = onClick,
            )
        }
    }
}

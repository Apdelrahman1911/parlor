package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_clue_eyebrow_label
import org.jetbrains.compose.resources.stringResource

/**
 * Museum-label-style clue display. Short, large, readable across a table.
 */
@Composable
fun ClueCard(
    text: String,
    eyebrow: String = stringResource(Res.string.round_clue_eyebrow_label),
    modifier: Modifier = Modifier,
) {
    ParlorCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xxl,
        hero = true,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            EyebrowLabel(
                text = eyebrow,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = text,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

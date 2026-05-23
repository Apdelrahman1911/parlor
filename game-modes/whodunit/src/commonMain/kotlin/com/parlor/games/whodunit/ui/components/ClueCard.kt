package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Museum-label-style clue display. Short, large, readable across a table.
 */
@Composable
fun ClueCard(
    text: String,
    eyebrow: String = "NEW CLUE",
    modifier: Modifier = Modifier,
) {
    ParlorCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.l),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xxl,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = eyebrow,
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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

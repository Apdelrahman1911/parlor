package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ParlorTheme.spacing.s)
            .background(ParlorTheme.colors.surfacePaper, shape)
            .border(
                ParlorTheme.borders.hairline,
                ParlorTheme.colors.borderElevated,
                shape,
            )
            .padding(ParlorTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = ParlorTheme.typography.labelSmall,
            color = ParlorTheme.colors.textOnPaperSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            style = ParlorTheme.typography.displayMedium,
            color = ParlorTheme.colors.textOnPaper,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

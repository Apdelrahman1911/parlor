package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/** Visibility/authority status shown above sensitive or host-led moments. */
enum class ParlorContextTone { Public, Private, Host }

@Composable
fun ContextRibbon(
    label: String,
    detail: String,
    tone: ParlorContextTone,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
) {
    val colors = ParlorTheme.colors
    val marker = when (tone) {
        ParlorContextTone.Public -> colors.semanticSuccess
        ParlorContextTone.Private -> colors.accentEmber
        ParlorContextTone.Host -> colors.accentBrass
    }
    val shape = RoundedCornerShape(ParlorTheme.radii.subtle)
    val background = if (inverted) colors.coverScreen else colors.surfaceInset
    val primaryText = if (inverted) colors.coverScreenTextPrimary else colors.textPrimary
    val secondaryText = if (inverted) colors.coverScreenTextSecondary else colors.textSecondary
    val border = if (inverted) {
        colors.coverScreenTextTertiary.copy(alpha = INVERTED_BORDER_ALPHA)
    } else {
        colors.borderSubtle
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(ParlorTheme.borders.hairline, border, shape)
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContextMarker(marker)
        Text(
            text = label,
            style = ParlorTheme.typography.labelSmall,
            color = primaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = ParlorTheme.typography.labelSmall,
            color = secondaryText,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val INVERTED_BORDER_ALPHA = 0.55f

@Composable
private fun ContextMarker(color: Color) {
    Box(
        modifier = Modifier
            .size(ParlorTheme.iconSize.xxs)
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(color),
    )
}

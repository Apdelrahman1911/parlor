package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Reusable empty/error state block. A circular glyph "well" + title + body +
 * optional action. Honest about what's missing — and replaces every screen's
 * bare-Text "Couldn't load..." with a consistent treatment.
 *
 *  - [title] required.
 *  - [body] optional supporting line.
 *  - [glyph] optional 1-2 character mark drawn into the circular well.
 *    Defaults to a centred dot.
 *  - [action] optional composable for a single CTA (typically a ParlorButton).
 *
 * Used both for "no cases" and "couldn't load cases" — the difference is
 * the action and the copy, not the shape.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    glyph: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = ParlorTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ParlorTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
    ) {
        Box(
            modifier = Modifier
                .size(ParlorTheme.iconSize.hero)
                .clip(CircleShape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph ?: "·",
                style = ParlorTheme.typography.displayMedium,
                color = colors.textTertiary,
            )
        }
        Text(
            text = title,
            style = ParlorTheme.typography.displayMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
            action()
        }
    }
}

package com.parlor.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Standard top-of-screen header. Replaces the ad-hoc Row + clickable
 * Text patterns that every screen rolled itself. Layout:
 *
 *     [back chevron]   [optional eyebrow]
 *                      [title]
 *                      [optional subtitle]                   [trailing]
 *
 * `onBack = null` hides the back affordance (root screens). `trailing`
 * is an optional composable for a settings cog or similar.
 *
 * All sizing / spacing / color comes from tokens; no literals.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ParlorTheme.spacing.l),
        verticalAlignment = Alignment.Top,
    ) {
        if (onBack != null) {
            BackChevron(
                onClick = onBack,
                contentDescription = backContentDescription,
            )
            Spacer(modifier = Modifier.padding(end = ParlorTheme.spacing.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                EyebrowLabel(text = eyebrow, accent = false)
                Spacer(modifier = Modifier.padding(top = ParlorTheme.spacing.xs))
            }
            Text(
                text = title,
                style = ParlorTheme.typography.displayLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.padding(top = ParlorTheme.spacing.xs))
                Text(
                    text = subtitle,
                    style = ParlorTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.padding(start = ParlorTheme.spacing.m))
            trailing()
        }
    }
}

@Composable
private fun BackChevron(
    onClick: () -> Unit,
    contentDescription: String?,
) {
    val colors = ParlorTheme.colors
    val description = contentDescription
    Box(
        modifier = Modifier
            .size(ParlorTheme.spacing.xxl)
            .clickable(onClick = onClick)
            .then(
                if (description != null) {
                    Modifier.semantics { this.contentDescription = description }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Chevron drawn as a single bold "‹" glyph in the body typeface,
        // sized via labelLarge so it matches the back-affordance scale on
        // every other major design system.
        Text(
            text = "‹",
            style = ParlorTheme.typography.displayMedium,
            color = colors.textSecondary,
        )
    }
}

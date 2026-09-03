package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Standard top-of-screen header. Replaces the ad-hoc Row + clickable
 * Text patterns that every screen rolled itself. Layout:
 *
 *     [back icon]      [optional eyebrow]
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
            BackButton(
                onClick = onBack,
                contentDescription = requireNotNull(backContentDescription) {
                    "A back content description is required when onBack is provided"
                },
            )
            Spacer(modifier = Modifier.width(ParlorTheme.spacing.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                EyebrowLabel(text = eyebrow)
                Spacer(modifier = Modifier.padding(top = ParlorTheme.spacing.xs))
            }
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.padding(top = ParlorTheme.spacing.xs))
                Text(
                    text = subtitle,
                    style = ParlorTheme.typography.bodyMedium,
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
private fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
) {
    ParlorIconButton(
        icon = ParlorIcons.Back,
        contentDescription = contentDescription,
        onClick = onClick,
    )
}

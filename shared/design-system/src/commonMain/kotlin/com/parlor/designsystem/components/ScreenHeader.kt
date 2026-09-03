package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
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
private fun BackChevron(
    onClick: () -> Unit,
    contentDescription: String?,
) {
    val colors = ParlorTheme.colors
    val description = contentDescription
    val glyph = backChevronGlyph(LocalLayoutDirection.current)
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    Box(
        modifier = Modifier
            .size(ParlorTheme.spacing.xxl)
            .clip(shape)
            .background(colors.surfaceElevated)
            .border(ParlorTheme.borders.hairline, colors.borderSubtle, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (description != null) {
                    Modifier.semantics { this.contentDescription = description }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = ParlorTheme.typography.displayMedium,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

internal fun backChevronGlyph(layoutDirection: LayoutDirection): String =
    if (layoutDirection == LayoutDirection.Rtl) "›" else "‹"

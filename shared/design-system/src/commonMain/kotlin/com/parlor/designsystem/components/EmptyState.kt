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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Reusable empty/error state block. A circular icon well + title + body +
 * optional action. Honest about what's missing — and replaces every screen's
 * bare-Text "Couldn't load..." with a consistent treatment.
 *
 *  - [title] required.
 *  - [body] optional supporting line.
 *  - [icon] visual mark drawn into the circular well. Defaults to a folder.
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
    icon: ImageVector = ParlorIcons.FolderOpen,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(ParlorTheme.iconSize.xl),
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

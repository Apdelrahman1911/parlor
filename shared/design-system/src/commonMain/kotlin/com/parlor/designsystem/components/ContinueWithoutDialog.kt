package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Confirmation modal for the host's destructive "Continue without
 * [name]" lifecycle action. Individual games decide whether that action drops
 * a setup seat or ends an active hidden-role game; either outcome is costly,
 * so the transition always requires a second explicit tap.
 *
 * Localization: every text comes in pre-formatted. The "%1$s" in the
 * EN/AR strings is interpolated by the caller against the offline
 * player's display name. Button order respects platform convention
 * (Cancel start, Confirm end in LTR; logical start/end placement mirrors in
 * RTL through the ambient layout direction).
 */
@Composable
fun ContinueWithoutDialog(
    title: String,
    body: String,
    cancelLabel: String,
    confirmLabel: String,
    confirmContentDescription: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.coverScreen)
            .semantics { paneTitle = title }
            .parlorSafeContentPadding(ParlorTheme.spacing.l),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(ParlorTheme.radii.elevated))
                .background(colors.surfaceElevated)
                .padding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                ParlorButton(
                    label = cancelLabel,
                    contentDescription = cancelLabel,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    variant = ParlorButtonVariant.Ghost,
                )
                ParlorButton(
                    label = confirmLabel,
                    contentDescription = confirmContentDescription,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    variant = ParlorButtonVariant.Destructive,
                )
            }
        }
    }
}

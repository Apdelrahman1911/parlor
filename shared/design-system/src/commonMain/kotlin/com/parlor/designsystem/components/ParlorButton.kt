package com.parlor.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Primary action button. Ember accent, premium-feeling press feedback. Enforces
 * a minimum 48dp touch target and requires a `contentDescription` for screen
 * readers — accessibility per ARCHITECTURE.md §10.2.3.
 */
@Composable
fun ParlorButton(
    label: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ParlorTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(ParlorTheme.radii.subtle),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentEmber,
            contentColor = colors.textOnAccent,
            disabledContainerColor = colors.semanticMuted,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(
            horizontal = ParlorTheme.spacing.xl,
            vertical = ParlorTheme.spacing.m,
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Row {
            Text(
                text = label,
                style = ParlorTheme.typography.labelLarge,
            )
        }
    }
}

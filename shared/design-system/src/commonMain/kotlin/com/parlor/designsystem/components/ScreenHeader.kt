package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Shared navigation header. Back/eyebrow/trailing occupy a compact navigation row;
 * title and explanation keep the entire width below it, including at large font scales.
 * Direction-aware back icons and logical layout order follow the active app language.
 * `onBack = null` leaves a root header; interactive back always requires an accessible label.
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
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = ParlorTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        if (onBack != null || eyebrow != null || trailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    BackButton(
                        onClick = onBack,
                        contentDescription = requireNotNull(backContentDescription) {
                            "A back content description is required when onBack is provided"
                        },
                    )
                }
                if (eyebrow != null) {
                    EyebrowLabel(text = eyebrow, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
        }
        Text(
            text = title,
            style = ParlorTheme.typography.displayMedium,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = ParlorTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
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

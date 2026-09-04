package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.theme.ParlorTheme

enum class ParlorIconButtonVariant { Surface, Ghost }

/**
 * A 48dp icon-only action with a required localized accessible name.
 * Decorative icons should use [Icon] directly with a null description.
 */
@Composable
fun ParlorIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ParlorIconButtonVariant = ParlorIconButtonVariant.Surface,
    tint: Color? = null,
) {
    require(contentDescription.isNotBlank()) {
        "Icon buttons require a non-blank content description"
    }

    val colors = ParlorTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    val containerColor = when (variant) {
        ParlorIconButtonVariant.Surface -> colors.surfaceElevated
        ParlorIconButtonVariant.Ghost -> colors.transparent
    }
    val iconColor = when {
        !enabled -> colors.textTertiary
        tint != null -> tint
        else -> colors.textSecondary
    }
    val borderColor = when (variant) {
        ParlorIconButtonVariant.Surface -> colors.borderSubtle
        ParlorIconButtonVariant.Ghost -> colors.transparent
    }
    val pressedTint = if (isPressed && enabled) {
        colors.accentEmber.copy(alpha = PRESSED_TINT_ALPHA)
    } else {
        colors.transparent
    }

    Box(
        modifier = modifier
            .size(ParlorTheme.spacing.xxl)
            .clip(shape)
            .background(containerColor)
            .background(pressedTint)
            .border(ParlorTheme.borders.hairline, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(ParlorTheme.iconSize.l),
        )
    }
}

private const val PRESSED_TINT_ALPHA = 0.12f

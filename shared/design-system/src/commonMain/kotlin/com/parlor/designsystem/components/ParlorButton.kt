package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Visual variants — editorial direction.
 *
 *  - [Primary] — coral-filled, no border. The most important action on
 *    the screen.
 *  - [Secondary] — surfaceElevated body, hairline border, primary text.
 *    Companion to a primary on the same screen.
 *  - [Ghost] — transparent body, no border, secondary text. Back/nav
 *    style actions that should recede.
 *  - [Destructive] — danger-tinted body for leave/end/delete paths.
 */
enum class ParlorButtonVariant { Primary, Secondary, Ghost, Destructive }

/**
 * Editorial action button. Flat, no decoration, no lift. Press feedback
 * is a brief background-tint shift only. Enforces a 52dp minimum touch
 * target and requires a contentDescription for screen readers.
 */
@Composable
fun ParlorButton(
    label: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ParlorButtonVariant = ParlorButtonVariant.Primary,
    loading: Boolean = false,
) {
    val colors = ParlorTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val interactive = enabled && !loading

    val style = ParlorButtonVariantStyle.of(variant, colors)

    val containerColor: Color
    val contentColor: Color
    val borderColor: Color
    if (!enabled) {
        containerColor = when (variant) {
            ParlorButtonVariant.Ghost, ParlorButtonVariant.Secondary -> colors.transparent
            else -> colors.semanticMuted
        }
        contentColor = colors.textTertiary
        borderColor = colors.borderSubtle
    } else {
        containerColor = style.background
        contentColor = style.foreground
        borderColor = style.border
    }

    // Press feedback: a brief tint shift on the background only. No scale,
    // no glow.
    val pressedTint = if (isPressed && interactive) {
        when (variant) {
            ParlorButtonVariant.Primary -> colors.accentEmberDeep.copy(alpha = 0.35f)
            ParlorButtonVariant.Destructive -> colors.semanticDanger.copy(alpha = 0.35f)
            ParlorButtonVariant.Secondary -> colors.accentEmber.copy(alpha = 0.10f)
            ParlorButtonVariant.Ghost -> colors.accentEmber.copy(alpha = 0.08f)
        }
    } else {
        colors.transparent
    }

    val shape = RoundedCornerShape(ParlorTheme.radii.subtle)

    Box(
        modifier = modifier
            // 52dp is the design-system primitive's intrinsic touch target,
            // not a feature-UI hardcoded value. Lives here, not in screens.
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(containerColor)
            .background(pressedTint)
            .border(
                width = ParlorTheme.borders.hairline,
                color = borderColor,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(
                PaddingValues(
                    horizontal = ParlorTheme.spacing.xl,
                    vertical = ParlorTheme.spacing.m,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            if (loading) {
                ParlorActivityIndicator(
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = LOADING_TRACK_ALPHA),
                    strokeWidth = ParlorTheme.borders.strong,
                    modifier = Modifier.size(ParlorTheme.iconSize.s),
                )
            }
            Text(
                text = label,
                style = ParlorTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

private const val LOADING_TRACK_ALPHA: Float = 0.35f

private data class ParlorButtonVariantStyle(
    val background: Color,
    val foreground: Color,
    val border: Color,
) {
    companion object {
        fun of(
            variant: ParlorButtonVariant,
            colors: com.parlor.designsystem.tokens.ParlorColors,
        ): ParlorButtonVariantStyle = when (variant) {
            ParlorButtonVariant.Primary -> ParlorButtonVariantStyle(
                background = colors.accentEmber,
                foreground = colors.textOnAccent,
                border = colors.transparent,
            )
            ParlorButtonVariant.Secondary -> ParlorButtonVariantStyle(
                background = colors.surfaceElevated,
                foreground = colors.textPrimary,
                border = colors.borderSubtle,
            )
            ParlorButtonVariant.Ghost -> ParlorButtonVariantStyle(
                background = colors.transparent,
                foreground = colors.textSecondary,
                border = colors.transparent,
            )
            ParlorButtonVariant.Destructive -> ParlorButtonVariantStyle(
                background = colors.semanticDanger,
                foreground = colors.textOnAccent,
                border = colors.transparent,
            )
        }
    }
}

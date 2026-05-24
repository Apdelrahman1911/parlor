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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Visual variant of [ParlorButton]. The design system was extended in the
 * Phase 8 UI rework so screens can express intent without falling back to
 * raw Material primitives.
 *
 *  - [Primary] — ember-filled, brass rim. The single most important action
 *    on the screen (e.g. "Begin Investigation", "Connect", "Start Game").
 *  - [Secondary] — transparent body, ember rim and text. Companion actions
 *    where the primary is in the same screen.
 *  - [Ghost] — transparent body, subtle text. Navigation/back-style
 *    affordances that should not visually compete with the primary.
 *  - [Destructive] — danger-tinted body. Used by leave/end/delete paths.
 */
enum class ParlorButtonVariant { Primary, Secondary, Ghost, Destructive }

/**
 * Parlor's action button. Replaces the previous Material `Button` with a
 * cozy-noir treatment: pressed scaling, ember-rim glow, full state matrix
 * (rest / pressed / disabled / loading). Enforces a minimum 48dp touch
 * target and requires a `contentDescription` for screen readers.
 *
 * Backwards-compatible with prior call sites: `label`, `onClick`,
 * `contentDescription`, `modifier`, `enabled` keep the same names and
 * positions. `variant` and `loading` are new optional parameters with
 * `Primary` and `false` defaults — adding `ParlorButtonVariant.Secondary`
 * to an existing call gets you the new visual.
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

    // Per-variant treatment: (background, foreground, border, press tint alpha).
    val variantStyle = ParlorButtonVariantStyle.of(variant, colors)
    val containerColor = variantStyle.background
    val contentColor = variantStyle.foreground
    val borderColor = variantStyle.border
    val pressedTintAlpha = variantStyle.pressedTintAlpha

    // Disabled override: muted body, tertiary text. Same shape, no border accent.
    val effectiveContainer: Color
    val effectiveContent: Color
    val effectiveBorder: Color
    if (!enabled) {
        effectiveContainer = colors.semanticMuted.copy(
            alpha = if (variant == ParlorButtonVariant.Ghost) 0f else 0.4f,
        )
        effectiveContent = colors.textTertiary
        effectiveBorder = colors.borderSubtle
    } else {
        effectiveContainer = containerColor
        effectiveContent = contentColor
        effectiveBorder = borderColor
    }
    // Press tint — slight dark/light wash on top of the container.
    val pressedTint = if (isPressed && interactive) {
        if (variant == ParlorButtonVariant.Primary || variant == ParlorButtonVariant.Destructive) {
            Color.Black.copy(alpha = pressedTintAlpha)
        } else {
            colors.accentEmber.copy(alpha = pressedTintAlpha)
        }
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ParlorTheme.radii.subtle))
            .background(effectiveContainer)
            .background(pressedTint)
            .border(
                width = 1.dp,
                color = effectiveBorder,
                shape = RoundedCornerShape(ParlorTheme.radii.subtle),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = interactive,
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
                CircularProgressIndicator(
                    color = effectiveContent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = ParlorTheme.typography.labelLarge,
                color = effectiveContent,
            )
        }
    }
}

private data class ParlorButtonVariantStyle(
    val background: Color,
    val foreground: Color,
    val border: Color,
    val pressedTintAlpha: Float,
) {
    companion object {
        fun of(
            variant: ParlorButtonVariant,
            colors: com.parlor.designsystem.tokens.ParlorColors,
        ): ParlorButtonVariantStyle = when (variant) {
            ParlorButtonVariant.Primary -> ParlorButtonVariantStyle(
                background = colors.accentEmber,
                foreground = colors.textOnAccent,
                border = colors.accentBrass.copy(alpha = 0.6f),
                pressedTintAlpha = 0.12f,
            )
            ParlorButtonVariant.Secondary -> ParlorButtonVariantStyle(
                background = Color.Transparent,
                foreground = colors.accentEmber,
                border = colors.accentEmber.copy(alpha = 0.8f),
                pressedTintAlpha = 0.10f,
            )
            ParlorButtonVariant.Ghost -> ParlorButtonVariantStyle(
                background = Color.Transparent,
                foreground = colors.textSecondary,
                border = colors.borderSubtle,
                pressedTintAlpha = 0.06f,
            )
            ParlorButtonVariant.Destructive -> ParlorButtonVariantStyle(
                background = colors.semanticDanger,
                foreground = colors.textOnAccent,
                border = colors.semanticDanger.copy(alpha = 0.4f),
                pressedTintAlpha = 0.14f,
            )
        }
    }
}

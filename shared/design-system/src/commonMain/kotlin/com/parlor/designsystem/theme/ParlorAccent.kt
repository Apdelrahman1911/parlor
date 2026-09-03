package com.parlor.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.parlor.designsystem.tokens.ParlorColors
import com.parlor.designsystem.tokens.isLight

/**
 * Game-neutral stage accents. Feature modules choose a semantic accent; shared
 * session and networking code never needs to know which game is being shown.
 */
enum class ParlorAccent {
    Amber,
    Crimson,
}

/**
 * Applies one game accent to design-system and Material components below it.
 * Surface, semantic, and privacy colors are deliberately unchanged.
 */
@Composable
fun ParlorAccentScope(
    accent: ParlorAccent,
    content: @Composable () -> Unit,
) {
    val accentedColors = ParlorTheme.colors.withAccent(accent)
    val materialColors = MaterialTheme.colorScheme.copy(
        primary = accentedColors.accentEmber,
        onPrimary = accentedColors.textOnAccent,
        outline = accentedColors.borderElevated,
        outlineVariant = accentedColors.borderSubtle,
    )
    CompositionLocalProvider(
        LocalParlorColors provides accentedColors,
        LocalContentColor provides accentedColors.textPrimary,
    ) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}

internal fun ParlorColors.withAccent(accent: ParlorAccent): ParlorColors {
    if (accent == ParlorAccent.Amber) return this
    val crimson = if (isLight) LightCrimsonAccent else DarkCrimsonAccent
    return copy(
        accentEmber = crimson.primary,
        accentEmberGlow = crimson.glow,
        accentEmberDeep = crimson.deep,
        borderGlow = crimson.primary.copy(alpha = ACCENT_GLOW_ALPHA),
        borderAccent = crimson.primary,
    )
}

private data class AccentValues(
    val primary: Color,
    val glow: Color,
    val deep: Color,
)

private val DarkCrimsonAccent = AccentValues(
    primary = Color(0xFFEF625E),
    glow = Color(0xFFFFB1AD),
    deep = Color(0xFF7D2728),
)

private val LightCrimsonAccent = AccentValues(
    primary = Color(0xFFB42F31),
    glow = Color(0xFF7E1D20),
    deep = Color(0xFF701719),
)

private const val ACCENT_GLOW_ALPHA = 0.20f

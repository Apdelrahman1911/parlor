package com.parlor.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.parlor.designsystem.motion.ParlorMotion
import com.parlor.designsystem.tokens.CozyNoirPalette
import com.parlor.designsystem.tokens.DefaultParlorTypography
import com.parlor.designsystem.tokens.ParlorBlur
import com.parlor.designsystem.tokens.ParlorColors
import com.parlor.designsystem.tokens.ParlorElevation
import com.parlor.designsystem.tokens.ParlorRadii
import com.parlor.designsystem.tokens.ParlorSpacing
import com.parlor.designsystem.tokens.ParlorTypography

/**
 * Parlor's design-system entry point. Wraps Material 3 with our token system
 * so existing M3 components inherit cozy-noir colors while we layer Parlor's
 * custom components on top.
 *
 * Access tokens inside Composable code via `ParlorTheme.colors`, `.typography`,
 * `.spacing`, `.elevation`, `.radii`, `.blur`, `.motion`.
 */
object ParlorTheme {
    val colors: ParlorColors
        @Composable @ReadOnlyComposable get() = LocalParlorColors.current
    val typography: ParlorTypography
        @Composable @ReadOnlyComposable get() = LocalParlorTypography.current
    val spacing: ParlorSpacing
        @Composable @ReadOnlyComposable get() = LocalParlorSpacing.current
    val elevation: ParlorElevation
        @Composable @ReadOnlyComposable get() = LocalParlorElevation.current
    val radii: ParlorRadii
        @Composable @ReadOnlyComposable get() = LocalParlorRadii.current
    val blur: ParlorBlur
        @Composable @ReadOnlyComposable get() = LocalParlorBlur.current
    val motion: ParlorMotion
        @Composable @ReadOnlyComposable get() = LocalParlorMotion.current
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/** Root theme — wraps M3 with cozy-noir defaults. */
@Composable
fun ParlorTheme(
    colors: ParlorColors = CozyNoirPalette,
    typography: ParlorTypography = DefaultParlorTypography,
    spacing: ParlorSpacing = ParlorSpacing(),
    elevation: ParlorElevation = ParlorElevation(),
    radii: ParlorRadii = ParlorRadii(),
    blur: ParlorBlur = ParlorBlur(),
    motion: ParlorMotion = ParlorMotion(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val m3Scheme = darkColorScheme(
        background = colors.surfaceCanvas,
        surface = colors.surfaceElevated,
        surfaceVariant = colors.surfaceHigher,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        primary = colors.accentEmber,
        onPrimary = colors.textOnAccent,
        secondary = colors.accentBrass,
        onSecondary = colors.textPrimary,
        error = colors.semanticDanger,
        onError = colors.textPrimary,
    )

    CompositionLocalProvider(
        LocalParlorColors provides colors,
        LocalParlorTypography provides typography,
        LocalParlorSpacing provides spacing,
        LocalParlorElevation provides elevation,
        LocalParlorRadii provides radii,
        LocalParlorBlur provides blur,
        LocalParlorMotion provides motion,
        LocalReducedMotion provides reducedMotion,
        LocalContentColor provides colors.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content,
        )
    }
}

private val LocalParlorColors = staticCompositionLocalOf<ParlorColors> {
    error("ParlorColors not provided. Wrap your content with ParlorTheme.")
}
private val LocalParlorTypography = staticCompositionLocalOf { DefaultParlorTypography }
private val LocalParlorSpacing = staticCompositionLocalOf { ParlorSpacing() }
private val LocalParlorElevation = staticCompositionLocalOf { ParlorElevation() }
private val LocalParlorRadii = staticCompositionLocalOf { ParlorRadii() }
private val LocalParlorBlur = staticCompositionLocalOf { ParlorBlur() }
private val LocalParlorMotion = staticCompositionLocalOf { ParlorMotion() }
private val LocalReducedMotion = staticCompositionLocalOf { false }

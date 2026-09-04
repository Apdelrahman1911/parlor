package com.parlor.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.parlor.designsystem.motion.ParlorMotion
import com.parlor.designsystem.motion.forReducedMotion
import com.parlor.designsystem.tokens.CozyNoirPalette
import com.parlor.designsystem.tokens.DefaultParlorTypography
import com.parlor.designsystem.tokens.LightCozyNoirPalette
import com.parlor.designsystem.tokens.ParlorBlur
import com.parlor.designsystem.tokens.ParlorBorders
import com.parlor.designsystem.tokens.ParlorColors
import com.parlor.designsystem.tokens.ParlorElevation
import com.parlor.designsystem.tokens.ParlorIconSize
import com.parlor.designsystem.tokens.ParlorRadii
import com.parlor.designsystem.tokens.ParlorSpacing
import com.parlor.designsystem.tokens.ParlorTypography
import com.parlor.designsystem.tokens.isLight
import com.parlor.designsystem.tokens.rememberParlorTypography

/**
 * Parlor's design-system entry point. Wraps Material 3 with our token system
 * so existing M3 components inherit the warm editorial palette while we layer Parlor's
 * custom components on top.
 *
 * Access tokens inside Composable code via `ParlorTheme.colors`, `.typography`,
 * `.spacing`, `.elevation`, `.radii`, `.blur`, `.motion`, `.themeMode`.
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
    val borders: ParlorBorders
        @Composable @ReadOnlyComposable get() = LocalParlorBorders.current
    val iconSize: ParlorIconSize
        @Composable @ReadOnlyComposable get() = LocalParlorIconSize.current
    val motion: ParlorMotion
        @Composable @ReadOnlyComposable get() = LocalParlorMotion.current
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
    val themeMode: ThemeMode
        @Composable @ReadOnlyComposable get() = LocalThemeMode.current
}

/**
 * Root theme. Resolves [themeMode] to a concrete palette and Material 3 color
 * scheme (light or dark), and wires CompositionLocals for all token groups.
 *
 * `themeMode = ThemeMode.System` follows the host OS (via
 * [isSystemInDarkTheme]). `Light` and `Dark` force the corresponding palette.
 *
 * Both light and dark palettes share the default amber accent; a game may
 * override that accent below the root theme without changing semantic colors.
 */
@Composable
fun ParlorTheme(
    themeMode: ThemeMode = ThemeMode.Default,
    darkPalette: ParlorColors = CozyNoirPalette,
    lightPalette: ParlorColors = LightCozyNoirPalette,
    typography: ParlorTypography = rememberParlorTypography(),
    spacing: ParlorSpacing = ParlorSpacing(),
    elevation: ParlorElevation = ParlorElevation(),
    radii: ParlorRadii = ParlorRadii(),
    blur: ParlorBlur = ParlorBlur(),
    borders: ParlorBorders = ParlorBorders(),
    iconSize: ParlorIconSize = ParlorIconSize(),
    motion: ParlorMotion = ParlorMotion(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colors = if (isDark) darkPalette else lightPalette
    val effectiveMotion = motion.forReducedMotion(reducedMotion)

    val m3Scheme = if (colors.isLight) {
        lightColorScheme(
            background = colors.surfaceCanvas,
            surface = colors.surfaceElevated,
            surfaceVariant = colors.surfaceHigher,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.accentEmber,
            onPrimary = colors.textOnAccent,
            secondary = colors.accentBrass,
            onSecondary = colors.textOnAccent,
            error = colors.semanticDanger,
            onError = colors.textOnAccent,
        )
    } else {
        darkColorScheme(
            background = colors.surfaceCanvas,
            surface = colors.surfaceElevated,
            surfaceVariant = colors.surfaceHigher,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.accentEmber,
            onPrimary = colors.textOnAccent,
            secondary = colors.accentBrass,
            onSecondary = colors.textOnAccent,
            error = colors.semanticDanger,
            onError = colors.textOnAccent,
        )
    }

    CompositionLocalProvider(
        LocalParlorColors provides colors,
        LocalParlorTypography provides typography,
        LocalParlorSpacing provides spacing,
        LocalParlorElevation provides elevation,
        LocalParlorRadii provides radii,
        LocalParlorBlur provides blur,
        LocalParlorBorders provides borders,
        LocalParlorIconSize provides iconSize,
        LocalParlorMotion provides effectiveMotion,
        LocalReducedMotion provides reducedMotion,
        LocalThemeMode provides themeMode,
        LocalContentColor provides colors.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content,
        )
    }
}

internal val LocalParlorColors = staticCompositionLocalOf<ParlorColors> {
    error("ParlorColors not provided. Wrap your content with ParlorTheme.")
}
private val LocalParlorTypography = staticCompositionLocalOf { DefaultParlorTypography }
private val LocalParlorSpacing = staticCompositionLocalOf { ParlorSpacing() }
private val LocalParlorElevation = staticCompositionLocalOf { ParlorElevation() }
private val LocalParlorRadii = staticCompositionLocalOf { ParlorRadii() }
private val LocalParlorBlur = staticCompositionLocalOf { ParlorBlur() }
private val LocalParlorBorders = staticCompositionLocalOf { ParlorBorders() }
private val LocalParlorIconSize = staticCompositionLocalOf { ParlorIconSize() }
private val LocalParlorMotion = staticCompositionLocalOf { ParlorMotion() }
private val LocalReducedMotion = staticCompositionLocalOf { false }
private val LocalThemeMode = staticCompositionLocalOf { ThemeMode.Default }

package com.parlor.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.inter
import parlor.shared.design_system.generated.resources.jetbrains_mono

/**
 * Typography tokens — warm editorial direction.
 *
 * Inter for everything except locked-digit timers (JetBrains Mono).
 * Big, confident display weights carry the hierarchy without sacrificing
 * Arabic coverage or large-text resilience.
 *
 * Variable fonts let one .ttf serve every weight.
 */
@Immutable
data class ParlorTypography(
    val displayHero: TextStyle,
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val headingLarge: TextStyle,
    val headingMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    val timerLarge: TextStyle,
    val timerMedium: TextStyle,
    val narration: TextStyle,
)

@Composable
fun rememberParlorTypography(): ParlorTypography {
    val displaySans = displaySansFamily()
    val bodySans = bodySansFamily()
    val timerMono = timerMonoFamily()
    return remember(displaySans, bodySans, timerMono) {
        buildParlorTypography(displaySans, bodySans, timerMono)
    }
}

@Composable
private fun displaySansFamily(): FontFamily = FontFamily(
    Font(Res.font.inter, weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(Res.font.inter, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(Res.font.inter, weight = FontWeight.ExtraBold, style = FontStyle.Normal),
    Font(Res.font.inter, weight = FontWeight.Black, style = FontStyle.Normal),
)

@Composable
private fun bodySansFamily(): FontFamily = FontFamily(
    Font(Res.font.inter, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(Res.font.inter, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(Res.font.inter, weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(Res.font.inter, weight = FontWeight.Bold, style = FontStyle.Normal),
)

@Composable
private fun timerMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono, weight = FontWeight.Light, style = FontStyle.Normal),
    Font(Res.font.jetbrains_mono, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(Res.font.jetbrains_mono, weight = FontWeight.Medium, style = FontStyle.Normal),
)

private fun buildParlorTypography(
    displaySans: FontFamily,
    bodySans: FontFamily,
    timerMono: FontFamily,
): ParlorTypography = ParlorTypography(
    displayHero = TextStyle(
        fontFamily = displaySans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayLarge = TextStyle(
        fontFamily = displaySans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = displaySans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headingLarge = TextStyle(
        fontFamily = displaySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    headingMedium = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.0.sp,
    ),
    labelSmall = TextStyle(
        // Editorial-style eyebrow: small, uppercase-spaced, tight tracking.
        fontFamily = bodySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.8.sp,
    ),
    timerLarge = TextStyle(
        fontFamily = timerMono,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 58.sp,
    ),
    timerMedium = TextStyle(
        fontFamily = timerMono,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    narration = TextStyle(
        // Editorial direction: no italic serif narration. Quote-style
        // body in regular weight.
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.0.sp,
    ),
)

/**
 * System-font fallback for previews / early theme init before the
 * Compose resource runtime is available.
 */
val DefaultParlorTypography: ParlorTypography = buildParlorTypography(
    displaySans = FontFamily.SansSerif,
    bodySans = FontFamily.SansSerif,
    timerMono = FontFamily.Monospace,
)

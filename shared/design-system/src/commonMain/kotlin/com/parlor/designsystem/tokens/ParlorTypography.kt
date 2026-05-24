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
import parlor.shared.design_system.generated.resources.cormorant_garamond
import parlor.shared.design_system.generated.resources.cormorant_garamond_italic
import parlor.shared.design_system.generated.resources.inter
import parlor.shared.design_system.generated.resources.jetbrains_mono

/**
 * Typography tokens per docs/DESIGN_TOKENS.md §2.
 *
 * Fonts (bundled under `composeResources/font/`):
 *  - **Cormorant Garamond** (variable, weight + italic) — display serif.
 *    A high-contrast transitional serif with thin terminals and warm letter
 *    rhythm; carries the cozy-noir "old manor" feel on displayHero / large
 *    titles / italic narration.
 *  - **Inter** (variable, opsz + weight) — humanist body sans.
 *    Modern, generous x-height, optical-size axis tuned for screen reading.
 *    Used for body text, labels, and microcopy.
 *  - **JetBrains Mono** (variable, weight) — monospaced display for timers
 *    where digit width must stay locked.
 *
 * Variable fonts let Skia (and Android's font renderer) pick the exact
 * weight axis value at render time without shipping a separate file per
 * weight. The same .ttf serves Light/Regular/Medium/SemiBold/Bold.
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

/**
 * Build the bundled-font typography. `@Composable` because it loads
 * resources; cached via [remember] so font loading runs once per
 * composition scope.
 */
@Composable
fun rememberParlorTypography(): ParlorTypography {
    val displaySerif = displaySerifFamily()
    val bodySans = bodySansFamily()
    val timerMono = timerMonoFamily()
    return remember(displaySerif, bodySans, timerMono) {
        buildParlorTypography(displaySerif, bodySans, timerMono)
    }
}

@Composable
private fun displaySerifFamily(): FontFamily = FontFamily(
    Font(Res.font.cormorant_garamond, weight = FontWeight.Light, style = FontStyle.Normal),
    Font(Res.font.cormorant_garamond, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(Res.font.cormorant_garamond, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(Res.font.cormorant_garamond, weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(Res.font.cormorant_garamond, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(Res.font.cormorant_garamond_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.cormorant_garamond_italic, weight = FontWeight.Medium, style = FontStyle.Italic),
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
    displaySerif: FontFamily,
    bodySans: FontFamily,
    timerMono: FontFamily,
): ParlorTypography = ParlorTypography(
    displayHero = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayLarge = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headingLarge = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.1.sp,
    ),
    headingMedium = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = bodySans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.6.sp,
    ),
    timerLarge = TextStyle(
        fontFamily = timerMono,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 60.sp,
    ),
    timerMedium = TextStyle(
        fontFamily = timerMono,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    narration = TextStyle(
        fontFamily = displaySerif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * System-font fallback used by previews / theme initialisation before the
 * Compose runtime is available. Production code goes through
 * [rememberParlorTypography] so the bundled Cormorant Garamond / Inter /
 * JetBrains Mono is what users actually see.
 */
val DefaultParlorTypography: ParlorTypography = buildParlorTypography(
    displaySerif = FontFamily.Serif,
    bodySans = FontFamily.SansSerif,
    timerMono = FontFamily.Monospace,
)

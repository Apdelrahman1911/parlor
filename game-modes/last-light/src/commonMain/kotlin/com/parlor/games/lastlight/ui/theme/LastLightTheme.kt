// Adapted from PartyDeck Standard, commit df649e6c896203bdf93130f6497e229757d5da30.
package com.parlor.games.lastlight.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.fraunces_semibold
import com.parlor.games.lastlight.resources.manrope_medium
import com.parlor.games.lastlight.resources.manrope_regular
import com.parlor.games.lastlight.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

/** Intentional card-table colors shared by the shell and game presentation. */
object LastLightColors {
    val Ink = Color(0xFF191526)
    val Surface = Color(0xFF252133)
    val Paper = Color(0xFFF4F0E8)
    val Muted = Color(0xFFBAB5C4)
    val Citron = Color(0xFFD6EF82)
    val Copper = Color(0xFFF16B48)
    val Divider = Color(0xFF575163)
    val Outline = Color(0xFF888190)
    val Error = Color(0xFFFFB4AB)
}

val LocalReduceMotion = staticCompositionLocalOf { false }

private val DeckColorScheme = darkColorScheme(
    primary = LastLightColors.Citron,
    onPrimary = LastLightColors.Ink,
    primaryContainer = Color(0xFF344025),
    onPrimaryContainer = LastLightColors.Citron,
    secondary = LastLightColors.Paper,
    onSecondary = LastLightColors.Ink,
    secondaryContainer = LastLightColors.Surface,
    onSecondaryContainer = LastLightColors.Paper,
    tertiary = LastLightColors.Copper,
    onTertiary = LastLightColors.Ink,
    tertiaryContainer = Color(0xFF492D2D),
    onTertiaryContainer = LastLightColors.Paper,
    background = LastLightColors.Ink,
    onBackground = LastLightColors.Paper,
    surface = LastLightColors.Surface,
    onSurface = LastLightColors.Paper,
    surfaceVariant = LastLightColors.Surface,
    onSurfaceVariant = LastLightColors.Muted,
    outline = LastLightColors.Outline,
    outlineVariant = LastLightColors.Divider,
    error = LastLightColors.Error,
    onError = LastLightColors.Ink,
    errorContainer = Color(0xFF4C272D),
    onErrorContainer = LastLightColors.Paper,
    scrim = LastLightColors.Ink,
)

@Composable
fun LastLightTheme(
    reduceMotion: Boolean = ParlorTheme.reducedMotion,
    content: @Composable () -> Unit,
) {
    // The bundled Latin fonts have no Arabic glyphs. Match Parlor's platform sans-serif
    // fallback for RTL text so Arabic shaping does not depend on a missing font face.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val display = if (rtl) FontFamily.SansSerif else FontFamily(Font(Res.font.fraunces_semibold, FontWeight.SemiBold))
    val body = if (rtl) FontFamily.SansSerif else FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_medium, FontWeight.Medium),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    )
    val typography = remember(display, body, rtl) {
        fun editorial(size: Int, line: Int) = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = size.sp,
            lineHeight = line.sp,
            letterSpacing = if (rtl) 0.sp else (-0.6).sp,
        )
        fun readable(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
            fontFamily = body,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = line.sp,
        )
        Typography(
            displayLarge = editorial(56, 62),
            displayMedium = editorial(48, 54),
            displaySmall = editorial(40, 46),
            headlineLarge = editorial(36, 42),
            headlineMedium = editorial(30, 36),
            headlineSmall = editorial(26, 32),
            titleLarge = readable(22, 30, FontWeight.SemiBold),
            titleMedium = readable(18, 26, FontWeight.SemiBold),
            titleSmall = readable(16, 24, FontWeight.SemiBold),
            bodyLarge = readable(17, 26),
            bodyMedium = readable(16, 24),
            bodySmall = readable(14, 21),
            labelLarge = readable(16, 22, FontWeight.SemiBold),
            labelMedium = readable(14, 20, FontWeight.Medium),
            labelSmall = readable(12, 18, FontWeight.SemiBold),
        )
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = DeckColorScheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}

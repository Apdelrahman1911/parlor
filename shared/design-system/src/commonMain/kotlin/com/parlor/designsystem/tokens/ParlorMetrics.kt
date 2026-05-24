package com.parlor.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing tokens per docs/DESIGN_TOKENS.md §3. 8dp grid with 4dp half-step. */
@Immutable
data class ParlorSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
)

/**
 * Elevation tokens per docs/DESIGN_TOKENS.md §4. Each level pairs a shadow
 * spec with a warm rim glow so cards feel like they're catching candlelight.
 */
@Immutable
data class ParlorElevation(
    val none: ElevationSpec = ElevationSpec(0.dp, 0.dp, 0f, 0f),
    val low: ElevationSpec = ElevationSpec(2.dp, 8.dp, 0.30f, 0.04f),
    val medium: ElevationSpec = ElevationSpec(4.dp, 12.dp, 0.45f, 0.06f),
    val high: ElevationSpec = ElevationSpec(8.dp, 20.dp, 0.50f, 0.10f),
    val dramatic: ElevationSpec = ElevationSpec(16.dp, 40.dp, 0.55f, 0.18f),
)

@Immutable
data class ElevationSpec(
    val yOffset: Dp,
    val blurRadius: Dp,
    val shadowAlpha: Float,
    val warmRimAlpha: Float,
)

@Immutable
data class ParlorRadii(
    val none: Dp = 0.dp,
    val subtle: Dp = 4.dp,
    val card: Dp = 12.dp,
    val elevated: Dp = 20.dp,
    val pill: Dp = 9999.dp,
)

@Immutable
data class ParlorBlur(
    val subtle: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val dramatic: Dp = 32.dp,
)

/**
 * Border-stroke tokens. Lives next to the other metric scales so feature
 * code can pull [ParlorTheme.borders.hairline] etc. without hardcoding
 * `1.dp` literals.
 */
@Immutable
data class ParlorBorders(
    val hairline: Dp = 1.dp,
    val regular: Dp = 1.5.dp,
    val strong: Dp = 2.dp,
)

/**
 * Icon-size tokens. Same purpose as [ParlorBorders] — keep icon sizes off
 * the literal dp scale at call sites.
 */
@Immutable
data class ParlorIconSize(
    val xs: Dp = 12.dp,
    val s: Dp = 16.dp,
    val m: Dp = 20.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val hero: Dp = 64.dp,
)

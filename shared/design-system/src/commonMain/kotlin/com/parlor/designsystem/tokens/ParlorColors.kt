package com.parlor.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens per docs/DESIGN_TOKENS.md §1.
 *
 * Pure black is banned. All surfaces are warm near-blacks. Ember accents
 * layer with alpha for glow. The exposed `ParlorColors` is the contract;
 * concrete values live in [CozyNoirPalette] (Whodunit) and future overlays.
 */
@Immutable
data class ParlorColors(
    val surfaceCanvas: Color,
    val surfaceElevated: Color,
    val surfaceHigher: Color,
    val surfaceInset: Color,

    val accentEmber: Color,
    val accentEmberGlow: Color,
    val accentEmberDeep: Color,
    val accentBrass: Color,
    val accentParchment: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    val textNarration: Color,

    val semanticSuccess: Color,
    val semanticDanger: Color,
    val semanticMuted: Color,

    val borderSubtle: Color,
    val borderElevated: Color,
    val borderGlow: Color,
)

/** Cozy-noir (dark) — the default Whodunit palette. */
val CozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFF0B0807),
    surfaceElevated = Color(0xFF14100D),
    surfaceHigher = Color(0xFF1C1814),
    surfaceInset = Color(0xFF070504),

    accentEmber = Color(0xFFD97A2A),
    accentEmberGlow = Color(0xFFF2A04D),
    accentEmberDeep = Color(0xFF8C4015),
    accentBrass = Color(0xFFB89968),
    accentParchment = Color(0xFFE8DAB6),

    textPrimary = Color(0xFFF2E6CD),
    textSecondary = Color(0xFFB8A98A),
    textTertiary = Color(0xFF6B5B45),
    textOnAccent = Color(0xFF0B0807),
    textNarration = Color(0xFFE8DAB6),

    semanticSuccess = Color(0xFF7A8C5E),
    semanticDanger = Color(0xFFA33D2A),
    semanticMuted = Color(0xFF5C4B3A),

    borderSubtle = Color(0xFF26201A),
    borderElevated = Color(0xFF3A312A),
    borderGlow = Color(0x2ED97A2A),  // ember at ~0.18 alpha (AARRGGBB)
)

/**
 * Cozy-noir (light) — a parchment-and-ember variant. The ember accent stays
 * constant across themes so brand recognition is preserved; surfaces become
 * warm parchment, text becomes deep brown, and the brass moves darker for
 * contrast on light backgrounds.
 *
 * Still cozy. Still noir-adjacent. Just lit by morning light instead of a
 * candle.
 */
val LightCozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFFF5EDD9),
    surfaceElevated = Color(0xFFFAF4E4),
    surfaceHigher = Color(0xFFFFFCF2),
    surfaceInset = Color(0xFFE8DEC4),

    accentEmber = Color(0xFFD97A2A),
    accentEmberGlow = Color(0xFFF2A04D),
    accentEmberDeep = Color(0xFF8C4015),
    accentBrass = Color(0xFF8C7A4A),
    accentParchment = Color(0xFF2D2618),

    textPrimary = Color(0xFF1C1814),
    textSecondary = Color(0xFF5C4B3A),
    textTertiary = Color(0xFF8C7A60),
    textOnAccent = Color(0xFFFFFCF2),
    textNarration = Color(0xFF3A312A),

    semanticSuccess = Color(0xFF5C7A3D),
    semanticDanger = Color(0xFF8C2D1F),
    semanticMuted = Color(0xFFB8A98A),

    borderSubtle = Color(0xFFD6CBB1),
    borderElevated = Color(0xFFB8A98A),
    borderGlow = Color(0x2ED97A2A),  // same ember rim alpha works on light too
)

/** Returns true if the palette is the light variant (used by the theme to pick a Material 3 scheme). */
val ParlorColors.isLight: Boolean
    get() = textPrimary.luminance() > 0.5f

// Local helper to avoid pulling in androidx.core; Color has no public luminance accessor in CMP common,
// but we can approximate with the relative red+green+blue channels.
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

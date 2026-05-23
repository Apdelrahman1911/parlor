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

/** Cozy-noir palette for Whodunit. */
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
    borderGlow = Color(0xD97A2A2E),  // ember at ~0.18 alpha
)

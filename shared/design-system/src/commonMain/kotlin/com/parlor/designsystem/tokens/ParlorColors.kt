package com.parlor.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens per docs/DESIGN_TOKENS.md §1 — reworked for production-quality.
 *
 * Two principles drove this revision:
 *  1. **Distinct surface elevation.** The previous palette had ~5% luminance
 *     steps between canvas → elevated → higher; cards barely stood out on a
 *     phone. The new dark palette widens to ~10–15% steps with a warmer-hue
 *     shift as elevation rises, so card-on-card hierarchy reads at 360 dp.
 *     A new `surfaceHero` carries the most attention-grabbing card on the
 *     screen (room code, dossier, reveal).
 *  2. **Light mode that's actually contrasty.** Text scale deepened so body
 *     text on parchment well clears WCAG AA. Brass borders give cards a
 *     bookbinding edge instead of fading into the background.
 *
 * Ember stays the brand anchor across modes; only the surface family swaps.
 * `borderAccent` (new) lets primary affordances carry a stronger ember rim
 * than the existing subtle `borderGlow`.
 */
@Immutable
data class ParlorColors(
    val surfaceCanvas: Color,
    val surfaceElevated: Color,
    val surfaceHigher: Color,
    val surfaceInset: Color,
    /** Attention-grabbing surface for the hero card on a screen. */
    val surfaceHero: Color,

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
    /** Stronger ember rim for hero cards and primary affordances. */
    val borderAccent: Color,
)

/**
 * Cozy-noir (dark) — candlelight on mahogany. Wider surface stops + warmer
 * hue shift as elevation rises; AA-clean text against every surface tier.
 */
val CozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFF0A0706),
    surfaceElevated = Color(0xFF1A1310),
    surfaceHigher = Color(0xFF261C16),
    surfaceInset = Color(0xFF050302),
    surfaceHero = Color(0xFF2B1F18),

    accentEmber = Color(0xFFD97A2A),
    accentEmberGlow = Color(0xFFF2A04D),
    accentEmberDeep = Color(0xFF8C4015),
    accentBrass = Color(0xFFC8A872),
    accentParchment = Color(0xFFEFE0BC),

    textPrimary = Color(0xFFF5EAD0),
    textSecondary = Color(0xFFC4B596),
    textTertiary = Color(0xFF8C7B5C),
    textOnAccent = Color(0xFF0A0706),
    textNarration = Color(0xFFEDDDB5),

    semanticSuccess = Color(0xFF8FA666),
    semanticDanger = Color(0xFFC4533F),
    semanticMuted = Color(0xFF6B5C45),

    borderSubtle = Color(0xFF2E2620),
    borderElevated = Color(0xFF4A3E33),
    borderGlow = Color(0x33D97A2A),
    borderAccent = Color(0x66D97A2A),
)

/**
 * Cozy-noir (light) — parchment and ember. Surfaces step cream→gold;
 * deeper text + brass borders give cards a bookbinding edge instead of
 * fading into the background.
 *
 * Still cozy. Still noir-adjacent. Just lit by morning light.
 */
val LightCozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFFEFE5CD),
    surfaceElevated = Color(0xFFF7EFDA),
    surfaceHigher = Color(0xFFFDF8EA),
    surfaceInset = Color(0xFFDFD3B8),
    surfaceHero = Color(0xFFFFF5DC),

    accentEmber = Color(0xFFC45F1F),
    accentEmberGlow = Color(0xFFE38845),
    accentEmberDeep = Color(0xFF7A3510),
    accentBrass = Color(0xFF7A6839),
    accentParchment = Color(0xFF2A2316),

    textPrimary = Color(0xFF1A1410),
    textSecondary = Color(0xFF4A3E2E),
    textTertiary = Color(0xFF7A6A50),
    textOnAccent = Color(0xFFFFF8EC),
    textNarration = Color(0xFF332918),

    semanticSuccess = Color(0xFF4D6630),
    semanticDanger = Color(0xFF7A2818),
    semanticMuted = Color(0xFFA89A7C),

    borderSubtle = Color(0xFFC8BC9D),
    borderElevated = Color(0xFFA89A7C),
    borderGlow = Color(0x33C45F1F),
    borderAccent = Color(0x66C45F1F),
)

/** Returns true if the palette is the light variant (used by the theme to pick a Material 3 scheme). */
val ParlorColors.isLight: Boolean
    get() = surfaceCanvas.luminance() > 0.5f

// Local helper to avoid pulling in androidx.core; Color has no public luminance accessor in CMP common,
// but we can approximate with the relative red+green+blue channels.
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

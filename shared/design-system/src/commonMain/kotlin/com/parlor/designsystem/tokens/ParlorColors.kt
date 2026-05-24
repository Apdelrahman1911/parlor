package com.parlor.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens — Modern Dark Editorial direction.
 *
 * Premium app-like aesthetic: charcoal surfaces, generous whitespace,
 * one bold accent (coral). No textures, no gradients, no warm wood
 * tones. Type and hierarchy carry the visual weight, not decoration.
 *
 * The data-class shape is intentionally backwards-compatible with the
 * prior `cozy-noir` token names — `accentEmber` / `accentBrass` /
 * `accentParchment` are still here but their values map onto coral and
 * neutrals so the rest of the codebase keeps compiling while every
 * screen automatically picks up the new look.
 *
 * Two utility tokens supplement the named set:
 *  - [transparent] — explicit transparent so feature code never needs
 *    `Color.Transparent`.
 *  - [overlayScrim] — modal/dim-the-world overlay so feature code
 *    never needs `Color.Black.copy(alpha=...)`.
 */
@Immutable
data class ParlorColors(
    val surfaceCanvas: Color,
    val surfaceElevated: Color,
    val surfaceHigher: Color,
    val surfaceInset: Color,
    /** Attention-grabbing surface for the hero card on a screen. */
    val surfaceHero: Color,

    /** Primary brand accent. Coral in the editorial palette. */
    val accentEmber: Color,
    /** Lighter accent — used for subtle highlights / focus glows. */
    val accentEmberGlow: Color,
    /** Deeper accent — used for pressed-state accents and rare emphasis. */
    val accentEmberDeep: Color,
    /** Neutral mid-grey, retained for back-compat. Editorial: a muted grey. */
    val accentBrass: Color,
    /** High-contrast surface highlight. Editorial: pure white-tone on dark; deep ink on light. */
    val accentParchment: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    /** Quoted / narration body — same as textSecondary in editorial direction. */
    val textNarration: Color,

    val semanticSuccess: Color,
    val semanticDanger: Color,
    val semanticMuted: Color,

    val borderSubtle: Color,
    val borderElevated: Color,
    /** Soft accent glow — used sparingly for focus rings, not decoration. */
    val borderGlow: Color,
    /** Strong accent rim — used on hero affordances. */
    val borderAccent: Color,

    /** Explicit fully-transparent token. Use instead of `Color.Transparent`. */
    val transparent: Color,
    /** Modal overlay scrim. Use instead of `Color.Black.copy(alpha=...)`. */
    val overlayScrim: Color,
    /** Cover-screen body. Pure black in both modes — used by the "hide the dossier" beats. */
    val coverScreen: Color,
    /** Light text colour readable on [coverScreen]. Identical across light + dark palettes. */
    val coverScreenTextPrimary: Color,
    /** Secondary text on [coverScreen]. */
    val coverScreenTextSecondary: Color,
    /** Tertiary / hint text on [coverScreen]. */
    val coverScreenTextTertiary: Color,
)

/**
 * Dark mode — charcoal canvas, coral accent, sharp whites.
 */
val CozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFF0B0B0F),
    surfaceElevated = Color(0xFF16161C),
    surfaceHigher = Color(0xFF1F1F26),
    surfaceInset = Color(0xFF050507),
    surfaceHero = Color(0xFF1A1A23),

    accentEmber = Color(0xFF7C5CFF),
    accentEmberGlow = Color(0xFF9477FF),
    accentEmberDeep = Color(0xFF5A3EE0),
    accentBrass = Color(0xFF8A8A92),
    accentParchment = Color(0xFFFAFAFA),

    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFB6B6BE),
    textTertiary = Color(0xFF7A7A82),
    textOnAccent = Color(0xFFFFFFFF),
    textNarration = Color(0xFFB6B6BE),

    semanticSuccess = Color(0xFF3FB66E),
    semanticDanger = Color(0xFFFF4438),
    semanticMuted = Color(0xFF44444E),

    borderSubtle = Color(0xFF20202A),
    borderElevated = Color(0xFF2A2A35),
    borderGlow = Color(0x337C5CFF),
    borderAccent = Color(0xFF7C5CFF),

    transparent = Color(0x00000000),
    overlayScrim = Color(0xD9000000),
    coverScreen = Color(0xFF000000),
    coverScreenTextPrimary = Color(0xFFFAFAFA),
    coverScreenTextSecondary = Color(0xFFB6B6BE),
    coverScreenTextTertiary = Color(0xFF7A7A82),
)

/**
 * Light mode — pure white canvas, deeper coral accent for legibility.
 */
val LightCozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF4F4F7),
    surfaceHigher = Color(0xFFE9E9EE),
    surfaceInset = Color(0xFFFAFAFA),
    surfaceHero = Color(0xFFF0F0F4),

    accentEmber = Color(0xFF5A3EE0),
    accentEmberGlow = Color(0xFF6F4BE8),
    accentEmberDeep = Color(0xFF3F25B5),
    accentBrass = Color(0xFF6E6E78),
    accentParchment = Color(0xFF0B0B0F),

    textPrimary = Color(0xFF0B0B0F),
    textSecondary = Color(0xFF4A4A52),
    textTertiary = Color(0xFF8A8A92),
    textOnAccent = Color(0xFFFFFFFF),
    textNarration = Color(0xFF4A4A52),

    semanticSuccess = Color(0xFF1E8E4F),
    semanticDanger = Color(0xFFD93022),
    semanticMuted = Color(0xFFC8C8D0),

    borderSubtle = Color(0xFFE2E2E8),
    borderElevated = Color(0xFFDADAE0),
    borderGlow = Color(0x335A3EE0),
    borderAccent = Color(0xFF5A3EE0),

    transparent = Color(0x00000000),
    overlayScrim = Color(0x99000000),
    coverScreen = Color(0xFF000000),
    coverScreenTextPrimary = Color(0xFFFAFAFA),
    coverScreenTextSecondary = Color(0xFFB6B6BE),
    coverScreenTextTertiary = Color(0xFF7A7A82),
)

/** True iff the palette is the light variant (used by the theme to pick a Material 3 scheme). */
val ParlorColors.isLight: Boolean
    get() = surfaceCanvas.luminance() > 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

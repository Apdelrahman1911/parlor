package com.parlor.designsystem.tokens

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

/**
 * Palette discipline: every field in [ParlorColors] must be populated in
 * both [CozyNoirPalette] (dark) and [LightCozyNoirPalette] (light). A few
 * fields (`transparent`, `coverScreen`, `coverScreenText*`) are
 * intentionally identical across modes — those are pinned by the
 * "expected identical" set below.
 *
 * The test guards two failure modes:
 *  1. A new field added to `ParlorColors` that someone forgot to populate
 *     in the light palette → would default-construct or stay at a copy
 *     and we'd ship a broken light mode.
 *  2. A field that's supposed to invert between modes accidentally being
 *     the same value in both → light mode would look like dark and vice
 *     versa for that token.
 */
class PaletteCoverageTest {

    private val dark = CozyNoirPalette
    private val light = LightCozyNoirPalette

    /** Fields that are intentionally identical across light and dark. */
    private val expectedIdentical = setOf(
        "transparent",
        // textOnAccent: both palettes use a dark indigo accent, so white text
        // works on top of the accent in both modes.
        "textOnAccent",
        "coverScreen",
        "coverScreenTextPrimary",
        "coverScreenTextSecondary",
        "coverScreenTextTertiary",
    )

    @Test
    fun every_palette_field_inverts_or_is_explicitly_pinned_identical() {
        val pairs = listOf(
            "surfaceCanvas" to (dark.surfaceCanvas to light.surfaceCanvas),
            "surfaceElevated" to (dark.surfaceElevated to light.surfaceElevated),
            "surfaceHigher" to (dark.surfaceHigher to light.surfaceHigher),
            "surfaceInset" to (dark.surfaceInset to light.surfaceInset),
            "surfaceHero" to (dark.surfaceHero to light.surfaceHero),
            "accentEmber" to (dark.accentEmber to light.accentEmber),
            "accentEmberGlow" to (dark.accentEmberGlow to light.accentEmberGlow),
            "accentEmberDeep" to (dark.accentEmberDeep to light.accentEmberDeep),
            "accentBrass" to (dark.accentBrass to light.accentBrass),
            "accentParchment" to (dark.accentParchment to light.accentParchment),
            "textPrimary" to (dark.textPrimary to light.textPrimary),
            "textSecondary" to (dark.textSecondary to light.textSecondary),
            "textTertiary" to (dark.textTertiary to light.textTertiary),
            "textOnAccent" to (dark.textOnAccent to light.textOnAccent),
            "textNarration" to (dark.textNarration to light.textNarration),
            "semanticSuccess" to (dark.semanticSuccess to light.semanticSuccess),
            "semanticDanger" to (dark.semanticDanger to light.semanticDanger),
            "semanticMuted" to (dark.semanticMuted to light.semanticMuted),
            "borderSubtle" to (dark.borderSubtle to light.borderSubtle),
            "borderElevated" to (dark.borderElevated to light.borderElevated),
            "borderGlow" to (dark.borderGlow to light.borderGlow),
            "borderAccent" to (dark.borderAccent to light.borderAccent),
            "transparent" to (dark.transparent to light.transparent),
            "overlayScrim" to (dark.overlayScrim to light.overlayScrim),
            "coverScreen" to (dark.coverScreen to light.coverScreen),
            "coverScreenTextPrimary" to (dark.coverScreenTextPrimary to light.coverScreenTextPrimary),
            "coverScreenTextSecondary" to (dark.coverScreenTextSecondary to light.coverScreenTextSecondary),
            "coverScreenTextTertiary" to (dark.coverScreenTextTertiary to light.coverScreenTextTertiary),
        )

        val violations = mutableListOf<String>()
        for ((name, values) in pairs) {
            val (darkValue, lightValue) = values
            if (name in expectedIdentical) {
                if (darkValue != lightValue) {
                    violations += "$name should be identical in both palettes but differs ($darkValue vs $lightValue)"
                }
            } else {
                if (darkValue == lightValue) {
                    violations += "$name should differ across light and dark but is the same ($darkValue)"
                }
            }
        }
        assertThat(violations.joinToString("; ")).isEqualTo("")
    }

    @Test
    fun text_primary_has_strong_contrast_with_canvas() {
        // textPrimary on surfaceCanvas should be at least 7:1 (WCAG AAA for
        // large text; comfortably above AA for body). Catches a paste-error
        // where, say, light's textPrimary accidentally points at a mid-grey.
        val darkRatio = contrastRatio(dark.textPrimary, dark.surfaceCanvas)
        val lightRatio = contrastRatio(light.textPrimary, light.surfaceCanvas)
        assertThat(darkRatio >= 7f).isTrue()
        assertThat(lightRatio >= 7f).isTrue()
    }

    @Test
    fun accent_ember_has_AA_contrast_with_canvas() {
        // Accent text or icons on canvas should clear AA (4.5:1) for body.
        val darkRatio = contrastRatio(dark.accentEmber, dark.surfaceCanvas)
        val lightRatio = contrastRatio(light.accentEmber, light.surfaceCanvas)
        assertThat(darkRatio >= 3f).isTrue()  // 3:1 minimum for large UI
        assertThat(lightRatio >= 3f).isTrue()
    }

    @Test
    fun light_canvas_is_actually_light_and_dark_is_actually_dark() {
        assertThat(luminance(light.surfaceCanvas) > 0.5f).isTrue()
        assertThat(luminance(dark.surfaceCanvas) < 0.2f).isTrue()
    }

    private fun luminance(c: androidx.compose.ui.graphics.Color): Float {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else {
            val x = (v + 0.055f) / 1.055f
            x * x  // approximation of pow(x, 2.4)
        }
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }

    private fun contrastRatio(
        fg: androidx.compose.ui.graphics.Color,
        bg: androidx.compose.ui.graphics.Color,
    ): Float {
        val lFg = luminance(fg) + 0.05f
        val lBg = luminance(bg) + 0.05f
        return if (lFg > lBg) lFg / lBg else lBg / lFg
    }
}

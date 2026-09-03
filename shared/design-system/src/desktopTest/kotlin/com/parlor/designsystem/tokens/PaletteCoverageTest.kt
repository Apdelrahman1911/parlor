package com.parlor.designsystem.tokens

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.parlor.designsystem.theme.ParlorAccent
import com.parlor.designsystem.theme.withAccent
import kotlin.math.pow
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
        "coverScreen",
        "coverScreenTextPrimary",
        "coverScreenTextSecondary",
        "coverScreenTextTertiary",
        "surfacePaper",
        "textOnPaper",
        "textOnPaperSecondary",
    )

    @Test
    fun every_palette_field_inverts_or_is_explicitly_pinned_identical() {
        val pairs = listOf(
            "surfaceCanvas" to (dark.surfaceCanvas to light.surfaceCanvas),
            "surfaceElevated" to (dark.surfaceElevated to light.surfaceElevated),
            "surfaceHigher" to (dark.surfaceHigher to light.surfaceHigher),
            "surfaceInset" to (dark.surfaceInset to light.surfaceInset),
            "surfaceHero" to (dark.surfaceHero to light.surfaceHero),
            "surfacePaper" to (dark.surfacePaper to light.surfacePaper),
            "textOnPaper" to (dark.textOnPaper to light.textOnPaper),
            "textOnPaperSecondary" to (
                dark.textOnPaperSecondary to light.textOnPaperSecondary
            ),
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
        assertThat(darkRatio >= AA_NORMAL_TEXT_MINIMUM).isTrue()
        assertThat(lightRatio >= AA_NORMAL_TEXT_MINIMUM).isTrue()
    }

    @Test
    fun evidence_paper_text_pairs_meet_AA() {
        listOf(dark, light).forEach { palette ->
            assertThat(contrastRatio(palette.textOnPaper, palette.surfacePaper) >= 7f).isTrue()
            assertThat(
                contrastRatio(palette.textOnPaperSecondary, palette.surfacePaper) >=
                    AA_NORMAL_TEXT_MINIMUM,
            ).isTrue()
        }
    }

    @Test
    fun text_hierarchy_remains_readable_on_standard_surfaces() {
        listOf(dark, light).forEach { palette ->
            val surfaces = listOf(
                palette.surfaceCanvas,
                palette.surfaceElevated,
                palette.surfaceHigher,
                palette.surfaceInset,
                palette.surfaceHero,
            )
            surfaces.forEach { surface ->
                assertThat(contrastRatio(palette.textPrimary, surface) >= 7f).isTrue()
                assertThat(
                    contrastRatio(palette.textSecondary, surface) >= AA_NORMAL_TEXT_MINIMUM,
                ).isTrue()
                assertThat(
                    contrastRatio(palette.textTertiary, surface) >= AA_NORMAL_TEXT_MINIMUM,
                ).isTrue()
            }
        }
    }

    @Test
    fun active_normal_text_pairs_used_by_design_system_components_meet_AA() {
        val violations = mutableListOf<String>()
        listOf("dark" to dark, "light" to light).forEach { (name, palette) ->
            val activePairs = listOf(
                "primary button" to (palette.textOnAccent to palette.accentEmber),
                "pressed primary button" to (
                    palette.textOnAccent to palette.accentEmberGlow
                        .copy(alpha = PRIMARY_BUTTON_PRESSED_TINT_ALPHA)
                        .compositedOver(palette.accentEmber)
                ),
                "destructive button" to (palette.textOnAccent to palette.semanticDanger),
                "offline banner" to (palette.textOnAccent to palette.accentBrass),
                "inactive bottom tab" to (palette.textTertiary to palette.surfaceCanvas),
                "info toast" to (palette.accentBrass to palette.surfaceElevated),
                "success toast" to (palette.semanticSuccess to palette.surfaceElevated),
                "warning toast" to (palette.accentEmber to palette.surfaceElevated),
                "danger toast" to (palette.semanticDanger to palette.surfaceElevated),
            )
            activePairs.forEach { (callSite, colors) ->
                val ratio = contrastRatio(colors.first, colors.second)
                if (ratio < AA_NORMAL_TEXT_MINIMUM) {
                    violations += "$name $callSite has $ratio:1 contrast"
                }
            }
        }

        assertThat(violations.joinToString("; ")).isEqualTo("")
    }

    @Test
    fun every_game_accent_meets_active_text_contrast_contracts() {
        val violations = mutableListOf<String>()
        listOf("dark" to dark, "light" to light).forEach { (mode, palette) ->
            ParlorAccent.entries.forEach { accent ->
                val colors = palette.withAccent(accent)
                val activePairs = listOf(
                    "accent on canvas" to (colors.accentEmber to colors.surfaceCanvas),
                    "accent on elevated surface" to (
                        colors.accentEmber to colors.surfaceElevated
                    ),
                    "accent on hero surface" to (colors.accentEmber to colors.surfaceHero),
                    "button text" to (colors.textOnAccent to colors.accentEmber),
                    "pressed button text" to (
                        colors.textOnAccent to colors.accentEmberGlow
                            .copy(alpha = PRIMARY_BUTTON_PRESSED_TINT_ALPHA)
                            .compositedOver(colors.accentEmber)
                    ),
                )
                activePairs.forEach { (callSite, pair) ->
                    val ratio = contrastRatio(pair.first, pair.second)
                    if (ratio < AA_NORMAL_TEXT_MINIMUM) {
                        violations += "$mode $accent $callSite has $ratio:1 contrast"
                    }
                }
            }
        }

        assertThat(violations.joinToString("; ")).isEqualTo("")
    }

    @Test
    fun light_canvas_is_actually_light_and_dark_is_actually_dark() {
        assertThat(luminance(light.surfaceCanvas) > 0.5f).isTrue()
        assertThat(luminance(dark.surfaceCanvas) < 0.2f).isTrue()
    }

    private fun luminance(c: androidx.compose.ui.graphics.Color): Float {
        fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else {
            val x = (v + 0.055f) / 1.055f
            x.toDouble().pow(2.4).toFloat()
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

    /** Returns the opaque color produced by drawing this color over [background]. */
    private fun androidx.compose.ui.graphics.Color.compositedOver(
        background: androidx.compose.ui.graphics.Color,
    ): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
        red = red * alpha + background.red * (1f - alpha),
        green = green * alpha + background.green * (1f - alpha),
        blue = blue * alpha + background.blue * (1f - alpha),
        alpha = 1f,
    )

    private companion object {
        const val AA_NORMAL_TEXT_MINIMUM = 4.5f
        const val PRIMARY_BUTTON_PRESSED_TINT_ALPHA = 0.35f
    }
}

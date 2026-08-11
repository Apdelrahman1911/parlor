package com.parlor.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Motion tokens per docs/DESIGN_TOKENS.md §7. Named timings and easings —
 * components use these instead of magic milliseconds.
 *
 * [forReducedMotion] derives the effective tokens installed by [ParlorTheme].
 * Reduced motion retains only a short fade duration and disables continuous
 * cycles; components must also replace spatial/continuous motion with an
 * equivalent non-moving presentation.
 */
@Immutable
data class ParlorMotion(
    val durationFast: Int = 180,
    val durationMedium: Int = 320,
    val durationSlow: Int = 480,
    val durationTheatrical: Int = 800,
    val durationEmberCycle: Int = 2400,

    val easingStandard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f),
    val easingTheatrical: Easing = CubicBezierEasing(0.2f, 0.6f, 0.1f, 1.0f),
    val easingDeflate: Easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f),
)

/** App preference and platform accessibility preference are both authoritative. */
fun shouldReduceMotion(appPreference: Boolean, systemPreference: Boolean): Boolean =
    appPreference || systemPreference

/** Returns the effective motion contract for one composition. */
fun ParlorMotion.forReducedMotion(enabled: Boolean): ParlorMotion =
    if (!enabled) {
        this
    } else {
        copy(
            durationFast = REDUCED_FADE_MILLIS,
            durationMedium = REDUCED_FADE_MILLIS,
            durationSlow = REDUCED_FADE_MILLIS,
            durationTheatrical = REDUCED_FADE_MILLIS,
            durationEmberCycle = 0,
            easingTheatrical = easingStandard,
            easingDeflate = easingStandard,
        )
    }

private const val REDUCED_FADE_MILLIS: Int = 120

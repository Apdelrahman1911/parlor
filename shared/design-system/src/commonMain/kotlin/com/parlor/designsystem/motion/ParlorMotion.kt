package com.parlor.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Motion tokens per docs/DESIGN_TOKENS.md §7. Named timings and easings —
 * components use these instead of magic milliseconds.
 *
 * Reduce-motion behavior is applied by [ParlorTheme] downstream: when the
 * preference is on, "theatrical" collapses to "medium" with the standard easing.
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

package com.parlor.designsystem.motion

import androidx.compose.runtime.Composable

/**
 * Observes the host platform's current reduce/remove-motion accessibility
 * preference. The app-level preference is combined with this value through
 * [shouldReduceMotion], so users may request less motion but cannot override an
 * accessibility reduction imposed by the operating system.
 */
@Composable
expect fun rememberSystemReducedMotion(): Boolean

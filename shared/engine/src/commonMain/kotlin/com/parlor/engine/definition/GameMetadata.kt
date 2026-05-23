package com.parlor.engine.definition

import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange

/**
 * Display-facing metadata for a registered game module. Used by the Parlor shell
 * to render the All Games grid and the Game Details screen.
 */
data class GameMetadata(
    val displayName: UiText,
    val tagline: UiText,
    val estimatedDuration: DurationRange,
    val themeOverlayId: String,
)

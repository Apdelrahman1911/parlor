package com.parlor.engine.definition

import com.parlor.core.ids.ModeId
import com.parlor.core.localization.UiText
import com.parlor.core.time.DurationRange

/**
 * A play variant inside a game module (e.g., Whodunit's Classic Vote vs Elimination).
 *
 * Distinct from `GameDefinition` — a module declares one or more `GameMode`s.
 * Validation: a case's `supportedPlayerCounts` must intersect with the chosen
 * mode's `supportedPlayerCounts` at session start.
 */
interface GameMode {
    val id: ModeId
    val displayName: UiText
    val description: UiText
    val supportedPlayerCounts: IntRange
    val estimatedDuration: DurationRange
}

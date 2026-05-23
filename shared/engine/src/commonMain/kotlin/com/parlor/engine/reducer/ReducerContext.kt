package com.parlor.engine.reducer

import com.parlor.core.random.RandomSource
import com.parlor.core.time.Clock

/**
 * Read-only context passed into the reducer. Allows pure functions to read
 * time and draw randomness without violating purity (the inputs are explicit).
 *
 * Game modules may extend with their own context — e.g., access to validated
 * case content — by carrying it inside this object.
 */
interface ReducerContext {
    val clock: Clock
    val random: RandomSource
}

/** Default implementation; modules can wrap with additional fields. */
data class DefaultReducerContext(
    override val clock: Clock,
    override val random: RandomSource,
) : ReducerContext

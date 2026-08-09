package com.parlor.games.whodunit.domain.reducer

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.random.RandomSource
import com.parlor.core.time.Clock
import com.parlor.engine.reducer.ReducerContext
import com.parlor.games.whodunit.content.WhodunitCase

/**
 * Whodunit-specific reducer context — carries the validated case content the
 * reducer needs to look up characters, clue pools, and reveal narratives.
 *
 * The session controller constructs this once at session start; the reducer
 * receives it on every reduce() call.
 */
data class WhodunitReducerContext(
    override val clock: Clock,
    override val random: RandomSource,
    val case: ValidatedCase<WhodunitCase>,
) : ReducerContext {
    val payload: WhodunitCase get() = case.payload
}

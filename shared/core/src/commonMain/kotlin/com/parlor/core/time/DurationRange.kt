package com.parlor.core.time

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * A closed range of durations. Used for content metadata (`estimatedDuration`)
 * and engine-level configurables like mode duration hints.
 */
@Serializable
data class DurationRange(val min: Duration, val max: Duration) {
    init {
        require(min <= max) { "DurationRange min ($min) must be <= max ($max)" }
    }
    operator fun contains(value: Duration): Boolean = value in min..max
}

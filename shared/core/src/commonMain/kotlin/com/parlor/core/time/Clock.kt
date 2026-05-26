package com.parlor.core.time

import kotlinx.datetime.Instant

/**
 * Time abstraction. The engine takes a Clock via constructor parameter so
 * reducers and timers can be tested deterministically.
 *
 * Production binding: [SystemClock]. Tests pass a [FakeClock].
 */
interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = kotlin.time.Clock.System.now()
}

/**
 * Mutable test clock. Advance time explicitly in tests rather than relying on
 * real-world delays.
 */
class FakeClock(private var current: Instant) : Clock {
    override fun now(): Instant = current
    fun advance(byMillis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + byMillis)
    }
    fun set(to: Instant) { current = to }
}

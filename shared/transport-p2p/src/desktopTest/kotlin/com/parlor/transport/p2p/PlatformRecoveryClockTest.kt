package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformRecoveryClockTest {
    @Test
    fun platform_elapsed_clock_is_readable_and_nondecreasing() {
        var previous = platformRecoveryElapsedMillis()
        assertTrue(previous >= 0L, "The native clock must be readable")
        repeat(32) {
            val observed = platformRecoveryElapsedMillis()
            assertTrue(observed >= previous, "The native clock must not roll back")
            previous = observed
        }
    }
}

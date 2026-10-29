package com.parlor.transport.p2p

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.clock_gettime_nsec_np

private const val NANOS_PER_MILLISECOND = 1_000_000uL

/** Apple's RAW monotonic clock wraps mach_continuous_time, including device sleep. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformRecoveryElapsedMillis(): Long {
    val nanos = clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.toUInt())
    // The documented error value cannot become a renewable zero-origin budget.
    return if (nanos == 0uL) -1L else (nanos / NANOS_PER_MILLISECOND).toLong()
}

package com.parlor.transport.p2p

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.darwin.mach_continuous_time
import platform.darwin.mach_timebase_info
import platform.darwin.mach_timebase_info_data_t

private const val NANOS_PER_MILLISECOND = 1_000_000.0

@OptIn(ExperimentalForeignApi::class)
private val continuousTickMillis = memScoped {
    val info = alloc<mach_timebase_info_data_t>()
    check(mach_timebase_info(info.ptr) == 0)
    check(info.numer > 0u && info.denom > 0u)
    info.numer.toDouble() / info.denom.toDouble() / NANOS_PER_MILLISECOND
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformRecoveryElapsedMillis(): Long =
    (mach_continuous_time().toDouble() * continuousTickMillis).toLong()

package com.parlor.transport.p2p

import java.util.concurrent.TimeUnit

private val recoveryClockOrigin = System.nanoTime()

internal actual fun platformRecoveryElapsedMillis(): Long =
    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - recoveryClockOrigin)

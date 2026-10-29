package com.parlor.transport.p2p

import android.os.SystemClock

internal actual fun platformRecoveryElapsedMillis(): Long = SystemClock.elapsedRealtime()

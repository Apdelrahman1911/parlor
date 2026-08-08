package com.parlor.transport.p2p

import android.util.Log

internal actual fun platformP2pDiagnosticWriter(): P2pDiagnosticWriter =
    P2pDiagnosticWriter { line -> Log.i("ParlorP2p", line) }

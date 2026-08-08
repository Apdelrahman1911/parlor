package com.parlor.transport.p2p

import platform.Foundation.NSLog

internal actual fun platformP2pDiagnosticWriter(): P2pDiagnosticWriter =
    P2pDiagnosticWriter { line -> NSLog("ParlorP2p $line") }

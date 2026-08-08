package com.parlor.transport.p2p

internal actual fun platformP2pDiagnosticWriter(): P2pDiagnosticWriter =
    P2pDiagnosticWriter { line -> println("ParlorP2p $line") }

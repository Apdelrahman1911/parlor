package com.parlor.transport.p2p

import dev.p2pkit.transport.lan.IosLanDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import platform.Foundation.NSLog
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

internal actual fun platformP2pDiagnosticWriter(): P2pDiagnosticWriter =
    P2pDiagnosticWriter { line -> NSLog("ParlorP2p $line") }

@OptIn(ExperimentalNativeApi::class)
internal fun observeNativeLanStates(scope: CoroutineScope, diagnostics: P2pDiagnostics) {
    if (!Platform.isDebugBinary) return
    // One app-scope observer, never one per kit/retry. Do not enable P2pKit's
    // raw console mirror or history. Only allowlisted states enter our bounded
    // recorder; identifiers, TXT, addresses, and frame traces are discarded.
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        IosLanDebug.events.collect { line ->
            nativeLanStateDiagnostic(line)?.let(diagnostics::record)
        }
    }
}

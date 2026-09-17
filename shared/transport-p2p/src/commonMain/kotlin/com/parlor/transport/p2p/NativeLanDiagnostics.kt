package com.parlor.transport.p2p

private const val MAX_NATIVE_STATE_LINE_LENGTH = 96
private val nativeStateLine = Regex(
    "\\[[0-9]{1,6}]\\[conn] state-changed -> " +
        "(preparing|waiting|ready|failed|cancelled)(?: errCode=(-?[0-9]{1,6}))?",
)

/**
 * Accept only rc3's fixed native-connection state record, never general LAN
 * tracing (which can contain TXT, names, endpoints, or payload information).
 * The raw input is neither retained nor forwarded to any logger.
 */
internal fun nativeLanStateDiagnostic(line: String): P2pDiagnosticEvent? {
    if (line.length > MAX_NATIVE_STATE_LINE_LENGTH) return null
    val match = nativeStateLine.matchEntire(line) ?: return null
    val state = match.groupValues[1]
    val event = when (state) {
        "preparing" -> P2pDiagnosticEventName.LAN_CONNECTION_PREPARING
        "waiting" -> P2pDiagnosticEventName.LAN_CONNECTION_WAITING
        "ready" -> P2pDiagnosticEventName.LAN_CONNECTION_READY
        "failed" -> P2pDiagnosticEventName.LAN_CONNECTION_FAILED
        "cancelled" -> P2pDiagnosticEventName.LAN_CONNECTION_CANCELLED
        else -> return null
    }
    val result = when (state) {
        "ready" -> P2pDiagnosticResult.SUCCESS
        "failed" -> P2pDiagnosticResult.FAILURE
        "cancelled" -> P2pDiagnosticResult.CANCELLED
        else -> P2pDiagnosticResult.NONE
    }
    return P2pDiagnosticEvent(
        name = event,
        result = result,
        reason = nativeErrorCategory(match.groupValues[2]),
    )
}

// These are diagnostic buckets, not evidence for a permission-gate decision:
// rc3 exposes the native error code here but not its error domain.
private fun nativeErrorCategory(code: String): P2pDiagnosticReason = when (code) {
    "", "0" -> P2pDiagnosticReason.NONE
    "1", "13", "-65570" -> P2pDiagnosticReason.NATIVE_PERMISSION
    "50", "51", "65" -> P2pDiagnosticReason.NATIVE_NO_ROUTE
    "61" -> P2pDiagnosticReason.NATIVE_REFUSED
    "60", "-65568" -> P2pDiagnosticReason.NATIVE_TIMEOUT
    "-65537", "-65538", "-65540", "-65554", "-65563", "-65565" ->
        P2pDiagnosticReason.NATIVE_DNS
    else -> P2pDiagnosticReason.TRANSPORT
}

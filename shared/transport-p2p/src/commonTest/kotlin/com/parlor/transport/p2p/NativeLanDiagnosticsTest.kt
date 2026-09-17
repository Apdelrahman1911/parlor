package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeLanDiagnosticsTest {
    @Test
    fun native_tcp_state_is_distinct_from_secure_session_establishment() {
        val ready = assertNotNull(nativeLanStateDiagnostic("[123][conn] state-changed -> ready"))
        assertEquals(P2pDiagnosticEventName.LAN_CONNECTION_READY, ready.name)
        assertEquals(P2pDiagnosticResult.SUCCESS, ready.result)
        assertEquals(P2pDiagnosticReason.NONE, ready.reason)
        assertEquals(P2pDiagnosticRole.NONE, ready.role)
    }

    @Test
    fun native_failure_codes_become_closed_categories_not_raw_error_text() {
        val waiting = assertNotNull(
            nativeLanStateDiagnostic("[123][conn] state-changed -> waiting errCode=65"),
        )
        assertEquals(P2pDiagnosticEventName.LAN_CONNECTION_WAITING, waiting.name)
        assertEquals(P2pDiagnosticReason.NATIVE_NO_ROUTE, waiting.reason)
        val unknown = assertNotNull(
            nativeLanStateDiagnostic("[123][conn] state-changed -> failed errCode=99999"),
        )
        assertEquals(P2pDiagnosticReason.TRANSPORT, unknown.reason)
        val exported = P2pDiagnosticRecord(1L, 0L, unknown).exportLine()
        assertFalse("99999" in exported)
        assertTrue("event=lan_connection_failed" in exported)
    }

    @Test
    fun peer_controlled_text_and_general_transport_traces_are_not_recorded() {
        listOf(
            "[123][browse] emitPeer: txt={name=private-player, pid=private-peer}",
            "[123][connect] begin peer=private-peer name=private-player",
            "[123][conn] read: flow collector started",
            "[123][conn] write(64): nw_connection_send",
            "[123][conn] state-changed -> ready private-player",
            "private-player [123][conn] state-changed -> ready",
            "[123][conn] state-changed -> ready\nprivate-player",
            "[123][conn] state-changed -> waiting errCode=private-player",
            "[123][conn] state-changed -> failed errCode=9999999",
            "x".repeat(10_000),
        ).forEach { assertNull(nativeLanStateDiagnostic(it)) }
    }
}

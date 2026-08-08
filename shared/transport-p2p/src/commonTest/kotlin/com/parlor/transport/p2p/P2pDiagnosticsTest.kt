package com.parlor.transport.p2p

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class P2pDiagnosticsTest {

    @Test
    fun sustained_flood_retains_only_the_fixed_ring_capacity() = runTest {
        val diagnostics = BoundedP2pDiagnostics(
            scope = backgroundScope,
            writer = P2pDiagnosticWriter {},
            capacity = 256,
            outputIntervalMillis = 100L,
        )

        repeat(10_000) {
            diagnostics.record(
                P2pDiagnosticEvent(P2pDiagnosticEventName.COMMAND_RECEIVED),
            )
        }

        val records = diagnostics.snapshot()
        assertEquals(256, records.size)
        assertEquals(9_745L, records.first().sequence)
        assertEquals(10_000L, records.last().sequence)
        assertEquals((9_745L..10_000L).toList(), records.map { it.sequence })
    }

    @Test
    fun platform_output_has_a_one_record_backlog_and_is_rate_limited() = runTest {
        val lines = mutableListOf<String>()
        val diagnostics = BoundedP2pDiagnostics(
            scope = backgroundScope,
            writer = P2pDiagnosticWriter(lines::add),
            outputIntervalMillis = 100L,
        )

        repeat(1_000) {
            diagnostics.record(
                P2pDiagnosticEvent(P2pDiagnosticEventName.FRAME_DROPPED),
            )
        }
        runCurrent()
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("seq=1000 "))

        repeat(1_000) {
            diagnostics.record(
                P2pDiagnosticEvent(P2pDiagnosticEventName.PEER_RATE_LIMITED),
            )
        }
        advanceTimeBy(99L)
        runCurrent()
        assertEquals(1, lines.size)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, lines.size)
        assertTrue(lines.last().startsWith("seq=2000 "))
    }

    @Test
    fun exported_lines_have_only_the_allowlisted_fixed_shape() = runTest {
        val diagnostics = BoundedP2pDiagnostics(
            scope = backgroundScope,
            writer = P2pDiagnosticWriter {},
            outputIntervalMillis = 0L,
        )
        diagnostics.record(
            P2pDiagnosticEvent(
                name = P2pDiagnosticEventName.COMMAND_REJECTED,
                role = P2pDiagnosticRole.HOST,
                result = P2pDiagnosticResult.REJECTED,
                reason = P2pDiagnosticReason.STALE_REVISION,
                count = P2pDiagnosticCountBucket.ONE,
            ),
        )

        val line = diagnostics.export()
        assertTrue(
            Regex(
                "seq=[0-9]+ elapsed_ms=[0-9]+ event=[a-z_]+ role=[a-z_]+ " +
                    "result=[a-z_]+ reason=[a-z_]+ count=[a-z_]+",
            ).matches(line),
        )
        assertTrue("event=command_rejected" in line)
        assertTrue("reason=stale_revision" in line)
    }

    @Test
    fun invalid_memory_or_output_configuration_fails_closed() = runTest {
        assertFailsWith<IllegalArgumentException> {
            BoundedP2pDiagnostics(
                scope = backgroundScope,
                writer = P2pDiagnosticWriter {},
                capacity = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BoundedP2pDiagnostics(
                scope = backgroundScope,
                writer = P2pDiagnosticWriter {},
                outputIntervalMillis = -1L,
            )
        }
    }

    @Test
    fun count_buckets_disclose_only_coarse_cardinality() {
        assertEquals(P2pDiagnosticCountBucket.ZERO, diagnosticCount(0))
        assertEquals(P2pDiagnosticCountBucket.ONE, diagnosticCount(1))
        assertEquals(P2pDiagnosticCountBucket.TWO_TO_FOUR, diagnosticCount(4))
        assertEquals(P2pDiagnosticCountBucket.FIVE_TO_EIGHT, diagnosticCount(8))
        assertEquals(P2pDiagnosticCountBucket.NINE_TO_SEVENTEEN, diagnosticCount(17))
        assertEquals(P2pDiagnosticCountBucket.OVER_LIMIT, diagnosticCount(18))
    }
}

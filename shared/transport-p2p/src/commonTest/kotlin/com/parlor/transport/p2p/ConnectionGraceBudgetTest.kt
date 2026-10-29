package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionGraceBudgetTest {
    @Test
    fun wallRollbackWithinWindowCannotExtendElapsedGrace() {
        var elapsed = 0L
        val budget = ConnectionGraceBudget(1_000L, 16_000L, 1_000L) { elapsed }
        elapsed = 14_999L
        assertEquals(1L, budget.remaining(2_000L))
        assertEquals(15_999L, budget.observedAt(2_000L))
        elapsed++
        assertEquals(0L, budget.remaining(2_000L))
        assertEquals(16_000L, budget.observedAt(2_000L))
    }

    @Test
    fun delayedIntakeAndWallForwardOnlyReduceTheBudget() {
        var elapsed = 100L
        val budget = ConnectionGraceBudget(1_000L, 16_000L, 11_000L) { elapsed }
        assertEquals(5_000L, budget.remaining(11_000L))
        elapsed += 4_000L
        assertEquals(1_000L, budget.remaining(2_000L))
        assertEquals(15_000L, budget.observedAt(2_000L))
        assertEquals(0L, budget.remaining(16_000L))
    }

    @Test
    fun invalidElapsedReadingsNeverGrantGraceOrRecoveryTime() {
        for ((start, end) in listOf(-1L to 0L, 100L to 99L, Long.MAX_VALUE to Long.MIN_VALUE)) {
            var elapsed = start
            val budget = ConnectionGraceBudget(0L, 15_000L, 0L) { elapsed }
            elapsed = end
            assertEquals(0L, budget.remaining(0L))
            assertEquals(Long.MAX_VALUE, budget.observedAt(0L))
        }
    }

    @Test
    fun projectedEpochSaturatesRatherThanOverflowingIntoANewWindow() {
        var elapsed = 0L
        val budget = ConnectionGraceBudget(Long.MAX_VALUE - 10L, Long.MAX_VALUE, Long.MAX_VALUE - 10L) { elapsed }
        elapsed = 20L
        assertEquals(0L, budget.remaining(Long.MAX_VALUE - 10L))
        assertEquals(Long.MAX_VALUE, budget.observedAt(0L))
    }

    @Test
    fun oversizedOrInvalidEpochWindowsCannotGrantMoreThanFifteenSeconds() {
        var elapsed = 0L
        val budget = ConnectionGraceBudget(0L, Long.MAX_VALUE, 0L) { elapsed }
        assertEquals(15_000L, budget.remaining(0L))
        elapsed = 15_000L
        assertEquals(0L, budget.remaining(0L))
        assertEquals(0L, ConnectionGraceBudget(100L, 200L, 99L) { 0L }.remaining(100L))
        assertEquals(0L, ConnectionGraceBudget(100L, 200L, 200L) { 0L }.remaining(100L))
        assertEquals(0L, ConnectionGraceBudget(100L, 200L, 100L) { 0L }.remaining(99L))
    }
}

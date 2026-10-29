package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertEquals

class RecoveryWindowBudgetTest {
    @Test
    fun repeated_schedules_cannot_renew_spent_elapsed_time() {
        var now = 0L
        val budget = RecoveryWindowBudget { now }
        assertEquals(100L, budget.remaining(1_000L, 100L, newWindow = true))
        now = 60L
        assertEquals(40L, budget.remaining(1_000L, 100L, newWindow = false))
        now = 100L
        assertEquals(0L, budget.remaining(1_000L, 100L, newWindow = false))
    }

    @Test
    fun a_shorter_epoch_hint_can_shorten_but_never_extend_the_original_budget() {
        var now = 0L
        val budget = RecoveryWindowBudget { now }
        budget.remaining(1_000L, 100L, newWindow = true)
        now = 60L
        assertEquals(10L, budget.remaining(1_000L, 10L, newWindow = false))
        assertEquals(40L, budget.remaining(1_000L, 1_000L, newWindow = false))
    }

    @Test
    fun a_new_window_or_distinct_deadline_receives_its_own_budget() {
        var now = 0L
        val budget = RecoveryWindowBudget { now }
        budget.remaining(1_000L, 100L, newWindow = true)
        now = 100L
        assertEquals(100L, budget.remaining(1_000L, 100L, newWindow = true))
        now = 200L
        assertEquals(50L, budget.remaining(2_000L, 50L, newWindow = false))
    }

    @Test
    fun impossible_elapsed_clock_readings_and_negative_requests_fail_closed() {
        var now = 100L
        val budget = RecoveryWindowBudget { now }
        budget.remaining(1_000L, 100L, newWindow = true)
        now = 99L
        assertEquals(0L, budget.remaining(1_000L, 100L, newWindow = false))
        assertEquals(0L, budget.remaining(2_000L, -1L, newWindow = true))
        val unavailable = RecoveryWindowBudget { -1L }
        assertEquals(0L, unavailable.remaining(1_000L, 100L, newWindow = true))
    }
}

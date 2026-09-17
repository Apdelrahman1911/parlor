package com.parlor.app.shell.game.multiplayer

import kotlin.test.Test
import kotlin.test.assertEquals

class GameBackPolicyTest {
    @Test
    fun critical_operation_then_leave_confirmation_then_transient_then_guard_is_the_shared_back_order() {
        var busy = true
        var confirming = true
        var transient = true
        val calls = mutableListOf<String>()
        fun back(request: Long = 1L) = dispatchGameBack(request, busy, confirming,
            dismissLeave = { confirming = false; calls += "stay" },
            closeTransient = {
                if (transient) { transient = false; calls += "transient"; true } else false
            },
            requestLeave = { confirming = true; calls += "leave-guard" },
        )
        back()
        assertEquals(emptyList(), calls)
        busy = false
        back(0L)
        assertEquals(emptyList(), calls)
        back()
        back()
        back()
        assertEquals(listOf("stay", "transient", "leave-guard"), calls)
        back() // A second Back dismisses the guard; it cannot confirm an exit.
        assertEquals(listOf("stay", "transient", "leave-guard", "stay"), calls)
    }
}

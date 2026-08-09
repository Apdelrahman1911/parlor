package com.parlor.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ParlorToastStateTest {

    @Test
    fun queue_is_bounded_and_keeps_the_latest_distinct_messages() {
        val state = ParlorToastState()

        repeat(100) { state.show("message-$it") }

        assertEquals(
            listOf("message-96", "message-97", "message-98", "message-99"),
            state.toasts.value.map(ParlorToast::text),
        )
    }

    @Test
    fun adjacent_duplicate_message_is_coalesced() {
        val state = ParlorToastState()

        state.show("Network restored", ParlorToastSeverity.Success)
        state.show("Network restored", ParlorToastSeverity.Success)

        assertEquals(1, state.toasts.value.size)
    }
}

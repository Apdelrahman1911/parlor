package com.parlor.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

    @Test
    fun dismissed_identity_cannot_dismiss_a_new_lifetime_even_with_identical_text() {
        val state = ParlorToastState()
        state.show("Network restored")
        val previousId = state.toasts.value.single().id
        state.dismiss(previousId)

        state.show("Network restored")
        val newId = state.toasts.value.single().id
        assertNotEquals(previousId, newId)
        state.dismiss(previousId)
        assertEquals(newId, state.toasts.value.single().id)
    }

    @Test
    fun coalescing_preserves_existing_lifetime_and_eviction_does_not_recycle_it() {
        val state = ParlorToastState()
        state.show("First")
        val first = state.toasts.value.single()
        state.show("First", ParlorToastSeverity.Danger)
        assertEquals(first, state.toasts.value.single())

        repeat(10) { state.show("replacement-$it") }
        state.dismiss(first.id)
        assertEquals(4, state.toasts.value.size)
        assertEquals(4, state.toasts.value.map { it.id }.distinct().size)
        state.toasts.value.forEach { assertNotEquals(first.id, it.id) }
    }
}

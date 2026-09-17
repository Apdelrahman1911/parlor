package com.parlor.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InPlaceBackTest {
    @Test
    fun inner_transient_consumes_once_before_outer_content_and_the_shell_guard() {
        val owner = InPlaceBackOwner()
        var innerVisible = true
        var outerVisible = true
        val calls = mutableListOf<String>()
        owner.register(Any()) {
            if (outerVisible) { outerVisible = false; calls += "outer"; true } else false
        }
        owner.register(Any()) {
            if (innerVisible) { innerVisible = false; calls += "inner"; true } else false
        }
        assertTrue(owner.handleBack())
        assertEquals(listOf("inner"), calls)
        assertTrue(owner.handleBack())
        assertEquals(listOf("inner", "outer"), calls)
        assertFalse(owner.handleBack())
    }

    @Test
    fun disposal_is_identity_scoped_and_registration_storage_is_bounded() {
        val owner = InPlaceBackOwner()
        val old = Any()
        val replacement = Any()
        owner.register(old) { error("Stale handler") }
        owner.register(replacement) { true }
        owner.unregister(old)
        owner.unregister(old)
        assertTrue(owner.handleBack())
        owner.unregister(replacement)
        assertFalse(owner.handleBack())
        repeat(8) { owner.register(Any()) { false } }
        assertFailsWith<IllegalStateException> { owner.register(Any()) { true } }
    }
}

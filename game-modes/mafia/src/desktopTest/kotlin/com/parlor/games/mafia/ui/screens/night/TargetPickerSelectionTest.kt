package com.parlor.games.mafia.ui.screens.night

import com.parlor.core.ids.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetPickerSelectionTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")

    @Test
    fun enabled_current_target_remains_submittable() {
        assertEquals(
            alice,
            validTargetSelection(
                selected = alice,
                targets = listOf(PickableTarget(alice, "Alice")),
            ),
        )
    }

    @Test
    fun removed_or_disabled_target_is_rejected_before_submit() {
        assertNull(
            validTargetSelection(
                selected = alice,
                targets = listOf(PickableTarget(bob, "Bob")),
            ),
        )
        assertNull(
            validTargetSelection(
                selected = alice,
                targets = listOf(PickableTarget(alice, "Alice", enabled = false)),
            ),
        )
    }

    @Test
    fun absent_selection_is_never_invented() {
        assertNull(
            validTargetSelection(
                selected = null,
                targets = listOf(PickableTarget(alice, "Alice")),
            ),
        )
    }
}

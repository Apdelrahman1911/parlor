package com.parlor.games.whodunit.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevealCompletionGateTest {
    @Test
    fun completionCanBeClaimedExactlyOnceAcrossRepeatedGestureCallbacks() {
        val gate = RevealCompletionGate()

        assertTrue(gate.tryComplete())
        assertFalse(gate.tryComplete())
        assertFalse(gate.tryComplete())
    }
}

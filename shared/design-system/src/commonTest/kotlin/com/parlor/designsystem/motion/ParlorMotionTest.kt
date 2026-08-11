package com.parlor.designsystem.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParlorMotionTest {
    @Test
    fun either_app_or_system_preference_enables_reduced_motion() {
        assertFalse(shouldReduceMotion(appPreference = false, systemPreference = false))
        assertTrue(shouldReduceMotion(appPreference = true, systemPreference = false))
        assertTrue(shouldReduceMotion(appPreference = false, systemPreference = true))
        assertTrue(shouldReduceMotion(appPreference = true, systemPreference = true))
    }

    @Test
    fun reduced_tokens_keep_only_a_short_fade_and_disable_continuous_cycles() {
        val regular = ParlorMotion()
        val reduced = regular.forReducedMotion(enabled = true)

        assertEquals(regular, regular.forReducedMotion(enabled = false))
        assertEquals(0, reduced.durationEmberCycle)
        assertEquals(reduced.durationFast, reduced.durationMedium)
        assertEquals(reduced.durationFast, reduced.durationSlow)
        assertEquals(reduced.durationFast, reduced.durationTheatrical)
        assertTrue(reduced.durationFast in 1..150)
    }
}

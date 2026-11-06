package com.parlor.designsystem.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class ScopedLocaleOverridesTest {
    @Test
    fun disposing_old_arabic_activity_does_not_overwrite_its_new_arabic_successor() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        val current = platform.overrides.acquire(arabic)

        old()
        assertEquals(arabic, platform.defaults)
        assertEquals(listOf(arabic, arabic), platform.writes)
        current()
        assertEquals(system, platform.defaults)
    }

    @Test
    fun reverse_disposal_restores_the_live_predecessor_then_the_complete_system_list() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        val current = platform.overrides.acquire(english)

        current()
        assertEquals(arabic, platform.defaults)
        old()
        assertEquals(system, platform.defaults)
    }

    @Test
    fun removing_middle_then_first_scope_does_not_leave_a_dead_restoration_target() {
        val platform = Platform()
        val first = platform.overrides.acquire(arabic)
        val middle = platform.overrides.acquire(english)
        val current = platform.overrides.acquire(arabic)

        middle()
        first()
        assertEquals(arabic, platform.defaults)
        current()
        assertEquals(system, platform.defaults)
    }

    @Test
    fun a_later_external_locale_change_is_not_undone_on_disposal() {
        val platform = Platform()
        val release = platform.overrides.acquire(arabic)
        platform.defaults = external

        release()
        assertEquals(external, platform.defaults)
        assertEquals(listOf(arabic), platform.writes)
    }

    @Test
    fun an_external_change_captured_by_a_successor_is_not_replaced_by_an_old_baseline() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        platform.defaults = external
        val current = platform.overrides.acquire(arabic)

        old()
        assertEquals(arabic, platform.defaults)
        current()
        assertEquals(external, platform.defaults)
    }

    @Test
    fun a_new_system_selection_takes_precedence_over_an_older_explicit_language() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        val current = platform.overrides.acquire(system)

        old()
        assertEquals(system, platform.defaults)
        current()
        assertEquals(system, platform.defaults)
    }

    @Test
    fun duplicate_release_cannot_mutate_a_later_owner() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        old()
        val current = platform.overrides.acquire(english)

        old()
        assertEquals(english, platform.defaults)
        current()
        assertEquals(system, platform.defaults)
    }

    private data class Defaults(val language: String, val orderedLanguages: List<String>)

    private class Platform {
        var defaults = system
        val writes = mutableListOf<Defaults>()
        val overrides = ScopedLocaleOverrides(read = { defaults }, write = {
            defaults = it
            writes += it
        })
    }

    private companion object {
        val system = Defaults("en", listOf("en", "es"))
        val english = Defaults("en", listOf("en"))
        val arabic = Defaults("ar", listOf("ar"))
        val external = Defaults("de", listOf("de", "en"))
    }
}

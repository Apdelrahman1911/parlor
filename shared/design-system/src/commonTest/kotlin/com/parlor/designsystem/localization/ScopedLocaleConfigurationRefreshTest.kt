package com.parlor.designsystem.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ScopedLocaleConfigurationRefreshTest {
    @Test
    fun active_owner_reapplies_after_a_platform_reset_and_restores_the_new_baseline_on_release() {
        val platform = Platform()
        val scope = platform.overrides.acquire(arabic)
        platform.defaults = changedSystem

        scope.refresh(arabic, changedSystem)
        assertEquals(arabic, platform.defaults)
        scope()
        assertEquals(changedSystem, platform.defaults)
    }

    @Test
    fun an_old_equal_language_callback_cannot_overwrite_the_new_activity_owner() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        val current = platform.overrides.acquire(arabic)
        platform.defaults = changedSystem
        val reads = platform.reads
        val writes = platform.writes.toList()

        old.refresh(arabic, changedSystem)
        assertEquals(changedSystem, platform.defaults)
        assertEquals(reads, platform.reads)
        assertEquals(writes, platform.writes)
        current.refresh(arabic, changedSystem)
        assertEquals(arabic, platform.defaults)
        old()
        current()
        assertEquals(changedSystem, platform.defaults)
    }

    @Test
    fun an_old_different_language_callback_cannot_steal_ownership() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        val current = platform.overrides.acquire(english)
        platform.defaults = changedSystem

        old.refresh(arabic, changedSystem)
        assertEquals(changedSystem, platform.defaults)
        current.refresh(english, changedSystem)
        assertEquals(english, platform.defaults)
    }

    @Test
    fun disposed_scope_callbacks_never_read_or_write_a_later_owner() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        old()
        platform.overrides.acquire(english)
        val reads = platform.reads
        val writes = platform.writes.toList()

        old.refresh(arabic, system)
        assertEquals(english, platform.defaults)
        assertEquals(reads, platform.reads)
        assertEquals(writes, platform.writes)
    }

    @Test
    fun system_mode_accepts_the_complete_new_platform_list_without_an_extra_write() {
        val platform = Platform()
        val scope = platform.overrides.acquire(system)
        platform.defaults = changedSystem
        val writes = platform.writes.toList()

        scope.refresh(changedSystem, changedSystem)
        assertEquals(changedSystem, platform.defaults)
        assertEquals(writes, platform.writes)
        scope()
        assertEquals(changedSystem, platform.defaults)
    }

    @Test
    fun equal_configuration_callbacks_do_not_add_redundant_global_writes() {
        val platform = Platform()
        val scope = platform.overrides.acquire(arabic)
        val writes = platform.writes.toList()

        scope.refresh(arabic, system)
        scope.refresh(arabic, system)
        assertEquals(writes, platform.writes)
        scope()
        assertEquals(system, platform.defaults)
    }

    @Test
    fun failing_platform_read_propagates_without_a_write() {
        val platform = Platform()
        val scope = platform.overrides.acquire(arabic)
        val failure = IllegalStateException("Synthetic read failure")
        val writes = platform.writes.toList()
        platform.readFailure = failure

        assertSame(failure, assertFailsWith<IllegalStateException> { scope.refresh(arabic, system) })
        assertEquals(writes, platform.writes)
        platform.readFailure = null
        scope()
        assertEquals(system, platform.defaults)
    }

    @Test
    fun failing_platform_write_does_not_claim_new_defaults_or_erase_the_external_change() {
        val platform = Platform()
        val scope = platform.overrides.acquire(arabic)
        platform.defaults = changedSystem
        val failure = IllegalStateException("Synthetic write failure")
        platform.writeFailure = failure

        assertSame(failure, assertFailsWith<IllegalStateException> { scope.refresh(english, changedSystem) })
        assertEquals(changedSystem, platform.defaults)
        platform.writeFailure = null
        scope()
        assertEquals(changedSystem, platform.defaults)
    }

    @Test
    fun releasing_the_current_scope_restores_its_live_predecessor_then_the_new_system_baseline() {
        val platform = Platform()
        val old = platform.overrides.acquire(arabic)
        val current = platform.overrides.acquire(english)
        platform.defaults = changedSystem

        current.refresh(english, changedSystem)
        current()
        assertEquals(arabic, platform.defaults)
        old()
        assertEquals(changedSystem, platform.defaults)
    }

    @Test
    fun a_system_change_matching_the_override_is_not_mistaken_for_the_old_system_baseline() {
        val platform = Platform()
        val scope = platform.overrides.acquire(arabic)

        scope.refresh(arabic, arabic)
        scope()
        assertEquals(arabic, platform.defaults)
    }

    private class Platform {
        var defaults = system
        var reads = 0
        var readFailure: IllegalStateException? = null
        var writeFailure: IllegalStateException? = null
        val writes = mutableListOf<List<String>>()
        val overrides = ScopedLocaleOverrides(
            read = {
                readFailure?.let { throw it }
                reads++
                defaults
            },
            write = {
                writeFailure?.let { throw it }
                defaults = it
                writes += it
            },
        )
    }

    private companion object {
        val system = listOf("en", "es")
        val arabic = listOf("ar")
        val english = listOf("en")
        val changedSystem = listOf("de", "en", "es")
    }
}

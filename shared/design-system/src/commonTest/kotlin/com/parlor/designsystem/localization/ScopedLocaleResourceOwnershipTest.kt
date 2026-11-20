package com.parlor.designsystem.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Nullable configuration-context ownership used by LocaleAwareApplication. */
class ScopedLocaleResourceOwnershipTest {
    @Test
    fun disposing_an_old_equal_language_context_preserves_the_exact_live_context() {
        val owner = ResourceOwner()
        val old = owner.scopes.acquire(Context("ar"))
        val currentContext = Context("ar")
        val current = owner.scopes.acquire(currentContext)

        old()
        assertSame(currentContext, owner.context)
        current()
        assertNull(owner.context)
    }

    @Test
    fun system_scope_reveals_the_live_base_context_not_a_captured_locale() {
        val owner = ResourceOwner()
        val explicit = owner.scopes.acquire(Context("ar"))
        val system = owner.scopes.acquire(null)
        owner.platformLocales = listOf("de", "fr")

        explicit()
        assertNull(owner.context)
        assertEquals(listOf("de", "fr"), owner.frameworkDefaults())
        system()
        assertNull(owner.context)
    }

    @Test
    fun framework_reset_without_a_configuration_callback_keeps_the_owned_language() {
        val owner = ResourceOwner()
        val release = owner.scopes.acquire(Context("ar"))

        // Model the documented reset source, including a newer system list.
        // No callback refresh or process-locale repair is invoked here.
        assertEquals(listOf("ar", "en-US", "es"), owner.frameworkDefaults())
        owner.platformLocales = listOf("fr", "de")
        assertEquals(listOf("ar", "fr", "de"), owner.frameworkDefaults())
        release()
        assertEquals(listOf("fr", "de"), owner.frameworkDefaults())
    }

    @Test
    fun reverse_release_restores_the_live_resource_owner_and_then_system() {
        val owner = ResourceOwner()
        val arabicContext = Context("ar")
        val arabic = owner.scopes.acquire(arabicContext)
        val english = owner.scopes.acquire(Context("en"))

        english()
        assertSame(arabicContext, owner.context)
        arabic()
        assertNull(owner.context)
        english()
        assertNull(owner.context)
    }

    private class Context(val language: String)

    private class ResourceOwner {
        var context: Context? = null
        var platformLocales = listOf("en-US", "es")
        val scopes = ScopedLocaleOverrides(read = { context }, write = { context = it })

        fun frameworkDefaults(): List<String> = orderedLocaleDefaults(context?.language, platformLocales)
    }
}

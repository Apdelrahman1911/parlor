package com.parlor.app.storage

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidSettingsKeyValueBackingTest {
    @Test
    fun readsOnlyTheRequestedPreferenceWithoutCloningTheWholeMap() {
        val backing = AndroidSettingsKeyValueBacking(
            fakePreferences(
                mapOf(
                    "enabled" to true,
                    "language" to "ar",
                ),
            ),
        )

        assertEquals(true, backing.readBoolean("enabled"))
        assertEquals("ar", backing.readString("language"))
        assertNull(backing.readBoolean("missing"))
        assertNull(backing.readString("missing"))
    }

    @Test
    fun wrongStoredTypesFailClosedInsteadOfCrashingStartup() {
        val backing = AndroidSettingsKeyValueBacking(
            fakePreferences(
                mapOf(
                    "enabled" to "not-a-boolean",
                    "language" to false,
                ),
            ),
        )

        assertNull(backing.readBoolean("enabled"))
        assertNull(backing.readString("language"))
    }

    private fun fakePreferences(values: Map<String, Any>): SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            val key = arguments?.firstOrNull() as? String
            when (method.name) {
                "contains" -> values.containsKey(key)
                "getBoolean" -> values[key] as Boolean
                "getString" -> values[key] as String
                "getAll" -> error("read path must not clone SharedPreferences.all")
                "toString" -> "FakeSharedPreferences"
                "hashCode" -> System.identityHashCode(values)
                "equals" -> false
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences
}

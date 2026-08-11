package com.parlor.app.storage

import android.content.Context
import android.content.SharedPreferences
import com.parlor.storage.settings.SettingsKeyValueBacking
import com.parlor.storage.settings.SettingsPersistenceException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credential-encrypted app preferences. These values contain no identifiers,
 * gameplay state, or secrets; the app manifest disables Android backup.
 */
internal class AndroidSettingsKeyValueBacking(
    private val preferences: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsKeyValueBacking {
    constructor(context: Context) : this(
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
    )

    override fun readBoolean(key: String): Boolean? {
        if (!preferences.contains(key)) return null
        return try {
            preferences.getBoolean(key, false)
        } catch (_: ClassCastException) {
            // Corrupt or legacy values fail closed to the validated default.
            null
        }
    }

    override fun readString(key: String): String? {
        if (!preferences.contains(key)) return null
        return try {
            preferences.getString(key, null)
        } catch (_: ClassCastException) {
            // Do not crash startup when an old build stored a different type.
            null
        }
    }

    override suspend fun writeBoolean(key: String, value: Boolean) {
        persist(preferences.edit().putBoolean(key, value))
    }

    override suspend fun writeString(key: String, value: String?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        persist(editor)
    }

    @Suppress("RedundantSuspendModifier") // withContext is a real suspension point; KMP Detekt false-positive.
    private suspend fun persist(editor: SharedPreferences.Editor) {
        withContext(ioDispatcher) {
            if (!editor.commit()) {
                throw SettingsPersistenceException("Couldn't persist app preference")
            }
        }
    }

    private companion object {
        const val FILE_NAME = "parlor_settings_v1"
    }
}

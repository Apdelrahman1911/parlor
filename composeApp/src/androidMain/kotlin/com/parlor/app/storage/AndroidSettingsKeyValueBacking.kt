package com.parlor.app.storage

import android.content.Context
import android.content.SharedPreferences
import com.parlor.storage.settings.SettingsKeyValueBacking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credential-encrypted app preferences. These values contain no identifiers,
 * gameplay state, or secrets; the app manifest disables Android backup.
 */
internal class AndroidSettingsKeyValueBacking(
    context: Context,
) : SettingsKeyValueBacking {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun readBoolean(key: String): Boolean? =
        preferences.all[key] as? Boolean

    override fun readString(key: String): String? =
        preferences.all[key] as? String

    override suspend fun writeBoolean(key: String, value: Boolean) {
        persist(preferences.edit().putBoolean(key, value))
    }

    override suspend fun writeString(key: String, value: String?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        persist(editor)
    }

    private suspend fun persist(editor: SharedPreferences.Editor) {
        withContext(Dispatchers.IO) {
            check(editor.commit()) { "Couldn't persist app preference" }
        }
    }

    private companion object {
        const val FILE_NAME = "parlor_settings_v1"
    }
}

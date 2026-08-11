package com.parlor.app.storage

import com.parlor.storage.settings.SettingsKeyValueBacking
import com.parlor.storage.settings.SettingsPersistenceException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/**
 * Desktop development preferences scoped to the current OS user.
 *
 * Only non-sensitive UI preferences are stored here; game secrets use the
 * separately protected snapshot filesystem.
 */
internal class DesktopSettingsKeyValueBacking(
    private val preferences: Preferences =
        Preferences.userRoot().node("com/parlor/app/settings-v1"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsKeyValueBacking {

    override fun readBoolean(key: String): Boolean? =
        preferences.get(key, null)?.toBooleanStrictOrNull()

    override fun readString(key: String): String? =
        preferences.get(key, null)

    override suspend fun writeBoolean(key: String, value: Boolean) {
        persist { preferences.put(key, value.toString()) }
    }

    override suspend fun writeString(key: String, value: String?) {
        persist {
            if (value == null) preferences.remove(key) else preferences.put(key, value)
        }
    }

    @Suppress("RedundantSuspendModifier") // withContext is a real suspension point; KMP Detekt false-positive.
    private suspend fun persist(change: () -> Unit) {
        withContext(ioDispatcher) {
            try {
                change()
                preferences.flush()
            } catch (failure: BackingStoreException) {
                throw SettingsPersistenceException("Couldn't persist app preference", failure)
            } catch (failure: SecurityException) {
                throw SettingsPersistenceException("Preference storage is unavailable", failure)
            }
        }
    }
}

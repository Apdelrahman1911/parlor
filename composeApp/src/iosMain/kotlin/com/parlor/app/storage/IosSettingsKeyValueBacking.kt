package com.parlor.app.storage

import com.parlor.storage.settings.SettingsKeyValueBacking
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/** Non-sensitive app preferences backed by the platform defaults database. */
internal class IosSettingsKeyValueBacking(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SettingsKeyValueBacking {

    override fun readBoolean(key: String): Boolean? =
        when (val value = defaults.objectForKey(key)) {
            is Boolean -> value
            is NSNumber -> value.boolValue
            else -> null
        }

    override fun readString(key: String): String? =
        defaults.objectForKey(key) as? String

    override suspend fun writeBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    override suspend fun writeString(key: String, value: String?) {
        if (value == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, forKey = key)
        }
    }
}

package com.parlor.app.storage

import com.parlor.storage.settings.SettingsKeyValueBacking
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * Non-sensitive app preferences backed by the platform defaults database.
 *
 * `NSUserDefaults` updates its process-visible search list immediately and
 * persists changes asynchronously. Its write methods expose no error result,
 * and Apple documents `synchronize()` as unnecessary and deprecated; these
 * methods therefore report acceptance, not a durable-write acknowledgement.
 */
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

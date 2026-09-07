package com.parlor.designsystem.localization

import platform.Foundation.NSUserDefaults

/**
 * Own only overrides installed by Parlor, not the resolved global/OS preference.
 * The small ownership record survives a process exit that skips Compose disposal.
 * Unmarked values (including older installations with no provenance) are preserved.
 * NSUserDefaults accepts writes synchronously but persists them asynchronously;
 * this is not a durable-write acknowledgement or a cross-process transaction.
 */
internal class IosLanguageOverrideOwner(
    private val defaults: NSUserDefaults,
    private val applicationDomain: String,
) {
    fun apply(languageTag: String?) {
        release()
        if (languageTag == null) return

        val previous = defaults.persistentDomainForName(applicationDomain)?.get(APPLE_LANGUAGES_KEY)
        val installed = listOf(languageTag)
        // An identical existing value was not installed by this owner. In
        // particular, never claim (and later remove) an OS-managed preference.
        if (previous == installed) return
        val record = mutableMapOf<String, Any>(
            "version" to "1",
            "installed" to languageTag,
        )
        if (previous != null) record["previous"] = previous
        // Prepare ownership first. If interrupted before the following write,
        // release() only restores when the exact installed value is present.
        defaults.setObject(record, OWNERSHIP_KEY)
        defaults.setObject(installed, APPLE_LANGUAGES_KEY)
    }

    fun release() {
        val domain = defaults.persistentDomainForName(applicationDomain).orEmpty()
        val record = domain[OWNERSHIP_KEY] as? Map<*, *> ?: return
        val installed = record["installed"] as? String
        val previous = record["previous"]
        val validPrevious = "previous" !in record ||
            previous is List<*> && previous.all { it is String }
        if (
            record["version"] == "1" && installed != null && validPrevious &&
            domain[APPLE_LANGUAGES_KEY] == listOf(installed)
        ) {
            if (previous == null) {
                defaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
            } else {
                defaults.setObject(previous, APPLE_LANGUAGES_KEY)
            }
        }
        // External changes win, and only Parlor's own bookkeeping is removed.
        defaults.removeObjectForKey(OWNERSHIP_KEY)
    }

    internal companion object {
        const val APPLE_LANGUAGES_KEY = "AppleLanguages"
        const val OWNERSHIP_KEY = "com.parlor.languageOverrideOwner.v1"
    }
}

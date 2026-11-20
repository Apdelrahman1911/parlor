package com.parlor.designsystem.localization

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Compose resources read [Locale.getDefault], so an explicit in-app language
 * requires a process-locale override. Each [DisposableEffect] acquires its own
 * scope: disposing an older Activity cannot restore defaults over a newer
 * composition, even when both requested Arabic. System mode also owns its
 * defaults while active, rather than inheriting a previous Activity's override.
 * Framework/Material configuration remains scoped; no shared Resources mutate.
 */
@Composable
internal actual fun PlatformAppLocale(
    languageTag: String?,
    loading: @Composable () -> Unit,
    content: @Composable (activeLanguageTag: String?) -> Unit,
) {
    val baseContext = LocalContext.current
    val applicationContext = baseContext.applicationContext
    val baseConfiguration = LocalConfiguration.current
    val systemLocales = baseConfiguration.locales
    val systemLocale = systemLocales[0]
    val requestedLocale = remember(languageTag, systemLocale) {
        languageTag?.let(Locale::forLanguageTag) ?: systemLocale
    }
    val requestedDefaults = remember(languageTag, requestedLocale, systemLocales) {
        defaultLocales(languageTag, systemLocales)
    }
    var appliedLanguageTag by remember {
        mutableStateOf<String?>(null)
    }

    // System and an explicit language can have equal defaults but must respond
    // differently to the next system-language change.
    DisposableEffect(applicationContext, languageTag, requestedDefaults) {
        // The shipping Application participates in framework locale resets.
        // Preview/embedded hosts can still use the composition-scoped adapter.
        val releaseResources = (applicationContext as? LocaleAwareApplication)?.acquireLocaleResources(languageTag)
        var localeAcquired = false
        val releaseLocale = try {
            androidLocaleOverrides.acquire(requestedDefaults).also { localeAcquired = true }
        } finally {
            if (!localeAcquired) releaseResources?.invoke()
        }
        // Android resets process defaults before dispatching configuration
        // callbacks, including changes that leave the selected language equal.
        // Restore the active owner's choice before resource-bearing UI updates;
        // do not remount content, mutate shared Resources or poll global state.
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                releaseLocale.refresh(
                    defaults = defaultLocales(languageTag, newConfig.locales),
                    platformDefaults = newConfig.locales,
                )
            }

            override fun onLowMemory() = Unit
        }
        var registered = false
        try {
            applicationContext.registerComponentCallbacks(callbacks)
            registered = true
        } finally {
            if (!registered) {
                try {
                    releaseLocale()
                } finally {
                    releaseResources?.invoke()
                }
            }
        }
        appliedLanguageTag = requestedLocale.toLanguageTag()

        onDispose {
            try {
                applicationContext.unregisterComponentCallbacks(callbacks)
            } finally {
                try {
                    releaseLocale()
                } finally {
                    releaseResources?.invoke()
                }
            }
        }
    }

    val activeTag = appliedLanguageTag ?: run {
        loading()
        return
    }
    val activeLocale = remember(activeTag) { Locale.forLanguageTag(activeTag) }
    val localizedConfiguration = remember(baseConfiguration, activeTag) {
        Configuration(baseConfiguration).apply { setLocale(activeLocale) }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
    ) {
        content(activeTag)
    }
}

private fun defaultLocales(languageTag: String?, platformDefaults: LocaleList): LocaleList {
    if (languageTag == null) return platformDefaults
    val ordered = orderedLocaleDefaults(
        selected = Locale.forLanguageTag(languageTag),
        platformDefaults = List(platformDefaults.size()) { platformDefaults[it] },
    )
    // Public API; no hidden LocaleList(Locale, LocaleList) constructor or
    // extra array clone at a Kotlin-to-Java varargs boundary.
    return LocaleList.forLanguageTags(ordered.joinToString(",") { it.toLanguageTag() })
}

// Only Compose's UI-thread effects and their registered configuration callbacks
// invoke these ports. File initialization itself performs no platform mutation.
private val androidLocaleOverrides = ScopedLocaleOverrides(
    // Capture the effective priority: selected Locale first, all remaining
    // preferences retained. The nonzero-index setter is a hidden system API,
    // not available to applications; do not use reflection or discard locales.
    read = { LocaleList.getAdjustedDefault() },
    write = { defaults: LocaleList ->
        // The public setter also restores Locale.getDefault() to the first item.
        LocaleList.setDefault(defaults)
    },
)

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
        if (languageTag == null) systemLocales else LocaleList(requestedLocale)
    }
    var appliedLanguageTag by remember {
        mutableStateOf<String?>(null)
    }

    // System and an explicit language can have equal defaults but must respond
    // differently to the next system-language change.
    DisposableEffect(applicationContext, languageTag, requestedDefaults) {
        val releaseLocale = androidLocaleOverrides.acquire(requestedDefaults)
        // Android resets process defaults before dispatching configuration
        // callbacks, including changes that leave the selected language equal.
        // Restore the active owner's choice before resource-bearing UI updates;
        // do not remount content, mutate shared Resources or poll global state.
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                releaseLocale.refresh(
                    defaults = if (languageTag == null) newConfig.locales else requestedDefaults,
                    platformDefaults = newConfig.locales,
                )
            }

            override fun onLowMemory() = Unit
        }
        applicationContext.registerComponentCallbacks(callbacks)
        appliedLanguageTag = requestedLocale.toLanguageTag()

        onDispose {
            applicationContext.unregisterComponentCallbacks(callbacks)
            releaseLocale()
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

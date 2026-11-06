package com.parlor.designsystem.localization

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
    val baseConfiguration = LocalConfiguration.current
    val systemLocales = baseConfiguration.locales
    val systemLocale = systemLocales[0]
    val requestedLocale = remember(languageTag, systemLocale) {
        languageTag?.let(Locale::forLanguageTag) ?: systemLocale
    }
    val requestedDefaults = remember(languageTag, requestedLocale, systemLocales) {
        AndroidLocaleDefaults(
            locale = requestedLocale,
            locales = if (languageTag == null) systemLocales else LocaleList(requestedLocale),
        )
    }
    var appliedLanguageTag by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(requestedDefaults) {
        val releaseLocale = androidLocaleOverrides.acquire(requestedDefaults)
        appliedLanguageTag = requestedLocale.toLanguageTag()

        onDispose {
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

private data class AndroidLocaleDefaults(val locale: Locale, val locales: LocaleList)

// Only Compose's UI-thread effect acquire/release invokes these ports. The
// registry itself performs no platform mutation when this file initializes.
private val androidLocaleOverrides = ScopedLocaleOverrides(
    read = { AndroidLocaleDefaults(Locale.getDefault(), LocaleList.getDefault()) },
    write = { defaults: AndroidLocaleDefaults ->
        val index = defaults.locales.indexOf(defaults.locale)
        check(index >= 0) { "The default locale must belong to its captured list" }
        // setDefault(list, index) updates BOTH Locale.getDefault() and the full
        // ordered LocaleList, preserving an original nonzero default index.
        LocaleList.setDefault(defaults.locales, index)
    },
)

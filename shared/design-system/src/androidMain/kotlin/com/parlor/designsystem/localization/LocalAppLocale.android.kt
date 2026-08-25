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
 * requires a process-locale override. The mutation is owned by
 * [DisposableEffect], restored on disposal, and paired with a scoped Android
 * configuration for framework/Material resources. No shared Resources object
 * is mutated.
 */
@Composable
internal actual fun PlatformAppLocale(
    languageTag: String?,
    loading: @Composable () -> Unit,
    content: @Composable (activeLanguageTag: String?) -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val systemLocale = baseConfiguration.locales[0]
    val requestedLocale = remember(languageTag, systemLocale) {
        languageTag?.let(Locale::forLanguageTag) ?: systemLocale
    }
    var appliedLanguageTag by remember(languageTag, systemLocale) {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(languageTag, requestedLocale) {
        val previousLocale = Locale.getDefault()
        val previousLocaleList = LocaleList.getDefault()
        if (languageTag != null) {
            Locale.setDefault(requestedLocale)
            LocaleList.setDefault(LocaleList(requestedLocale))
        }
        appliedLanguageTag = requestedLocale.toLanguageTag()

        onDispose {
            if (
                languageTag != null &&
                Locale.getDefault().toLanguageTag() == requestedLocale.toLanguageTag()
            ) {
                Locale.setDefault(previousLocale)
                LocaleList.setDefault(previousLocaleList)
            }
        }
    }

    val activeTag = appliedLanguageTag ?: run {
        loading()
        return
    }
    val localizedConfiguration = remember(baseConfiguration, activeTag) {
        Configuration(baseConfiguration).apply { setLocale(requestedLocale) }
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

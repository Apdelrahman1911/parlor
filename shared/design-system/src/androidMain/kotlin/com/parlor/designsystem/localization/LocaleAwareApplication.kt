package com.parlor.designsystem.localization

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Looper
import java.util.Locale

/**
 * Supplies Android's process-locale reset with the same choice as Compose.
 * Android can read application resources and reset defaults without delivering
 * a configuration callback. A callback-only process override is not durable.
 *
 * Each live language scope owns a configuration context, never a mutation of
 * shared Resources. Android updates its other configuration fields normally.
 * System mode uses the unmodified base context, including future system locales.
 */
open class LocaleAwareApplication : Application() {
    @Volatile
    private var localizedContext: Context? = null

    private val resourceOverrides = ScopedLocaleOverrides<Context?>(
        read = { localizedContext },
        write = { localizedContext = it },
    )

    override fun getResources(): Resources = localizedContext?.resources ?: super.getResources()

    internal fun acquireLocaleResources(languageTag: String?): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        val context = languageTag?.let { tag ->
            // Only locale/layout direction are overridden. Do not freeze an
            // orientation, density, font scale, night mode or window size.
            baseContext.createConfigurationContext(
                Configuration().apply { setLocale(Locale.forLanguageTag(tag)) },
            )
        }
        val release = resourceOverrides.acquire(context)
        return {
            check(Looper.myLooper() == Looper.getMainLooper())
            release()
        }
    }
}

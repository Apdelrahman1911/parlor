package com.parlor.app

import com.parlor.app.di.allModules
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.designsystem.localization.LocaleAwareApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Android Application entry point. Bootstraps Koin with the shared module list.
 */
class ParlorApplication : LocaleAwareApplication() {
    override fun onCreate() {
        super.onCreate()
        val koinApplication = startKoin {
            androidContext(this@ParlorApplication)
            modules(allModules)
        }
        registerActivityLifecycleCallbacks(
            AndroidProcessLifecycleCallbacks(
                coordinator = koinApplication.koin.get<AppLifecycleCoordinator>(),
            ),
        )
    }
}

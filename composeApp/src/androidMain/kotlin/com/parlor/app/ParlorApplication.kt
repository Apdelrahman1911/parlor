package com.parlor.app

import android.app.Application
import com.parlor.app.di.allModules
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Android Application entry point. Bootstraps Koin with the shared module list.
 */
class ParlorApplication : Application() {
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

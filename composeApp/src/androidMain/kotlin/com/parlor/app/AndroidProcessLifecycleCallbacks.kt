package com.parlor.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.lifecycle.ProcessVisibilityTracker

/**
 * Maps process visibility to the shared lifecycle policy.
 *
 * Activity-local onStart/onStop callbacks are insufficient: configuration
 * recreation can create a false background/foreground pair. This process
 * counter keeps networking active while one Activity replaces another and is
 * also safe if another Activity is added later.
 */
internal class AndroidProcessLifecycleCallbacks(
    private val coordinator: AppLifecycleCoordinator,
) : Application.ActivityLifecycleCallbacks {
    private val processVisibility = ProcessVisibilityTracker<Activity>(
        onProcessForegrounded = coordinator::notifyActive,
        onProcessBackgrounded = coordinator::notifyBackgrounded,
    )

    override fun onActivityStarted(activity: Activity) {
        processVisibility.ownerStarted(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        processVisibility.ownerStopped(activity, activity.isChangingConfigurations)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        // Normally onStop already removed the Activity. Keeping this fallback
        // makes an abnormal destroy callback safe without double-emitting.
        processVisibility.ownerStopped(activity, activity.isChangingConfigurations)
    }
}

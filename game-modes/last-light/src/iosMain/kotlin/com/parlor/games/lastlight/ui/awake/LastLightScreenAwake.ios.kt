package com.parlor.games.lastlight.ui.awake

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState.UIApplicationStateActive
import platform.UIKit.UIApplicationWillResignActiveNotification

@Composable
internal actual fun rememberLastLightScreenAwake(): LastLightScreenAwake = remember { IosScreenAwake() }

/** All UIKit properties and leases are accessed from the main thread. */
private object ApplicationAwakeLeases {
    val pool = ScreenAwakeLeases(
        read = { UIApplication.sharedApplication.idleTimerDisabled },
        write = { UIApplication.sharedApplication.idleTimerDisabled = it },
    )
}

private class IosScreenAwake : LastLightScreenAwake {
    private val owner = Any()
    private val removeObservers = mutableListOf<() -> Unit>()
    private var requested = false
    private var nativeActive = false
    private var closed = false

    override fun prepare() {
        if (closed || removeObservers.isNotEmpty()) return
        nativeActive = UIApplication.sharedApplication.applicationState == UIApplicationStateActive
        observe(UIApplicationWillResignActiveNotification, active = false)
        observe(UIApplicationDidBecomeActiveNotification, active = true)
    }

    override fun setEnabled(enabled: Boolean) {
        if (closed) return
        requested = enabled
        update()
    }

    private fun update() {
        if (!closed && requested && nativeActive && UIApplication.sharedApplication.applicationState == UIApplicationStateActive) {
            ApplicationAwakeLeases.pool.acquire(owner)
        } else {
            ApplicationAwakeLeases.pool.release(owner)
        }
    }

    private fun observe(name: String?, active: Boolean) {
        if (name == null) return
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(name, `object` = null, queue = NSOperationQueue.mainQueue) {
            if (!closed) {
                nativeActive = active
                // Native inactivity releases immediately, without waiting for Compose.
                update()
            }
        }
        removeObservers += { center.removeObserver(observer) }
    }

    override fun close() {
        if (closed) return
        closed = true
        requested = false
        ApplicationAwakeLeases.pool.release(owner)
        removeObservers.forEach { it() }
        removeObservers.clear()
    }
}

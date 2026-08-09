package com.parlor.app.lifecycle

import com.parlor.networking.transport.RoomTransport

/**
 * App-level visibility as reported by the platform shell.
 *
 * [Inactive] is deliberately distinct from [Background]. Apple enters an
 * inactive state for short system interruptions (for example Control Centre,
 * an incoming call banner, or an authentication sheet) while the application
 * is still foreground-resident. Private game content must be covered during
 * that interval, but the LAN session must remain active.
 */
internal enum class AppVisibility {
    Active,
    Inactive,
    Background,
}

internal enum class TransportVisibility {
    Unknown,
    Foreground,
    Background,
}

internal enum class TransportLifecycleEffect {
    Foregrounded,
    Backgrounded,
}

internal data class AppLifecyclePolicyState(
    val appVisibility: AppVisibility = AppVisibility.Inactive,
    val transportVisibility: TransportVisibility = TransportVisibility.Unknown,
) {
    val privateContentCovered: Boolean
        get() = appVisibility != AppVisibility.Active
}

internal data class AppLifecycleTransition(
    val state: AppLifecyclePolicyState,
    val transportEffect: TransportLifecycleEffect?,
)

/** Pure lifecycle policy, kept separate so duplicate and reordered callbacks are testable. */
internal fun reduceAppLifecycle(
    previous: AppLifecyclePolicyState,
    visibility: AppVisibility,
): AppLifecycleTransition {
    val nextTransportVisibility = when (visibility) {
        AppVisibility.Active -> TransportVisibility.Foreground
        AppVisibility.Inactive -> previous.transportVisibility
        AppVisibility.Background -> TransportVisibility.Background
    }
    val effect = when {
        nextTransportVisibility == previous.transportVisibility -> null
        nextTransportVisibility == TransportVisibility.Foreground ->
            TransportLifecycleEffect.Foregrounded
        nextTransportVisibility == TransportVisibility.Background ->
            TransportLifecycleEffect.Backgrounded
        else -> null
    }
    return AppLifecycleTransition(
        state = AppLifecyclePolicyState(
            appVisibility = visibility,
            transportVisibility = nextTransportVisibility,
        ),
        transportEffect = effect,
    )
}

/**
 * Process-visibility tracker used by Android's Activity callbacks.
 *
 * Owners are tracked by identity/equality instead of a bare counter so a
 * duplicate callback cannot corrupt the process-visible count. A sole owner
 * stopping for configuration recreation is replaced without inventing a
 * background/foreground cycle.
 */
internal class ProcessVisibilityTracker<Owner : Any>(
    private val onProcessForegrounded: () -> Unit,
    private val onProcessBackgrounded: () -> Unit,
) {
    private val startedOwners = mutableSetOf<Owner>()
    private var awaitingConfigurationReplacement = false

    fun ownerStarted(owner: Owner) {
        if (!startedOwners.add(owner) || startedOwners.size != 1) return

        if (awaitingConfigurationReplacement) {
            awaitingConfigurationReplacement = false
        } else {
            onProcessForegrounded()
        }
    }

    fun ownerStopped(owner: Owner, changingConfigurations: Boolean) {
        if (!startedOwners.remove(owner) || startedOwners.isNotEmpty()) return

        if (changingConfigurations) {
            awaitingConfigurationReplacement = true
        } else {
            awaitingConfigurationReplacement = false
            onProcessBackgrounded()
        }
    }
}

/**
 * Process-scoped owner of the platform-to-transport lifecycle mapping.
 *
 * Android and iOS deliver these callbacks on their UI thread, which is the
 * single mutation owner for this class. Keeping this object in Koin as a
 * singleton also makes Activity/controller recreation harmless: duplicate
 * callbacks do not emit duplicate transport events.
 */
internal class AppLifecycleCoordinator(
    private val onTransportForegrounded: () -> Unit,
    private val onTransportBackgrounded: () -> Unit,
) {
    constructor(transport: RoomTransport) : this(
        onTransportForegrounded = transport::notifyAppForegrounded,
        onTransportBackgrounded = transport::notifyAppBackgrounded,
    )

    private var policyState = AppLifecyclePolicyState()

    fun notifyActive() = transitionTo(AppVisibility.Active)

    fun notifyInactive() = transitionTo(AppVisibility.Inactive)

    fun notifyBackgrounded() = transitionTo(AppVisibility.Background)

    private fun transitionTo(visibility: AppVisibility) {
        val transition = reduceAppLifecycle(policyState, visibility)
        // Commit before invoking the transport. A re-entrant duplicate callback
        // therefore cannot emit a second event.
        policyState = transition.state
        when (transition.transportEffect) {
            TransportLifecycleEffect.Foregrounded -> onTransportForegrounded()
            TransportLifecycleEffect.Backgrounded -> onTransportBackgrounded()
            null -> Unit
        }
    }
}

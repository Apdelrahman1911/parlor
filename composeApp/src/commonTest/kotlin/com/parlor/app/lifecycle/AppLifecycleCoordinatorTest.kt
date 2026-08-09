package com.parlor.app.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLifecycleCoordinatorTest {
    @Test
    fun inactiveCoversPrivateContentWithoutChangingTransportState() {
        val foreground = reduceAppLifecycle(
            previous = AppLifecyclePolicyState(),
            visibility = AppVisibility.Active,
        )
        val inactive = reduceAppLifecycle(
            previous = foreground.state,
            visibility = AppVisibility.Inactive,
        )

        assertEquals(TransportLifecycleEffect.Foregrounded, foreground.transportEffect)
        assertFalse(foreground.state.privateContentCovered)
        assertNull(inactive.transportEffect)
        assertEquals(TransportVisibility.Foreground, inactive.state.transportVisibility)
        assertTrue(inactive.state.privateContentCovered)
    }

    @Test
    fun backgroundAndForegroundEffectsAreIdempotent() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)

        coordinator.notifyActive()
        coordinator.notifyActive()
        coordinator.notifyInactive()
        coordinator.notifyInactive()
        coordinator.notifyBackgrounded()
        coordinator.notifyBackgrounded()
        coordinator.notifyActive()
        coordinator.notifyActive()

        assertEquals(
            listOf(
                TransportLifecycleEffect.Foregrounded,
                TransportLifecycleEffect.Backgrounded,
                TransportLifecycleEffect.Foregrounded,
            ),
            effects,
        )
    }

    @Test
    fun shortInactiveInterruptionNeverSuspendsTransport() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)

        coordinator.notifyActive()
        coordinator.notifyInactive()
        coordinator.notifyActive()

        assertEquals(listOf(TransportLifecycleEffect.Foregrounded), effects)
    }

    @Test
    fun initialInactiveDoesNotInventForegroundOrBackgroundEvidence() {
        val initial = reduceAppLifecycle(
            previous = AppLifecyclePolicyState(),
            visibility = AppVisibility.Inactive,
        )

        assertNull(initial.transportEffect)
        assertEquals(TransportVisibility.Unknown, initial.state.transportVisibility)
        assertTrue(initial.state.privateContentCovered)
    }

    @Test
    fun backgroundBeforeFirstActiveStillProducesAConsistentSuspendResumePair() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)

        coordinator.notifyInactive()
        coordinator.notifyBackgrounded()
        coordinator.notifyActive()

        assertEquals(
            listOf(
                TransportLifecycleEffect.Backgrounded,
                TransportLifecycleEffect.Foregrounded,
            ),
            effects,
        )
    }

    @Test
    fun inactiveThenBackgroundSuspendsExactlyOnceAndActiveResumesExactlyOnce() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)

        coordinator.notifyActive()
        coordinator.notifyInactive()
        coordinator.notifyBackgrounded()
        coordinator.notifyInactive()
        coordinator.notifyBackgrounded()
        coordinator.notifyActive()

        assertEquals(
            listOf(
                TransportLifecycleEffect.Foregrounded,
                TransportLifecycleEffect.Backgrounded,
                TransportLifecycleEffect.Foregrounded,
            ),
            effects,
        )
    }

    @Test
    fun processTrackerIgnoresDuplicateOwnerCallbacks() {
        val effects = mutableListOf<String>()
        val tracker = processTrackerRecording(effects)
        val activity = Any()

        tracker.ownerStarted(activity)
        tracker.ownerStarted(activity)
        tracker.ownerStopped(activity, changingConfigurations = false)
        tracker.ownerStopped(activity, changingConfigurations = false)

        assertEquals(listOf("foreground", "background"), effects)
    }

    @Test
    fun processTrackerDoesNotFlapDuringConfigurationReplacement() {
        val effects = mutableListOf<String>()
        val tracker = processTrackerRecording(effects)
        val oldActivity = Any()
        val replacementActivity = Any()

        tracker.ownerStarted(oldActivity)
        tracker.ownerStopped(oldActivity, changingConfigurations = true)
        tracker.ownerStarted(replacementActivity)
        tracker.ownerStopped(replacementActivity, changingConfigurations = false)

        assertEquals(listOf("foreground", "background"), effects)
    }

    @Test
    fun processTrackerKeepsProcessForegroundedWhileAnotherOwnerIsStarted() {
        val effects = mutableListOf<String>()
        val tracker = processTrackerRecording(effects)
        val firstActivity = Any()
        val secondActivity = Any()

        tracker.ownerStarted(firstActivity)
        tracker.ownerStarted(secondActivity)
        tracker.ownerStopped(firstActivity, changingConfigurations = false)
        tracker.ownerStopped(secondActivity, changingConfigurations = false)

        assertEquals(listOf("foreground", "background"), effects)
    }

    private fun coordinatorRecording(
        effects: MutableList<TransportLifecycleEffect>,
    ): AppLifecycleCoordinator = AppLifecycleCoordinator(
        onTransportForegrounded = { effects += TransportLifecycleEffect.Foregrounded },
        onTransportBackgrounded = { effects += TransportLifecycleEffect.Backgrounded },
    )

    private fun processTrackerRecording(
        effects: MutableList<String>,
    ): ProcessVisibilityTracker<Any> = ProcessVisibilityTracker(
        onProcessForegrounded = { effects += "foreground" },
        onProcessBackgrounded = { effects += "background" },
    )
}

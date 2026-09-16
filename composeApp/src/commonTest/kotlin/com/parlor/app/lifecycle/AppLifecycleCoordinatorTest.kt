package com.parlor.app.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLifecycleCoordinatorTest {
    @Test
    fun androidForegroundAndResumeWithoutFocusKeepPrivateContentCovered() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)
        coordinator.notifyForegrounded()
        assertTrue(coordinator.visibility.value.privateContentCovered)
        coordinator.notifyWindowFocus(focused = false, resumed = true)
        assertTrue(coordinator.visibility.value.privateContentCovered)
        coordinator.notifyWindowFocus(focused = true, resumed = true)
        assertFalse(coordinator.visibility.value.privateContentCovered)

        coordinator.notifyWindowFocus(focused = false, resumed = true)
        val interruptedEpoch = coordinator.visibility.value.privacyEpoch
        coordinator.notifyForegrounded()
        coordinator.notifyWindowFocus(focused = false, resumed = true)
        assertTrue(coordinator.visibility.value.privateContentCovered)
        assertEquals(interruptedEpoch, coordinator.visibility.value.privacyEpoch)
        assertEquals(listOf(TransportLifecycleEffect.Foregrounded), effects)
    }

    @Test
    fun focusCannotRevealAnActivityBeforeItResumesOrResumeBackgroundTransport() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)
        coordinator.notifyForegrounded()
        coordinator.notifyBackgrounded()
        coordinator.notifyWindowFocus(focused = true, resumed = false)

        assertTrue(coordinator.visibility.value.privateContentCovered)
        assertEquals(TransportVisibility.Background, coordinator.visibility.value.transportVisibility)
        assertEquals(
            listOf(TransportLifecycleEffect.Foregrounded, TransportLifecycleEffect.Backgrounded),
            effects,
        )
    }

    @Test
    fun foregroundSnapshotRetainsConcealmentAfterBriefInterruption() {
        val effects = mutableListOf<TransportLifecycleEffect>()
        val coordinator = coordinatorRecording(effects)
        assertTrue(coordinator.visibility.value.privateContentCovered)
        coordinator.notifyActive()
        val before = coordinator.visibility.value.privacyEpoch
        coordinator.notifyInactive()
        coordinator.notifyActive()

        assertFalse(coordinator.visibility.value.privateContentCovered)
        assertEquals(before + 1L, coordinator.visibility.value.privacyEpoch)
        assertEquals(listOf(TransportLifecycleEffect.Foregrounded), effects)
    }

    @Test
    fun duplicateVisibilityCallbacksDoNotAdvancePrivacyEpoch() {
        val coordinator = coordinatorRecording(mutableListOf())
        coordinator.notifyActive()
        coordinator.notifyInactive()
        val interrupted = coordinator.visibility.value
        coordinator.notifyInactive()
        assertEquals(interrupted, coordinator.visibility.value)
        coordinator.notifyBackgrounded()
        val background = coordinator.visibility.value
        coordinator.notifyBackgrounded()
        coordinator.notifyActive()

        assertEquals(interrupted.privacyEpoch + 1L, background.privacyEpoch)
        assertEquals(background.privacyEpoch, coordinator.visibility.value.privacyEpoch)
    }

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

package com.parlor.app.storage

import android.test.InstrumentationTestCase

/** Decision regressions only; the process matrix separately exercises actual Home. */
@Suppress("DEPRECATION")
class AndroidRecoveryRevealRuntimeTest : InstrumentationTestCase() {
    fun testVisibleTargetTakesPriorityWithoutAcquiringAScrollNode() {
        val result = androidRecoveryRevealObservation(
            target = { "target" },
            scroll = { error("Do not acquire or act on a scroll node when the target is visible") },
        )
        assertEquals(AndroidRecoveryReveal("target", needsScroll = false), result)
    }

    fun testTargetSettlingAtTheTopDoesNotRequireAnUnavailableBackwardAction() {
        var ready = false
        var targetReads = 0
        var scrollReads = 0
        val target = { targetReads++; if (ready) "target" else null }
        val scroll = { scrollReads++; null }
        assertNull(androidRecoveryRevealObservation(target, scroll))
        ready = true
        assertEquals(AndroidRecoveryReveal("target", needsScroll = false),
            androidRecoveryRevealObservation(target, scroll))
        assertEquals(2, targetReads)
        assertEquals(1, scrollReads)
    }

    fun testOffscreenTargetSelectsOnlyTheAdvertisedScrollNode() {
        val result = androidRecoveryRevealObservation(target = { null }, scroll = { "list" })
        assertEquals(AndroidRecoveryReveal("list", needsScroll = true), result)
    }

    fun testNeitherTargetNorScrollDoesNotFabricateReadiness() {
        assertNull(androidRecoveryRevealObservation<String>(target = { null }, scroll = { null }))
    }
}

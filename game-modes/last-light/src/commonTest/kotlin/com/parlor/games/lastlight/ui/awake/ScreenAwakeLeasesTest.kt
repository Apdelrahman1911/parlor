package com.parlor.games.lastlight.ui.awake

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenAwakeLeasesTest {
    @Test
    fun overlappingOwnersAndDuplicateDisposalCannotReleaseTheCurrentMatch() {
        var awake = false
        val leases = ScreenAwakeLeases({ awake }, { awake = it })
        val old = Any()
        val replacement = Any()
        leases.acquire(old)
        leases.acquire(old)
        leases.acquire(replacement)
        leases.release(old)
        leases.release(old)
        assertTrue(awake)
        leases.release(replacement)
        assertFalse(awake)
    }

    @Test
    fun priorNativeSettingIsRestoredRatherThanAssumedFalse() {
        var awake = true
        val leases = ScreenAwakeLeases({ awake }, { awake = it })
        val owner = Any()
        leases.acquire(owner)
        leases.release(owner)
        assertTrue(awake)
        awake = false
        leases.acquire(owner)
        leases.release(owner)
        assertFalse(awake)
    }
}

package com.parlor.session.multidevice

import com.parlor.core.ids.PlayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundConnectionProbeTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")

    @Test
    fun onlyFreshChallengeFromEveryExpectedSeatCanComplete() = runTest {
        val probe = ForegroundConnectionProbe { "fresh-probe-00000000000000001" }
        var challenge: String? = null
        val checking = async { probe.check(setOf(alice, bob)) { challenge = it; true } }
        runCurrent()
        assertFalse(probe.accept(alice, "old-probe-000000000000000001", 2L))
        assertFalse(probe.accept(PlayerId("outsider"), checkNotNull(challenge), 2L))
        assertFalse(probe.accept(alice, checkNotNull(challenge), -1L))
        assertTrue(probe.accept(alice, checkNotNull(challenge), 2L))
        runCurrent()
        assertFalse(checking.isCompleted)
        assertTrue(probe.accept(bob, checkNotNull(challenge), 3L))
        assertEquals(mapOf(alice to 2L, bob to 3L), checking.await())
    }

    @Test
    fun canceledProbeAndLateReplyCannotCompleteReplacement() = runTest {
        var id = 0
        val probe = ForegroundConnectionProbe { "probe-${++id}" }
        val first = async { probe.check(setOf(alice)) { true } }
        runCurrent()
        first.cancelAndJoin()
        val second = async { probe.check(setOf(alice)) { true } }
        runCurrent()
        assertFalse(probe.accept(alice, "probe-1", 0L))
        assertFalse(second.isCompleted)
        assertTrue(probe.accept(alice, "probe-2", 1L))
        assertEquals(mapOf(alice to 1L), second.await())
    }

    @Test
    fun closingOrFailedSendCannotLeaveAnUnboundedWaiter() = runTest {
        val probe = ForegroundConnectionProbe { "probe" }
        assertNull(probe.check(setOf(alice)) { false })
        val checking = async { probe.check(setOf(alice)) { true } }
        runCurrent()
        probe.close()
        assertNull(checking.await())
        assertNull(probe.check(setOf(alice)) { error("closed probe must not send") })
        assertFalse(probe.accept(alice, "probe", 0L))
    }
}

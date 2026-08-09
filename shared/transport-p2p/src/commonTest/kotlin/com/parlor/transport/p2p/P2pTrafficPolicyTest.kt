package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P2pTrafficPolicyTest {

    @Test
    fun queue_memory_budgets_are_derived_from_capacity_and_directional_frame_limits() {
        assertEquals(
            P2pTrafficLimits.HOST_APPLICATION_QUEUE_CAPACITY *
                P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES,
            P2pTrafficLimits.HOST_APPLICATION_QUEUE_MAX_BYTES,
        )
        assertEquals(
            P2pTrafficLimits.PEER_APPLICATION_QUEUE_CAPACITY *
                P2pTrafficLimits.MAX_HOST_TO_PEER_FRAME_BYTES,
            P2pTrafficLimits.PEER_APPLICATION_QUEUE_MAX_BYTES,
        )
    }

    @Test
    fun sustained_flood_is_dropped_then_disconnects_only_the_offending_session() {
        val guard = InboundTrafficGuard(
            maxFrameBytes = P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES,
            nowMillis = 0L,
        )
        repeat(P2pTrafficLimits.INBOUND_BURST_FRAMES) {
            assertEquals(InboundTrafficDecision.Accept, guard.inspectFrame(128, 0L))
        }
        assertEquals(InboundTrafficDecision.Drop, guard.inspectFrame(128, 0L))
        assertEquals(InboundTrafficDecision.Drop, guard.inspectFrame(128, 0L))
        assertEquals(InboundTrafficDecision.Disconnect, guard.inspectFrame(128, 0L))
    }

    @Test
    fun traffic_budget_refills_and_violation_strikes_expire_after_cooldown() {
        val guard = InboundTrafficGuard(
            maxFrameBytes = P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES,
            nowMillis = 0L,
        )
        repeat(P2pTrafficLimits.INBOUND_BURST_FRAMES) {
            guard.inspectFrame(128, 0L)
        }
        assertEquals(InboundTrafficDecision.Drop, guard.inspectFrame(128, 0L))
        assertEquals(
            InboundTrafficDecision.Accept,
            guard.inspectFrame(128, P2pTrafficLimits.TRAFFIC_VIOLATION_COOLDOWN_MS),
        )
        assertEquals(
            InboundTrafficDecision.Drop,
            guard.inspectFrame(
                P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES + 1,
                P2pTrafficLimits.TRAFFIC_VIOLATION_COOLDOWN_MS,
            ),
        )
    }

    @Test
    fun long_idle_period_never_accumulates_more_than_one_burst() {
        val guard = InboundTrafficGuard(
            maxFrameBytes = P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES,
            nowMillis = 0L,
        )
        repeat(P2pTrafficLimits.INBOUND_BURST_FRAMES) {
            assertEquals(InboundTrafficDecision.Accept, guard.inspectFrame(128, 0L))
        }
        val afterLongIdle = 24L * 60L * 60L * 1_000L
        repeat(P2pTrafficLimits.INBOUND_BURST_FRAMES) {
            assertEquals(
                InboundTrafficDecision.Accept,
                guard.inspectFrame(128, afterLongIdle),
            )
        }
        assertEquals(
            InboundTrafficDecision.Drop,
            guard.inspectFrame(128, afterLongIdle),
        )
    }

    @Test
    fun admission_limiter_allows_legitimate_room_burst_and_throttles_one_identity() {
        val limiter = AdmissionAttemptLimiter(nowMillis = 0L)
        repeat(P2pTrafficLimits.MAX_PENDING_ADMISSION_REQUESTS) { index ->
            assertTrue(limiter.tryAcquire("peer-$index", 0L))
        }

        assertTrue(limiter.tryAcquire("attacker", 0L))
        assertTrue(limiter.tryAcquire("attacker", 0L))
        assertTrue(limiter.tryAcquire("attacker", 0L))
        assertFalse(limiter.tryAcquire("attacker", 0L))
        assertTrue(
            limiter.tryAcquire(
                "attacker",
                P2pTrafficLimits.ADMISSION_PER_PEER_REFILL_MS,
            ),
        )
    }

    @Test
    fun rate_limited_identity_does_not_consume_global_admission_credit() {
        val limiter = AdmissionAttemptLimiter(nowMillis = 0L)

        repeat(P2pTrafficLimits.ADMISSION_PER_PEER_BURST) {
            assertTrue(limiter.tryAcquire("attacker", 0L))
        }
        repeat(P2pTrafficLimits.ADMISSION_GLOBAL_BURST * 2) {
            assertFalse(limiter.tryAcquire("attacker", 0L))
        }

        assertTrue(limiter.tryAcquire("legitimate-peer", 0L))
    }

    @Test
    fun admission_identity_bookkeeping_is_bounded_and_pruned() {
        val limiter = AdmissionAttemptLimiter(nowMillis = 0L)
        var now = 0L
        repeat(P2pTrafficLimits.MAX_TRACKED_ADMISSION_IDENTITIES) { index ->
            assertTrue(limiter.tryAcquire("peer-$index", now))
            now += P2pTrafficLimits.ADMISSION_GLOBAL_REFILL_MS
        }
        assertEquals(
            P2pTrafficLimits.MAX_TRACKED_ADMISSION_IDENTITIES,
            limiter.trackedIdentityCount(),
        )
        assertFalse(limiter.tryAcquire("overflow", now))

        now += P2pTrafficLimits.ADMISSION_IDENTITY_RETENTION_MS
        assertTrue(limiter.tryAcquire("fresh", now))
        assertEquals(1, limiter.trackedIdentityCount())
    }
}

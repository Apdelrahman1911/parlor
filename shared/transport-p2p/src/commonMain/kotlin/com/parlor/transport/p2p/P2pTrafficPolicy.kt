package com.parlor.transport.p2p

import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import com.parlor.networking.protocol.MAX_CONTROL_PAYLOAD_BYTES
import com.parlor.networking.protocol.MAX_ROOM_FRAME_BYTES

/**
 * Application-level resource policy layered on top of P2pKit's encrypted
 * sessions. P2pKit bounds transport frames; Parlor additionally bounds the
 * decoded work and memory one room or modified same-app peer can consume.
 */
internal object P2pTrafficLimits {
    /** A peer command plus its CBOR envelope/control metadata. */
    const val MAX_PEER_TO_HOST_FRAME_BYTES: Int =
        MAX_COMMAND_PAYLOAD_BYTES + MAX_CONTROL_PAYLOAD_BYTES

    /** Host snapshots are the only application frames allowed to reach this ceiling. */
    const val MAX_HOST_TO_PEER_FRAME_BYTES: Int = MAX_ROOM_FRAME_BYTES

    /**
     * Host queue: 16 * 40 KiB = 655,360 bytes of encoded-frame-equivalent
     * payload, plus bounded object/envelope overhead.
     */
    const val HOST_APPLICATION_QUEUE_CAPACITY: Int = 16
    const val HOST_APPLICATION_QUEUE_MAX_BYTES: Int =
        HOST_APPLICATION_QUEUE_CAPACITY * MAX_PEER_TO_HOST_FRAME_BYTES

    /**
     * Peer queue: 8 * 272 KiB = 2,228,224 bytes of encoded-frame-equivalent
     * payload, plus bounded object/envelope overhead.
     */
    const val PEER_APPLICATION_QUEUE_CAPACITY: Int = 8
    const val PEER_APPLICATION_QUEUE_MAX_BYTES: Int =
        PEER_APPLICATION_QUEUE_CAPACITY * MAX_HOST_TO_PEER_FRAME_BYTES

    /** Legitimate gameplay is human-paced; this still permits snapshot/command bursts. */
    const val INBOUND_BURST_FRAMES: Int = 32
    const val INBOUND_SUSTAINED_FRAMES_PER_SECOND: Int = 16
    const val MAX_TRAFFIC_VIOLATIONS: Int = 3
    const val TRAFFIC_VIOLATION_COOLDOWN_MS: Long = 10_000L

    /** All 17 supported remote seats may request admission concurrently. */
    const val MAX_PENDING_ADMISSION_REQUESTS: Int = 17
    /** Allows a replacement connection and short races while all seats are occupied. */
    const val SESSION_ADMISSION_HEADROOM: Int = 4
    const val MAX_TRACKED_SESSIONS: Int = 21

    /** One identity gets three fresh attempts, then one additional attempt per 10 seconds. */
    const val ADMISSION_PER_PEER_BURST: Int = 3
    const val ADMISSION_PER_PEER_REFILL_MS: Long = 10_000L
    /** A room accepts 32 fresh attempts at once, then one per second across all identities. */
    const val ADMISSION_GLOBAL_BURST: Int = 32
    const val ADMISSION_GLOBAL_REFILL_MS: Long = 1_000L
    /** Bounds identity bookkeeping even if a modified client rotates identities. */
    const val MAX_TRACKED_ADMISSION_IDENTITIES: Int = 128
    const val ADMISSION_IDENTITY_RETENTION_MS: Long = 5L * 60L * 1_000L
}

internal enum class InboundTrafficDecision {
    Accept,
    Drop,
    Disconnect,
}

/** Deterministic token bucket plus a bounded violation/cooldown policy. */
internal class InboundTrafficGuard(
    private val maxFrameBytes: Int,
    nowMillis: Long,
) {
    private val frameBucket = TokenBucket(
        capacity = P2pTrafficLimits.INBOUND_BURST_FRAMES,
        refillTokens = P2pTrafficLimits.INBOUND_SUSTAINED_FRAMES_PER_SECOND,
        refillPeriodMs = 1_000L,
        nowMillis = nowMillis,
    )
    private var violations: Int = 0
    private var lastViolationAtMillis: Long? = null

    fun inspectFrame(frameBytes: Int, nowMillis: Long): InboundTrafficDecision {
        if (frameBytes <= 0 || frameBytes > maxFrameBytes) {
            return violation(nowMillis)
        }
        return if (frameBucket.tryTake(nowMillis)) {
            InboundTrafficDecision.Accept
        } else {
            violation(nowMillis)
        }
    }

    fun malformedFrame(nowMillis: Long): InboundTrafficDecision = violation(nowMillis)

    private fun violation(nowMillis: Long): InboundTrafficDecision {
        val previous = lastViolationAtMillis
        if (
            previous == null ||
            nowMillis - previous >= P2pTrafficLimits.TRAFFIC_VIOLATION_COOLDOWN_MS ||
            nowMillis < previous
        ) {
            violations = 0
        }
        lastViolationAtMillis = nowMillis
        violations += 1
        return if (violations >= P2pTrafficLimits.MAX_TRAFFIC_VIOLATIONS) {
            InboundTrafficDecision.Disconnect
        } else {
            InboundTrafficDecision.Drop
        }
    }
}

/**
 * Bounds fresh admission/resume attempts. Retransmissions on the same pending
 * physical session are handled idempotently by the room and do not call this
 * limiter.
 */
internal class AdmissionAttemptLimiter(nowMillis: Long) {
    private data class PeerBudget(
        val bucket: TokenBucket,
        var lastSeenAtMillis: Long,
    )

    private val global = TokenBucket(
        capacity = P2pTrafficLimits.ADMISSION_GLOBAL_BURST,
        refillTokens = 1,
        refillPeriodMs = P2pTrafficLimits.ADMISSION_GLOBAL_REFILL_MS,
        nowMillis = nowMillis,
    )
    private val peers = mutableMapOf<String, PeerBudget>()

    fun tryAcquire(peerKey: String, nowMillis: Long): Boolean {
        prune(nowMillis)
        val existing = peers[peerKey]
        if (
            existing == null &&
            peers.size >= P2pTrafficLimits.MAX_TRACKED_ADMISSION_IDENTITIES
        ) {
            return false
        }
        val budget = existing ?: PeerBudget(
            bucket = TokenBucket(
                capacity = P2pTrafficLimits.ADMISSION_PER_PEER_BURST,
                refillTokens = 1,
                refillPeriodMs = P2pTrafficLimits.ADMISSION_PER_PEER_REFILL_MS,
                nowMillis = nowMillis,
            ),
            lastSeenAtMillis = nowMillis,
        )
        budget.lastSeenAtMillis = nowMillis
        // Admission is one transaction across both budgets. Consuming the
        // room-wide token before discovering that this identity is exhausted
        // lets one attacker drain every global token with requests that could
        // never be accepted. Refill/check both first, then commit both takes.
        if (!budget.bucket.canTake(nowMillis) || !global.canTake(nowMillis)) {
            return false
        }
        budget.bucket.take()
        global.take()
        if (existing == null) peers[peerKey] = budget
        return true
    }

    internal fun trackedIdentityCount(): Int = peers.size

    private fun prune(nowMillis: Long) {
        peers.entries.removeAll { (_, budget) ->
            nowMillis < budget.lastSeenAtMillis ||
                nowMillis - budget.lastSeenAtMillis >=
                P2pTrafficLimits.ADMISSION_IDENTITY_RETENTION_MS
        }
    }
}

/** Fixed-point token bucket that preserves fractional credits without floating point. */
private class TokenBucket(
    private val capacity: Int,
    private val refillTokens: Int,
    private val refillPeriodMs: Long,
    nowMillis: Long,
) {
    private val capacityUnits: Long = capacity.toLong() * refillPeriodMs
    private var availableUnits: Long = capacityUnits
    private var lastRefillAtMillis: Long = nowMillis

    fun tryTake(nowMillis: Long): Boolean {
        if (!canTake(nowMillis)) return false
        take()
        return true
    }

    fun canTake(nowMillis: Long): Boolean {
        refill(nowMillis)
        return availableUnits >= refillPeriodMs
    }

    fun take() {
        check(availableUnits >= refillPeriodMs) { "Token bucket credit was not reserved" }
        availableUnits -= refillPeriodMs
    }

    private fun refill(nowMillis: Long) {
        if (nowMillis < lastRefillAtMillis) {
            availableUnits = capacityUnits
            lastRefillAtMillis = nowMillis
            return
        }
        val elapsed = nowMillis - lastRefillAtMillis
        if (elapsed == 0L) return
        val replenishedUnits = if (elapsed > capacityUnits / refillTokens) {
            capacityUnits
        } else {
            elapsed * refillTokens
        }
        availableUnits = (availableUnits + replenishedUnits).coerceAtMost(capacityUnits)
        // Excess idle credit is deliberately discarded at capacity; it must
        // never permit more than one burst after a long idle period.
        lastRefillAtMillis = nowMillis
    }
}

package com.parlor.session.multidevice

import com.parlor.networking.room.PeerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable peer reachability state; unlike an event, a late UI collector cannot miss it. */
data class PeerConnectionState(
    val hostLost: Boolean = false,
    val selfOffline: Boolean = false,
)

/**
 * Reduces transport reachability events into durable state and owns the one
 * host-loss grace deadline. Duplicate HostLost events are idempotent and must
 * never extend that deadline.
 */
class PeerConnectionTracker(
    scope: CoroutineScope,
    private val hostLostTimeoutMs: Long,
    private val onHostLossExpired: suspend () -> Unit,
) {
    /**
     * Timers belong to this tracker, not to the caller's whole session scope.
     * Cancelling this child job makes close-vs-callback races safe: even when
     * a callback passed the closed check immediately before [close], any job
     * it launches is born cancelled and cannot outlive the tracker.
     */
    private val trackerJob = SupervisorJob(scope.coroutineContext[Job])
    private val trackerScope = CoroutineScope(scope.coroutineContext + trackerJob)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PeerConnectionState())
    val state: StateFlow<PeerConnectionState> = _state.asStateFlow()

    // Compatibility/diagnostic edge stream. Correct UI rendering must use
    // [state], so a bounded best-effort event stream is never authoritative.
    private val _events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = EVENT_CAPACITY)
    val events: SharedFlow<PeerEvent> = _events.asSharedFlow()

    private var hostLossJob: Job? = null
    private var hostLossGeneration = 0L
    private var closed = false

    init {
        require(hostLostTimeoutMs > 0L) { "hostLostTimeoutMs must be positive" }
    }

    suspend fun handle(event: PeerEvent) {
        val emitted = mutableListOf<PeerEvent>()
        mutex.withLock {
            if (closed) return
            when (event) {
                PeerEvent.HostLost -> {
                    if (!_state.value.hostLost) {
                        _state.value = _state.value.copy(hostLost = true)
                        emitted += PeerEvent.HostLost
                        val generation = ++hostLossGeneration
                        hostLossJob?.cancel()
                        hostLossJob = trackerScope.launch {
                            delay(hostLostTimeoutMs)
                            val expired = mutex.withLock {
                                !closed &&
                                    generation == hostLossGeneration &&
                                    _state.value.hostLost
                            }
                            if (expired) onHostLossExpired()
                        }
                    }
                }
                PeerEvent.HostRestored -> {
                    if (_state.value.hostLost) {
                        _state.value = _state.value.copy(hostLost = false)
                        emitted += PeerEvent.HostRestored
                        hostLossGeneration++
                        hostLossJob?.cancel()
                        hostLossJob = null
                    }
                    if (_state.value.selfOffline) {
                        _state.value = _state.value.copy(selfOffline = false)
                        emitted += PeerEvent.SelfOnline
                    }
                }
                PeerEvent.SelfOffline -> {
                    if (!_state.value.selfOffline) {
                        _state.value = _state.value.copy(selfOffline = true)
                        emitted += PeerEvent.SelfOffline
                    }
                }
                PeerEvent.SelfOnline -> {
                    if (_state.value.selfOffline) {
                        _state.value = _state.value.copy(selfOffline = false)
                        emitted += PeerEvent.SelfOnline
                    }
                }
                is PeerEvent.AdmissionRequested,
                is PeerEvent.PeerLeft,
                is PeerEvent.PeerReconnected,
                is PeerEvent.PeerJoined -> Unit
            }
        }
        emitted.forEach { _events.tryEmit(it) }
    }

    suspend fun markSelfOffline() = handle(PeerEvent.SelfOffline)

    suspend fun markSelfOnline() = handle(PeerEvent.SelfOnline)

    /**
     * Seals the tracker against new callbacks and waits for every timer/callback
     * child to finish. Returning before an already-started expiry callback had
     * stopped allowed an old peer session to race teardown and affect its owner.
     */
    suspend fun close() {
        val shouldJoin = mutex.withLock {
            if (closed) {
                false
            } else {
                closed = true
                hostLossGeneration++
                hostLossJob = null
                true
            }
        }
        if (shouldJoin) trackerJob.cancelAndJoin()
    }

    private companion object {
        const val EVENT_CAPACITY = 16
    }
}

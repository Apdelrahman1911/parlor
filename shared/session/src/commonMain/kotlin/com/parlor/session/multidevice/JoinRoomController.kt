package com.parlor.session.multidevice

import com.parlor.core.result.Result
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.JoinError
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State machine for the Join Room screen. Lives between the transport
 * (discovery + join) and the UI (state-driven rendering).
 *
 * ### Why a controller
 *
 * The previous join path called `transport.join(...)` directly inside a
 * `LaunchedEffect` and threw `NetError.toString()` at the screen. That
 * leaks transport internals to users, loses room-discovery affordances,
 * and bakes the join state machine into Compose. This class:
 *
 *  - centralises the Scanning → Found / Empty → Joining → Failed flow
 *    in pure Kotlin (testable, multiplatform),
 *  - maps [NetError] to [JoinError] so the UI gets typed failures it
 *    can localize per variant, never raw exception text,
 *  - exposes a single [state] StateFlow for Compose to drive against.
 *
 * UI integration lands in 9H-8; this batch is the controller + tests.
 */
class JoinRoomController(
    private val transport: RoomTransport,
    private val scope: CoroutineScope,
    /**
     * How long to wait for at least one room before declaring [JoinState.Empty].
     * Real transports tune this; tests pass a small value.
     */
    private val scanEmptyTimeoutMs: Long = DEFAULT_SCAN_EMPTY_TIMEOUT_MS,
    /** Predicate for whether a typed code is well-formed. */
    private val codeValidator: (String) -> Boolean = ::defaultCodeValidator,
) {

    private val _state: MutableStateFlow<JoinState> = MutableStateFlow(JoinState.Idle)

    /** UI subscribes to this. */
    val state: StateFlow<JoinState> = _state.asStateFlow()

    private var discoveryJob: Job? = null
    private var emptyWatchdog: Job? = null

    /**
     * Begin discovery. Transitions to [JoinState.Scanning]; emits
     * [JoinState.Found] as soon as the transport reports any rooms, or
     * [JoinState.Empty] if [scanEmptyTimeoutMs] elapses with no rooms.
     */
    fun startScanning() {
        if (_state.value is JoinState.Joining) return
        cancelScanning()
        _state.value = JoinState.Scanning
        emptyWatchdog = scope.launch {
            delay(scanEmptyTimeoutMs)
            // Only flip to Empty if we're still Scanning — a Found result
            // in flight would have advanced us already.
            if (_state.value is JoinState.Scanning) {
                _state.value = JoinState.Empty
            }
        }
        discoveryJob = scope.launch {
            transport.discoverRooms().collect { rooms ->
                if (rooms.isNotEmpty()) {
                    emptyWatchdog?.cancel()
                    _state.value = JoinState.Found(rooms)
                } else if (_state.value is JoinState.Found) {
                    // All rooms vanished — back to Scanning (with a new
                    // empty-watchdog so the screen can transition to Empty
                    // again if needed).
                    _state.value = JoinState.Scanning
                    emptyWatchdog?.cancel()
                    emptyWatchdog = scope.launch {
                        delay(scanEmptyTimeoutMs)
                        if (_state.value is JoinState.Scanning) {
                            _state.value = JoinState.Empty
                        }
                    }
                }
            }
        }
    }

    /** Stop discovery and reset to [JoinState.Idle]. */
    fun stopScanning() {
        cancelScanning()
        _state.value = JoinState.Idle
    }

    /** Switch into manual-entry mode (user typed a code by hand). */
    fun enterManualEntry() {
        cancelScanning()
        _state.value = JoinState.Manual
    }

    /**
     * Attempt to join a room. Validates the code first, then calls the
     * transport. On success the controller surfaces [JoinState.Joined]
     * carrying the [LocalRoom]; the host of this controller takes
     * ownership of that room.
     */
    fun join(code: String, displayName: String, onJoined: (LocalRoom) -> Unit) {
        if (!codeValidator(code)) {
            _state.value = JoinState.Failed(JoinError.WrongCode)
            return
        }
        cancelScanning()
        _state.value = JoinState.Joining(code)
        scope.launch {
            when (val r = transport.join(code, displayName)) {
                is Result.Success -> {
                    _state.value = JoinState.Joined(code)
                    onJoined(r.data)
                }
                is Result.Failure -> {
                    _state.value = JoinState.Failed(mapNetError(r.error))
                }
            }
        }
    }

    /** Reset from [JoinState.Failed] back to manual entry so the user can retry. */
    fun retry() {
        _state.value = JoinState.Manual
    }

    private fun cancelScanning() {
        discoveryJob?.cancel(); discoveryJob = null
        emptyWatchdog?.cancel(); emptyWatchdog = null
    }

    private companion object {
        const val DEFAULT_SCAN_EMPTY_TIMEOUT_MS: Long = 8_000L

        /**
         * Project's room codes are 6 chars, alphanumeric, no `0/O/1/I` (per
         * `P2pKitRoomTransport.ROOM_CODE_ALPHABET`). The validator is a soft
         * filter — the host rejects invalid codes via [JoinError.RoomNotFound]
         * if anything slips through.
         */
        fun defaultCodeValidator(code: String): Boolean {
            if (code.length != 6) return false
            val allowed = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return code.all { it in allowed }
        }
    }
}

/**
 * Map a transport-level [NetError] to the user-facing [JoinError]
 * vocabulary. Kept as a top-level function so tests can verify the
 * mapping directly without spinning a controller.
 */
fun mapNetError(error: NetError): JoinError = when (error) {
    NetError.NotConnected -> JoinError.HostUnreachable
    NetError.Timeout -> JoinError.ConnectionTimeout
    NetError.PayloadTooLarge -> JoinError.Generic
    NetError.WrongCode -> JoinError.WrongCode
    NetError.HostDeclined -> JoinError.Generic
    NetError.RoomFull -> JoinError.RoomFull
    NetError.SessionStarted -> JoinError.GameAlreadyStarted
    NetError.IncompatibleProtocol -> JoinError.Generic
    NetError.RateLimited -> JoinError.Generic
    NetError.Unauthorized -> JoinError.Generic
    is NetError.TransportFailure -> JoinError.Generic
}

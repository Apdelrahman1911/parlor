package com.parlor.transport.p2p

import com.parlor.networking.room.ForegroundConnectionValidator
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal interface RetainedRoomConnection {
    val health: Flow<Boolean>
}

/** One bounded, never-retired set of authenticated sockets; not OS background execution. */
internal class BackgroundConnectionRetention(private val nowMillis: () -> Long) {
    private class Registration(val validator: ForegroundConnectionValidator)

    private sealed interface Presence {
        data object Foreground : Presence
        data object Suspended : Presence
    }

    private class Retained(
        val startedAt: Long,
        val deadline: Long,
        val sessions: List<P2pSession>,
        val registration: Registration,
    ) : Presence, RetainedRoomConnection {
        val valid = MutableStateFlow(true)
        private val connected = combine(sessions.map { it.state }) { states ->
            states.all { it == ConnectionState.Connected }
        }
        override val health = combine(connected, valid) { connected, valid -> connected && valid }

        fun healthy(): Boolean = valid.value && sessions.all { it.state.value == ConnectionState.Connected }
    }

    private val presence = MutableStateFlow<Presence>(Presence.Foreground)
    private val transitionMutex = Mutex()
    private val registration = MutableStateFlow<Registration?>(null)
    private val _foregroundReady = MutableStateFlow(true)
    val foregroundReady = _foregroundReady.asStateFlow()

    val acceptsLocalCommands: Boolean get() = presence.value == Presence.Foreground
    val acceptsRemoteCommands: Boolean get() = when (val current = presence.value) {
        Presence.Foreground -> true
        Presence.Suspended -> false
        is Retained -> eligible(current, nowMillis())
    }
    val isBackgrounded: Boolean get() = presence.value != Presence.Foreground

    fun register(value: ForegroundConnectionValidator): () -> Unit {
        val owner = Registration(value)
        registration.value = owner
        return { registration.compareAndSet(owner, null) }
    }

    /** Called under the room's membership lock, only for an otherwise healthy room. */
    suspend fun retain(startedAt: Long, deadline: Long, sessions: List<P2pSession>): RetainedRoomConnection? = transitionMutex.withLock {
        suspendLocked()
        val owner = registration.value?.takeIf { it.validator.ready } ?: return@withLock null
        if (sessions.isEmpty()) return@withLock null
        val retained = Retained(startedAt, deadline, sessions.toList(), owner)
        if (!eligible(retained, nowMillis())) return@withLock null
        presence.value = retained
        retained
    }

    /** A known loss is sticky even if the library immediately reports Connected again. */
    suspend fun connectionLost() = transitionMutex.withLock {
        (presence.value as? Retained)?.valid?.value = false
    }

    suspend fun resume(atEpochMillis: Long, beforeReady: suspend () -> Unit = {}): Boolean {
        val retained = presence.value as? Retained ?: return false
        val observedAt = maxOf(atEpochMillis, nowMillis())
        if (!eligible(retained, observedAt)) return false
        return withTimeoutOrNull(minOf(VALIDATION_TIMEOUT_MS, retained.deadline - observedAt)) {
            if (!retained.registration.validator.validate() || !eligible(retained, maxOf(atEpochMillis, nowMillis()))) {
                return@withTimeoutOrNull false
            }
            beforeReady()
            transitionMutex.withLock {
                if (!eligible(retained, maxOf(atEpochMillis, nowMillis()))) return@withLock false
                if (!presence.compareAndSet(retained, Presence.Foreground)) return@withLock false
                _foregroundReady.value = true
                true
            }
        } == true
    }

    suspend fun suspendNow() = transitionMutex.withLock { suspendLocked() }

    private fun suspendLocked() {
        (presence.value as? Retained)?.valid?.value = false
        presence.value = Presence.Suspended
        _foregroundReady.value = false
    }

    /** Hard recovery still separately gates commands through RoomLifecycleState. */
    suspend fun foregrounded() = transitionMutex.withLock {
        presence.value = Presence.Foreground
        _foregroundReady.value = true
    }

    private fun eligible(retained: Retained, observedAt: Long): Boolean =
        observedAt >= retained.startedAt && observedAt < retained.deadline &&
            retained.healthy() && registration.value === retained.registration && retained.registration.validator.ready

    private companion object {
        const val VALIDATION_TIMEOUT_MS = 2_000L
    }
}

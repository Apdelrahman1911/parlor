package com.parlor.engine.timer

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Timer-as-a-side-effect — the reducer remains pure by emitting timer-scheduling
 * events; the session controller drives this service in response.
 *
 * On each tick or expiration, the service feeds a `TimerTicked` / `TimerExpired`
 * action back into the reducer.
 */
interface TimerService {
    fun schedule(id: String, after: Duration): Flow<TimerEvent>
    fun cancel(id: String)
    fun pauseAll()
    fun resumeAll()
}

sealed interface TimerEvent {
    val timerId: String
    data class Tick(override val timerId: String, val remaining: Duration) : TimerEvent
    data class Expired(override val timerId: String) : TimerEvent
}

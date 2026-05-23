package com.parlor.games.whodunit.ui.timer

import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Real-time ticker for the discussion timer.
 *
 * Drives `TimerTicked` / `TimerExpired` once per [tickInterval] while a timer
 * with the given [timerId] is active and unpaused. Pulls live state from
 * [session.publicState] on every iteration so a pause that arrives mid-delay
 * is honoured immediately on the next tick.
 *
 * Behaviour:
 *  - **Stops** when the timer is cleared (`state.public.timer == null`) or a
 *    different timer id appears — the next round's ticker should start as a
 *    separate `LaunchedEffect`, not piggy-back on this one.
 *  - **Skips ticks** while either `state.public.paused` (session-wide) or
 *    `timer.paused` (per-timer) is true. The visible countdown freezes
 *    without restarting the coroutine.
 *  - **Submits `TimerExpired`** when `remainingSeconds <= 1` so the reducer
 *    clears the timer cleanly and the round can advance.
 *
 * The function is extracted from the `LaunchedEffect` in [RoundSegment] so
 * the loop is unit-testable under `runTest` with virtual time, without
 * Compose UI test infrastructure.
 *
 * Cancellation: the caller is expected to cancel this via the enclosing
 * coroutine scope (e.g. when the `LaunchedEffect` key changes or the
 * composition leaves). The loop respects cooperative cancellation through
 * [delay].
 */
suspend fun runDiscussionTickerLoop(
    session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    timerId: String,
    tickInterval: Duration = 1.seconds,
) {
    while (true) {
        delay(tickInterval)
        val state = session.publicState.value.state
        val timer = state.public.timer
        if (timer == null || timer.timerId != timerId) {
            // Timer was cleared (TimerExpired / AdvanceFromDiscussion) or a
            // new round started its own timer. Either way, this loop is done.
            return
        }
        if (state.public.paused || timer.paused) {
            // Frozen — re-loop without submitting anything.
            continue
        }
        if (timer.remainingSeconds <= 1) {
            session.submit(WhodunitAction.TimerExpired)
            return
        }
        session.submit(WhodunitAction.TimerTicked(timer.remainingSeconds - 1))
    }
}

package com.parlor.engine.reducer

import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState

/**
 * Pure state-machine reducer. Given a current state and an action, returns a
 * new state and any one-shot events.
 *
 * **Discipline:** no side effects. No I/O, no globals, no time-reading. Time,
 * randomness, and content lookup arrive via [ReducerContext].
 *
 * The reducer is verified pure by unit tests; violating this contract is a
 * correctness bug.
 */
interface GameReducer<S : GameState, A : GameAction, E : GameEvent> {
    fun reduce(state: S, action: A, ctx: ReducerContext): Reduction<S, E>
}

/**
 * The result of a reduction — the new state and any events to emit.
 *
 * When `newState === state`, callers can skip recomposition / persistence.
 */
data class Reduction<S, E>(
    val newState: S,
    val events: List<E> = emptyList(),
)

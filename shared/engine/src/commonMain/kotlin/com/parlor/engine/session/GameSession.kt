package com.parlor.engine.session

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.GameState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A running game instance. The session owns the canonical state on whichever
 * device hosts it (pass-and-play: the only device; multi-device: the host).
 *
 * Per-player private state is exposed via [privateStateFor]; the projection
 * policy enforces that callers only see what their viewer context allows.
 */
interface GameSession<S : GameState, A : GameAction, E : GameEvent> {
    val publicState: StateFlow<PublicProjection<S>>
    fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>>
    val hostState: StateFlow<HostProjection<S>>?  // only on the host device
    val events: SharedFlow<E>

    suspend fun submit(by: PlayerId?, action: A): Result<Unit, SubmitError>
    suspend fun snapshot(): GameSnapshot
    suspend fun restore(snapshot: GameSnapshot)
    suspend fun close()
}

/** Errors when a submitted action cannot be applied. */
sealed interface SubmitError {
    data object IllegalForPhase : SubmitError
    data object UnknownPlayer : SubmitError
    data class RejectedByReducer(val reason: String) : SubmitError
    /** Another host-authoritative mutation is awaiting an explicit outcome. */
    data object CommandPending : SubmitError
    /** The room is temporarily unable to accept gameplay mutations. */
    data object SessionSuspended : SubmitError
    data object SessionClosed : SubmitError
}

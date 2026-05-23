package com.parlor.engine.definition

import com.parlor.core.ids.GameId
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.engine.state.GameState

/**
 * A registered game in the Parlor catalog (e.g., Whodunit).
 *
 * Every module implements one `GameDefinition` and contributes it to the
 * `GameRegistry` via its Koin module. The shell uses the registry to render the
 * `All Games` grid and to route into the right module.
 *
 * Generic over the module's State/Action/Event triple so the engine is fully
 * decoupled from any single game's vocabulary.
 */
interface GameDefinition<S : GameState, A : GameAction, E : GameEvent> {
    val id: GameId
    val metadata: GameMetadata
    val supportedModes: List<GameMode>
    val supportedPlayerCounts: IntRange

    /** Build the initial state for a session given its config (players, mode, content). */
    fun createInitialState(config: SessionConfig): S

    /** The reducer that drives state transitions. Must be pure. */
    fun reducer(): GameReducer<S, A, E>

    /** Strips host-only and other-player private content per viewer. */
    fun projectionPolicy(): ProjectionPolicy<S>

    /** Serialization for snapshots; used by storage to persist and resume. */
    fun snapshotCodec(): SnapshotCodec<S>
}

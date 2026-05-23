package com.parlor.engine.state

import com.parlor.engine.phase.GamePhase

/**
 * Marker interface for game state. Each game module defines its own concrete
 * state, structured as a three-bucket container (`GameStateContainer`):
 * public, per-player private, and host-only.
 *
 * The engine itself does not know about killers, votes, or clues — those live
 * inside module-specific implementations of this interface.
 */
interface GameState {
    val phase: GamePhase
    val players: List<Player>
}

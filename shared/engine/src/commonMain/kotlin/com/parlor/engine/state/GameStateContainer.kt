package com.parlor.engine.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.phase.GamePhase

/**
 * Three-bucket state container used to enforce the engine privacy model.
 *
 * - `public` is visible to every viewer.
 * - `privatePerPlayer[playerId]` is visible only to the owning player.
 * - `hostOnly` is visible only to the host device.
 *
 * Game modules parameterize this with their own concrete bucket types. The
 * `ProjectionPolicy` must strip unwanted buckets per viewer at runtime. The
 * generic projection wrappers retain the full state type, so the type system
 * does not itself prevent host-only data from reaching a `PublicProjection`.
 */
data class GameStateContainer<P, Pr, H>(
    val public: P,
    val privatePerPlayer: Map<PlayerId, Pr>,
    val hostOnly: H,
    override val phase: GamePhase,
    override val players: List<Player>,
) : GameState

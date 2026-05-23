package com.parlor.engine.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.phase.GamePhase

/**
 * Three-bucket state container — the engine's enforcement of the privacy
 * model from ARCHITECTURE.md §7.
 *
 * - `public` is visible to every viewer.
 * - `privatePerPlayer[playerId]` is visible only to the owning player.
 * - `hostOnly` is visible only to the host device.
 *
 * Game modules parameterize this with their own concrete bucket types. The
 * `ProjectionPolicy` strips the unwanted buckets per viewer; the type system
 * prevents host-only data from ending up in a `PublicProjection`.
 */
data class GameStateContainer<P, Pr, H>(
    val public: P,
    val privatePerPlayer: Map<PlayerId, Pr>,
    val hostOnly: H,
    override val phase: GamePhase,
    override val players: List<Player>,
) : GameState

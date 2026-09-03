package com.parlor.engine.projection

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.GameState

/**
 * The viewer-filtered slice of game state. Three variants, one per viewer
 * context, carry the same full state type. They are not type-separated over
 * state buckets, so the compiler cannot establish redaction. Each game's
 * [ProjectionPolicy] enforces redaction at runtime; see its discipline note
 * below.
 */
sealed interface Projection<S : GameState> {
    val state: S
}

data class PublicProjection<S : GameState>(override val state: S) : Projection<S>

data class PrivateProjection<S : GameState>(
    override val state: S,
    val playerId: PlayerId,
) : Projection<S>

data class HostProjection<S : GameState>(override val state: S) : Projection<S>

/**
 * Strips host-only and other-player private content per viewer. Each
 * `GameDefinition` supplies its own policy.
 *
 * **Discipline:** `toPublic` must strip everything in host-only and per-player
 * private buckets. `toPlayer(id)` must strip everything in host-only and other
 * players' private buckets, keeping only `id`'s. `toHost` returns the full state.
 */
interface ProjectionPolicy<S : GameState> {
    fun toPublic(state: S): PublicProjection<S>
    fun toPlayer(state: S, playerId: PlayerId): PrivateProjection<S>
    fun toHost(state: S): HostProjection<S>
}

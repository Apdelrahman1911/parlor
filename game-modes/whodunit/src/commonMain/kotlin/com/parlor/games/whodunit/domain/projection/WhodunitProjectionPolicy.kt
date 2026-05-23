package com.parlor.games.whodunit.domain.projection

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState

/**
 * Strips state per viewer per ARCHITECTURE.md §7.
 *
 * - `toPublic`: clears `privatePerPlayer` AND `hostOnly`.
 * - `toPlayer(id)`: clears `hostOnly`; keeps only `privatePerPlayer[id]`.
 * - `toHost`: full state.
 */
object WhodunitProjectionPolicy : ProjectionPolicy<WhodunitState> {

    private val redactedHostOnly: WhodunitHostOnly = WhodunitHostOnly(
        killerId = PlayerId("redacted"),
        killerCharacterId = com.parlor.core.ids.CharacterId("redacted"),
        randomSeed = 0L,
        seatToCharacter = emptyMap(),
        redHerringTargets = emptyList(),
    )

    override fun toPublic(state: WhodunitState): PublicProjection<WhodunitState> =
        PublicProjection(
            state.copy(
                privatePerPlayer = emptyMap(),
                hostOnly = redactedHostOnly,
            ),
        )

    override fun toPlayer(state: WhodunitState, playerId: PlayerId): PrivateProjection<WhodunitState> {
        val ownEntry = state.privatePerPlayer[playerId]
        val filtered: Map<PlayerId, WhodunitPrivate> = if (ownEntry != null) {
            mapOf(playerId to ownEntry)
        } else emptyMap()
        return PrivateProjection(
            state.copy(
                privatePerPlayer = filtered,
                hostOnly = redactedHostOnly,
            ),
            playerId = playerId,
        )
    }

    override fun toHost(state: WhodunitState): HostProjection<WhodunitState> =
        HostProjection(state)
}

package com.parlor.games.mafia.domain.projection

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState

/**
 * Strips state per viewer per ARCHITECTURE.md §7.
 *
 *  - `toPublic`: clears `privatePerPlayer` AND `hostOnly`. Living-player
 *    roles in `public.roster` are already `null` by construction (the
 *    reducer never writes a non-null `revealedRole` while alive); we don't
 *    need to scrub them here.
 *
 *  - `toPlayer(id)`: clears `hostOnly`; keeps only `privatePerPlayer[id]`.
 *    Every other player's MafiaPrivate (including their role, knownTeammates,
 *    mafiaCoordination snapshot, pendingDetectiveResult, lastSuspicion,
 *    pendingNightChoice) is removed before transmission.
 *
 *  - `toHost`: full state.
 *
 * Privacy contract — load-bearing:
 *  - Mafia coordination only ever lives in living Mafia members' MafiaPrivate.
 *    The reducer's `updateMafiaCoordination` enforces this; the projection
 *    enforces it again at the transport boundary.
 *  - Detective inspection results only ever live in the Detective's MafiaPrivate.
 *  - Doctor/Detective/Civilian/Mafia night picks only live in the submitter's
 *    MafiaPrivate.
 *  - Full role map only lives in MafiaHostOnly.
 */
object MafiaProjectionPolicy : ProjectionPolicy<MafiaState> {

    private val redactedHostOnly: MafiaHostOnly = MafiaHostOnly(
        fullRoleMap = emptyMap(),
        randomSeed = 0L,
        nightLog = emptyList(),
        voteLog = emptyList(),
    )

    override fun toPublic(state: MafiaState): PublicProjection<MafiaState> =
        PublicProjection(
            state.copy(
                privatePerPlayer = emptyMap(),
                hostOnly = redactedHostOnly,
            ),
        )

    override fun toPlayer(state: MafiaState, playerId: PlayerId): PrivateProjection<MafiaState> {
        val ownEntry = state.privatePerPlayer[playerId]
        val filtered: Map<PlayerId, MafiaPrivate> = if (ownEntry != null) {
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

    override fun toHost(state: MafiaState): HostProjection<MafiaState> =
        HostProjection(state)
}

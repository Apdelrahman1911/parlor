package com.parlor.games.mafia.domain.projection

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState

/**
 * Strips canonical state into the exact public or per-player view permitted
 * at the multiplayer and UI boundaries.
 *
 *  - `toPublic`: clears `privatePerPlayer` AND `hostOnly`. Living-player
 *    roles in `public.roster` are already `null` by construction (the
 *    reducer never writes a non-null `revealedRole` while alive during play).
 *    In PostGame, every role is deliberately copied public before redaction.
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
 *  - PostGame is the sole exception to living-role secrecy: final roles are
 *    intentionally projected into the public roster for the shared reveal.
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
            withTerminalRoleReveal(state).copy(
                privatePerPlayer = emptyMap(),
                hostOnly = redactedHostOnly,
            ),
        )

    override fun toPlayer(state: MafiaState, playerId: PlayerId): PrivateProjection<MafiaState> {
        val ownEntry = state.privatePerPlayer[playerId]
        val filtered: Map<PlayerId, MafiaPrivate> = if (ownEntry != null) {
            mapOf(playerId to ownEntry)
        } else emptyMap()
        val terminalState = withTerminalRoleReveal(state)
        return PrivateProjection(
            terminalState.copy(
                privatePerPlayer = filtered,
                hostOnly = redactedHostOnly,
            ),
            playerId = playerId,
        )
    }

    override fun toHost(state: MafiaState): HostProjection<MafiaState> =
        HostProjection(state)

    /**
     * The complete role map is secret during play and public after the terminal
     * transition. Deriving this before host-only redaction guarantees that a
     * peer receives every final role even when reveal-on-death was disabled,
     * while no in-progress projection gains any extra information.
     */
    private fun withTerminalRoleReveal(state: MafiaState): MafiaState {
        if (state.phase != MafiaPhase.PostGame) return state
        return state.copy(
            public = state.public.copy(
                roster = state.public.roster.map { slot ->
                    slot.copy(
                        revealedRole = state.hostOnly.fullRoleMap[slot.playerId]
                            ?: slot.revealedRole,
                    )
                },
            ),
        )
    }
}

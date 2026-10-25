package com.parlor.games.ghamza.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection

/** Public and own-seat snapshots are detached. Host-only entropy/transcripts never enter a screen. */
object GhamzaProjection : ProjectionPolicy<GhamzaState> {
    override fun toPublic(state: GhamzaState) = PublicProjection(state.copy(
        public = detachedPublic(state), players = state.players.toList(),
        privatePerPlayer = emptyMap(), hostOnly = GhamzaHostOnly(),
    ))

    override fun toPlayer(state: GhamzaState, playerId: PlayerId): PrivateProjection<GhamzaState> {
        val own = state.privatePerPlayer[playerId]
        val private = when {
            state.players.none { it.id == playerId } || own == null -> emptyMap()
            state.phase == GhamzaPhase.Aborted -> mapOf(playerId to GhamzaPrivate())
            else -> mapOf(playerId to own.copy())
        }
        return PrivateProjection(toPublic(state).state.copy(privatePerPlayer = private), playerId)
    }

    override fun toHost(state: GhamzaState) = HostProjection(state)

    private fun detachedPublic(state: GhamzaState) = state.public.copy(
        ready = state.public.ready.toSet(), reports = state.public.reports.toMap(),
        recentReports = state.public.recentReports.toList(),
        disconnected = state.public.disconnected.toSet(),
    )
}

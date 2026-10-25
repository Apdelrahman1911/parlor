package com.parlor.games.dominoes.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection

/** Public and own-seat snapshots are detached. Host-only entropy/transcripts never enter a screen. */
object DominoProjection : ProjectionPolicy<DominoState> {
    override fun toPublic(state: DominoState) = PublicProjection(state.copy(
        public = detachedPublic(state), players = state.players.toList(),
        privatePerPlayer = emptyMap(), hostOnly = DominoHostOnly(),
    ))

    override fun toPlayer(state: DominoState, playerId: PlayerId): PrivateProjection<DominoState> {
        val own = state.privatePerPlayer[playerId]
        val private = when {
            state.players.none { it.id == playerId } || own == null -> emptyMap()
            state.phase == DominoPhase.Aborted -> mapOf(playerId to DominoPrivate())
            else -> mapOf(playerId to own.copy(hand = own.hand.toList()))
        }
        return PrivateProjection(toPublic(state).state.copy(privatePerPlayer = private), playerId)
    }

    override fun toHost(state: DominoState) = HostProjection(state)

    private fun detachedPublic(state: DominoState) = state.public.copy(
        chain = state.public.chain.toList(), handCounts = state.public.handCounts.toMap(),
        scores = state.public.scores.toMap(), disconnected = state.public.disconnected.toSet(),
        matchWinners = state.public.matchWinners.toSet(),
        result = state.public.result?.let { it.copy(remainingPips = it.remainingPips.toMap()) },
    )
}

package com.parlor.games.wordimpostor.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection

/** Public and own-seat snapshots are detached. Host-only entropy/transcripts never enter a screen. */
object WordImpostorProjection : ProjectionPolicy<WordImpostorState> {
    override fun toPublic(state: WordImpostorState) = PublicProjection(state.copy(
        public = detachedPublic(state), players = state.players.toList(),
        privatePerPlayer = emptyMap(), hostOnly = WordImpostorHostOnly(),
    ))

    override fun toPlayer(state: WordImpostorState, playerId: PlayerId): PrivateProjection<WordImpostorState> {
        val own = state.privatePerPlayer[playerId]
        val private = when {
            state.players.none { it.id == playerId } || own == null -> emptyMap()
            state.phase == WordImpostorPhase.Aborted -> mapOf(playerId to WordImpostorPrivate())
            else -> mapOf(playerId to own.copy(teammates = own.teammates.toSet(), choices = own.choices.toList()))
        }
        return PrivateProjection(toPublic(state).state.copy(privatePerPlayer = private), playerId)
    }

    override fun toHost(state: WordImpostorState) = HostProjection(state)

    private fun detachedPublic(state: WordImpostorState) = state.public.copy(
        ready = state.public.ready.toSet(), interactions = state.public.interactions.toList(),
        voted = state.public.voted.toSet(), voteCounts = state.public.voteCounts?.toMap(),
        scores = state.public.scores.toMap(), disconnected = state.public.disconnected.toSet(),
        result = state.public.result?.let {
            it.copy(impostors = it.impostors.toSet(), identified = it.identified.toSet(), correctVoters = it.correctVoters.toSet(),
                guesses = it.guesses.toMap(), awarded = it.awarded.toMap())
        },
    )
}

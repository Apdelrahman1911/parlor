package com.parlor.games.lastlight.domain.projection

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.immutableMapSnapshot
import com.parlor.games.lastlight.domain.model.immutableSnapshot
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.state.LastLightHostOnly
import com.parlor.games.lastlight.domain.state.LastLightPrivate
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.domain.state.detached

/** Secret hands, future fuse positions, undealt/discarded cards, and entropy never cross this boundary. */
object LastLightProjectionPolicy : ProjectionPolicy<LastLightState> {
    override fun toPublic(state: LastLightState): PublicProjection<LastLightState> =
        PublicProjection(redact(state, emptyMap()))

    override fun toPlayer(state: LastLightState, playerId: PlayerId): PrivateProjection<LastLightState> {
        val known = state.players.any { it.id == playerId }
        val eliminated = state.public.roster.firstOrNull { it.id == playerId.raw }?.eliminated != false
        val own = state.privatePerPlayer[playerId]?.let {
            if (eliminated) LastLightPrivate() else it.copy(hand = it.hand.immutableSnapshot())
        }
        val private = if (known && own != null) mapOf(playerId to own) else emptyMap()
        return PrivateProjection(redact(state, private), playerId)
    }

    override fun toHost(state: LastLightState): HostProjection<LastLightState> = HostProjection(state)

    /** Safe with canonical, public, or own-private projected input. Never reconstructs a missing hand. */
    fun viewFor(state: LastLightState, viewerId: PlayerId?): GameView {
        val view = state.public.detached()
        val viewer = view.roster.firstOrNull { it.id == viewerId?.raw }
        val hand = if (viewer != null && !viewer.eliminated) {
            state.privatePerPlayer[viewerId]?.hand.orEmpty().immutableSnapshot()
        } else emptyList()
        val actor = state.phase == GamePhase.PLAYING &&
            view.disconnectedPlayers.isEmpty() && !view.endedEarly &&
            viewer != null && !viewer.eliminated && viewer.id == view.turnPlayerId &&
            hand.size == viewer.handCount
        val canPlay = actor && !view.forcedChallenge && hand.isNotEmpty()
        val canChallenge = actor && view.latestClaim != null && view.latestClaim.playerId != viewer.id
        return GameView(
            viewerId = viewer?.id,
            phase = state.phase,
            roundNumber = view.roundNumber,
            tableRank = view.tableRank,
            players = view.roster,
            yourHand = hand,
            turnPlayerId = view.turnPlayerId,
            latestClaim = view.latestClaim,
            forcedChallenge = view.forcedChallenge,
            availableActions = AvailableActions(
                canPlay = canPlay,
                canChallenge = canChallenge,
                maxPlayableCards = if (canPlay) minOf(hand.size, LastLightRules.MAX_PLAY_CARDS) else 0,
            ),
            roundOutcome = view.roundOutcome,
            winnerId = view.winnerId,
            acceptedPlaySequence = view.acceptedPlaySequence,
            outcomeSequence = view.outcomeSequence,
        )
    }

    private fun redact(state: LastLightState, private: Map<PlayerId, LastLightPrivate>): LastLightState = state.copy(
        public = state.public.detached(),
        players = state.players.immutableSnapshot(),
        privatePerPlayer = private.immutableMapSnapshot(),
        hostOnly = LastLightHostOnly.Redacted,
    )
}

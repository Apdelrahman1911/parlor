package com.parlor.games.lastlight.domain.authority

import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.state.LastLightState

sealed interface LastLightAuthorityScope {
    data object HostOnly : LastLightAuthorityScope
    data class SelfActor(val actor: PlayerId) : LastLightAuthorityScope
}

object LastLightActionAuthority {
    fun classify(action: LastLightAction): LastLightAuthorityScope = when (action) {
        is LastLightAction.PlayCards -> LastLightAuthorityScope.SelfActor(action.by)
        is LastLightAction.Challenge -> LastLightAuthorityScope.SelfActor(action.by)
        LastLightAction.NextRound,
        LastLightAction.EndGame,
        is LastLightAction.MarkPlayerDisconnected,
        is LastLightAction.MarkPlayerReconnected,
        is LastLightAction.ContinueWithoutPlayer,
        -> LastLightAuthorityScope.HostOnly
    }

    fun isAllowed(
        action: LastLightAction,
        senderId: PlayerId,
        hostId: PlayerId,
        droppedPlayers: Set<PlayerId> = emptySet(),
    ): Boolean = when (val scope = classify(action)) {
        LastLightAuthorityScope.HostOnly -> senderId == hostId
        is LastLightAuthorityScope.SelfActor -> senderId == scope.actor && scope.actor !in droppedPlayers
    }

    /** State-aware use also refuses stale identities and eliminated player actions. */
    fun isAllowed(
        action: LastLightAction,
        senderId: PlayerId,
        hostId: PlayerId,
        state: LastLightState,
    ): Boolean {
        if (state.players.none { it.id == senderId }) return false
        if (!isAllowed(action, senderId, hostId, state.public.droppedPlayers)) return false
        return classify(action) == LastLightAuthorityScope.HostOnly ||
            state.public.roster.any { it.id == senderId.raw && !it.eliminated }
    }
}

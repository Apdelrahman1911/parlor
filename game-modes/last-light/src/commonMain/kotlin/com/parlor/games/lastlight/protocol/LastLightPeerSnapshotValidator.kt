package com.parlor.games.lastlight.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.rules.LastLightCardIds
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.state.LastLightHostOnly
import com.parlor.games.lastlight.domain.state.LastLightObservableStateValidator
import com.parlor.games.lastlight.domain.state.LastLightPrivate
import com.parlor.games.lastlight.domain.state.LastLightState

/** Receiver-side validation before installing a recipient-specific host snapshot. */
object LastLightPeerSnapshotValidator {
    fun isValidPublic(state: LastLightState): Boolean =
        state.privatePerPlayer.isEmpty() && state.hostOnly == LastLightHostOnly.Redacted &&
            LastLightObservableStateValidator.isValid(state)

    fun isValid(publicState: LastLightState, ownPrivate: LastLightPrivate?, selfPlayerId: PlayerId): Boolean {
        if (!isValidPublic(publicState)) return false
        val player = publicState.public.roster.firstOrNull { it.id == selfPlayerId.raw } ?: return false
        if (ownPrivate == null || ownPrivate.hand.size != player.handCount) return false
        if (ownPrivate.hand.size > LastLightRules.HAND_SIZE) return false
        if (!LastLightObservableStateValidator.areValidCards(ownPrivate.hand, publicState.public.roundNumber)) return false
        if (!hasValidDealtSlots(publicState, ownPrivate, selfPlayerId)) return false
        val outcome = publicState.public.roundOutcome
        if (outcome?.roundNumber == publicState.public.roundNumber) {
            if (!LastLightObservableStateValidator.areValidCards(
                    ownPrivate.hand + outcome.revealedCards,
                    publicState.public.roundNumber,
                )
            ) {
                return false
            }
        }
        return !player.eliminated || ownPrivate.hand.isEmpty()
    }

    private fun hasValidDealtSlots(state: LastLightState, own: LastLightPrivate, playerId: PlayerId): Boolean {
        if (own.hand.isEmpty()) return true
        val public = state.public
        val outcome = public.roundOutcome
        val eliminatedThisRound = outcome?.roundNumber == public.roundNumber && outcome.burnedOut
        val participants = public.roster.count { !it.eliminated } + if (eliminatedThisRound) 1 else 0
        val slots = own.hand.map { requireNotNull(LastLightCardIds.slot(it.id, public.roundNumber)) }
        if (slots.any { it >= participants * LastLightRules.HAND_SIZE } || slots != slots.sorted()) return false
        if (slots.map { it % participants }.distinct().size != 1) return false
        if (state.phase != GamePhase.PLAYING || public.latestClaim != null) return true
        val opener = public.roster.indexOfFirst { it.id == public.turnPlayerId }
        val dealingOrder = public.roster.indices.map { public.roster[(opener + it) % public.roster.size] }
            .filterNot { it.eliminated }
        val ownSlot = dealingOrder.indexOfFirst { it.id == playerId.raw }
        val expected = List(LastLightRules.HAND_SIZE) { ownSlot + it * participants }
        return slots == expected
    }
}

package com.parlor.games.lastlight.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.reducer.LastLightReducer
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.state.LastLightHistoryEntry
import com.parlor.games.lastlight.domain.state.LastLightObservableStateValidator
import com.parlor.games.lastlight.domain.state.LastLightState

/**
 * A full replay, bounded by the fixed rule set, proves that all retained
 * hands, fuse positions, results, turn order, and future entropy agree.
 * Valid connection markers may surround that game history without changing it.
 */
internal class LastLightCanonicalStateValidator(private val reducer: LastLightReducer) {
    fun requireValid(state: LastLightState) {
        LastLightObservableStateValidator.requireValid(state)
        require(hasValidAuthorityShape(state)) { "Invalid Last Light authority state" }
        var replay = reducer.createInitialState(SessionConfig(
            sessionId = SessionId("last-light-snapshot-validation"),
            caseId = LastLightIds.CaseId,
            modeId = LastLightIds.StandardModeId,
            players = state.players,
            randomSeed = state.hostOnly.randomSeed,
        ))
        for (entry in state.hostOnly.history) {
            require(isValidEntry(entry, state.players.size)) { "Invalid Last Light history entry" }
            val action = actionFor(replay, entry)
            val next = reducer.apply(replay, action).newState
            require(next !== replay) { "Last Light history contains an illegal transition" }
            replay = next
        }
        val expected = replay.copy(public = replay.public.copy(
            disconnectedPlayers = state.public.disconnectedPlayers,
            droppedPlayers = state.public.droppedPlayers,
        ))
        require(expected == state) { "Last Light snapshot is not reducer-reachable" }
    }

    private fun hasValidAuthorityShape(state: LastLightState): Boolean {
        val ids = state.players.map { it.id }.toSet()
        val host = state.hostOnly
        if (state.privatePerPlayer.keys != ids || host.burnoutSteps.keys != ids) return false
        if (host.burnoutSteps.values.any { it !in 1..LastLightRules.FUSE_LIGHTS }) return false
        if (host.openerPlayerId !in ids.map { it.raw }) return false
        if (host.history.size > LastLightRules.MAX_HISTORY_ENTRIES) return false
        if (state.privatePerPlayer.any { (id, private) ->
                private.hand.size != state.public.roster.single { it.id == id.raw }.handCount
            }
        ) {
            return false
        }
        val physicalCards = state.privatePerPlayer.values.flatMap { it.hand } + host.discardedCards + host.undealtCards
        if (physicalCards.size != LastLightRules.DECK_SIZE) return false
        if (!LastLightObservableStateValidator.areValidCards(physicalCards, state.public.roundNumber)) return false
        if (physicalCards.count { it.rank == CardRank.WILD } != LastLightRules.WILD_CARDS) return false
        if (host.pendingCards.size != (state.public.latestClaim?.cardCount ?: 0)) return false
        if (!LastLightObservableStateValidator.areValidCards(host.pendingCards, state.public.roundNumber)) return false
        return host.discardedCards.containsAll(host.pendingCards)
    }

    private fun isValidEntry(entry: LastLightHistoryEntry, players: Int): Boolean {
        if (entry.seat !in LastLightHistoryEntry.END_GAME until players) return false
        if (entry.seat < 0) return entry.cardSlots.isEmpty()
        return entry.cardSlots.size <= LastLightRules.MAX_PLAY_CARDS &&
            entry.cardSlots.distinct().size == entry.cardSlots.size &&
            entry.cardSlots.all { it in 0 until LastLightRules.DECK_SIZE }
    }

    private fun actionFor(state: LastLightState, entry: LastLightHistoryEntry): LastLightAction = when (entry.seat) {
        LastLightHistoryEntry.NEXT_ROUND -> LastLightAction.NextRound
        LastLightHistoryEntry.END_GAME -> LastLightAction.EndGame
        else -> {
            val actor = state.players[entry.seat].id
            if (entry.cardSlots.isEmpty()) {
                LastLightAction.Challenge(actor)
            } else {
                LastLightAction.PlayCards(actor, entry.cardSlots.map { "r${state.public.roundNumber}-c$it" })
            }
        }
    }
}

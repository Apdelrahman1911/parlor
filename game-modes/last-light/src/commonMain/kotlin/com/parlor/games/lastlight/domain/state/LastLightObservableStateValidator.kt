package com.parlor.games.lastlight.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.RoundOutcome
import com.parlor.games.lastlight.domain.rules.LastLightCardIds
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.rules.LastLightSessionRules

/** Pure validation of facts visible to every player; it never needs secret cards or fuse positions. */
object LastLightObservableStateValidator {
    fun isValid(state: LastLightState): Boolean =
        hasValidRoster(state) && hasValidProgress(state) && hasValidPhase(state) &&
            hasValidConnections(state) && hasValidOutcome(state)

    fun requireValid(state: LastLightState) {
        require(isValid(state)) { "Invalid Last Light public state" }
    }

    fun areValidCards(cards: List<Card>, round: Int): Boolean =
        cards.size <= LastLightRules.DECK_SIZE && cards.map { it.id }.distinct().size == cards.size &&
            cards.all { LastLightCardIds.slot(it.id, round) != null } &&
            CardRank.entries.all { rank ->
                val maximum = if (rank == CardRank.WILD) LastLightRules.WILD_CARDS else LastLightRules.COPIES_PER_RANK
                cards.count { it.rank == rank } <= maximum
            }

    private fun hasValidRoster(state: LastLightState): Boolean {
        if (!LastLightSessionRules.isValidRoster(state.players)) return false
        val roster = state.public.roster
        if (roster.size != state.players.size) return false
        return roster.withIndex().all { (seat, player) ->
            val identity = state.players[seat]
            player.id == identity.id.raw && player.displayName == identity.displayName &&
                player.handCount in 0..LastLightRules.HAND_SIZE &&
                if (player.eliminated) {
                    player.handCount == 0 && player.penaltyAttempts in 1..LastLightRules.FUSE_LIGHTS
                } else {
                    player.penaltyAttempts in 0 until LastLightRules.FUSE_LIGHTS
                }
        }
    }

    private fun hasValidProgress(state: LastLightState): Boolean {
        val public = state.public
        val maximumRounds = LastLightRules.FUSE_LIGHTS * state.players.size - 1
        if (public.roundNumber !in 1..maximumRounds || public.tableRank == CardRank.WILD) return false
        if (public.outcomeSequence !in 0L..maximumRounds.toLong()) return false
        if (public.acceptedPlaySequence !in public.outcomeSequence..LastLightRules.MAX_PLAYS.toLong()) return false
        if (public.roster.sumOf { it.penaltyAttempts.toLong() } != public.outcomeSequence) return false
        val maximumPlaysPerRound = LastLightRules.HAND_SIZE * state.players.size - 1
        if (public.acceptedPlaySequence > public.roundNumber.toLong() * maximumPlaysPerRound) return false
        val completedRound = public.outcomeSequence.toInt()
        return when {
            public.endedEarly -> completedRound in (public.roundNumber - 1)..public.roundNumber
            state.phase == GamePhase.PLAYING -> completedRound == public.roundNumber - 1
            else -> completedRound == public.roundNumber
        }
    }

    private fun hasValidPhase(state: LastLightState): Boolean {
        val public = state.public
        val survivors = public.roster.filterNot { it.eliminated }
        if (survivors.isEmpty()) return false
        if (public.endedEarly && state.phase != GamePhase.FINISHED) return false
        if (state.phase == GamePhase.FINISHED && !public.endedEarly) {
            if (survivors.size != 1 || public.winnerId != survivors.single().id) return false
        } else if (survivors.size < LastLightRules.MIN_PLAYERS || public.winnerId != null) {
            return false
        }
        val holders = survivors.filter { it.handCount > 0 }
        val playing = state.phase == GamePhase.PLAYING
        if (public.forcedChallenge != (playing && holders.size == 1)) return false
        if (!playing) return public.turnPlayerId == null && public.latestClaim == null
        if (holders.none { it.id == public.turnPlayerId }) return false
        if (!hasValidPlayCount(state)) return false
        val claim = public.latestClaim
        if (claim == null) {
            return survivors.all { it.handCount == LastLightRules.HAND_SIZE } && !public.forcedChallenge
        }
        val claimant = survivors.firstOrNull { it.id == claim.playerId } ?: return false
        return public.turnPlayerId == nextHolderAfter(state, claim.playerId) &&
            claim.cardCount in 1..LastLightRules.MAX_PLAY_CARDS &&
            claimant.handCount + claim.cardCount <= LastLightRules.HAND_SIZE && public.acceptedPlaySequence > 0
    }

    private fun hasValidPlayCount(state: LastLightState): Boolean {
        val public = state.public
        val playedCards = public.roster.filterNot { it.eliminated }.sumOf { LastLightRules.HAND_SIZE - it.handCount }
        val minimumCurrentPlays = (playedCards + LastLightRules.MAX_PLAY_CARDS - 1) / LastLightRules.MAX_PLAY_CARDS
        val maximumPastPlays = public.outcomeSequence * (LastLightRules.HAND_SIZE * state.players.size - 1)
        return public.acceptedPlaySequence >= public.outcomeSequence + minimumCurrentPlays &&
            public.acceptedPlaySequence <= maximumPastPlays + playedCards
    }

    private fun nextHolderAfter(state: LastLightState, claimantId: String): String? {
        val roster = state.public.roster
        val claimantSeat = roster.indexOfFirst { it.id == claimantId }
        return (1 until roster.size).asSequence()
            .map { roster[(claimantSeat + it) % roster.size] }
            .firstOrNull { !it.eliminated && it.handCount > 0 }?.id
    }

    private fun hasValidConnections(state: LastLightState): Boolean {
        val public = state.public
        val survivors = public.roster.filterNot { it.eliminated }.map { PlayerId(it.id) }.toSet()
        if (!survivors.containsAll(public.disconnectedPlayers) || !survivors.containsAll(public.droppedPlayers)) return false
        if (public.droppedPlayers.size > 1 || public.disconnectedPlayers.any { it in public.droppedPlayers }) return false
        if (public.droppedPlayers.isNotEmpty() && !public.endedEarly) return false
        return state.phase != GamePhase.FINISHED || public.disconnectedPlayers.isEmpty()
    }

    private fun hasValidOutcome(state: LastLightState): Boolean {
        val public = state.public
        val outcome = public.roundOutcome
        if (public.outcomeSequence == 0L) return outcome == null
        if (outcome == null || outcome.roundNumber.toLong() != public.outcomeSequence) return false
        if (!isValidProof(outcome)) return false
        if (outcome.roundNumber == public.roundNumber &&
            (outcome.tableRank != public.tableRank || !outcomeFollowsClaim(state, outcome))
        ) {
            return false
        }
        val participants = public.roster.count { !it.eliminated } + if (outcome.burnedOut) 1 else 0
        val slots = outcome.revealedCards.map { requireNotNull(LastLightCardIds.slot(it.id, outcome.roundNumber)) }
        if (slots.any { it >= participants * LastLightRules.HAND_SIZE }) return false
        if (slots.map { it % participants }.distinct().size != 1) return false
        val claimant = public.roster.firstOrNull { it.id == outcome.claimantId } ?: return false
        val challenger = public.roster.firstOrNull { it.id == outcome.challengerId } ?: return false
        val penalized = if (outcome.truthful) challenger else claimant
        val spared = if (outcome.truthful) claimant else challenger
        return !spared.eliminated && penalized.id == outcome.penalizedPlayerId &&
            penalized.penaltyAttempts == outcome.penaltyAttempt && penalized.eliminated == outcome.burnedOut
    }

    private fun outcomeFollowsClaim(state: LastLightState, outcome: RoundOutcome): Boolean {
        val roster = state.public.roster
        val claimantSeat = roster.indexOfFirst { it.id == outcome.claimantId }
        if (claimantSeat < 0) return false
        if (roster[claimantSeat].handCount + outcome.revealedCards.size > LastLightRules.HAND_SIZE) return false
        val challenger = (1 until roster.size).asSequence().map { roster[(claimantSeat + it) % roster.size] }
            .firstOrNull { player ->
                val justEliminated = outcome.burnedOut && player.id == outcome.penalizedPlayerId
                val challengerBurnedOut = justEliminated && player.id == outcome.challengerId
                (!player.eliminated || justEliminated) && (player.handCount > 0 || challengerBurnedOut)
            }
        return challenger?.id == outcome.challengerId
    }

    private fun isValidProof(outcome: RoundOutcome): Boolean {
        if (outcome.roundNumber !in 1..LastLightRules.MAX_ROUNDS || outcome.tableRank == CardRank.WILD) return false
        if (outcome.claimantId == outcome.challengerId || outcome.penaltyAttempt !in 1..LastLightRules.FUSE_LIGHTS) return false
        if (outcome.revealedCards.size !in 1..LastLightRules.MAX_PLAY_CARDS) return false
        if (!areValidCards(outcome.revealedCards, outcome.roundNumber)) return false
        return outcome.truthful == outcome.revealedCards.all { it.rank == outcome.tableRank || it.rank == CardRank.WILD }
    }
}

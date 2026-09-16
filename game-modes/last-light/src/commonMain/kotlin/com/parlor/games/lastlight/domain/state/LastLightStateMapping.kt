package com.parlor.games.lastlight.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameState
import com.parlor.games.lastlight.domain.model.PendingPlay
import com.parlor.games.lastlight.domain.model.PlayerIdentity
import com.parlor.games.lastlight.domain.model.PlayerState
import com.parlor.games.lastlight.domain.model.PlayerView
import com.parlor.games.lastlight.domain.model.PublicClaim
import com.parlor.games.lastlight.domain.model.immutableMapSnapshot
import com.parlor.games.lastlight.domain.model.immutableSetSnapshot
import com.parlor.games.lastlight.domain.model.immutableSnapshot

/** Converts only trusted authority buckets into the original pure PartyDeck rules model. */
internal fun LastLightState.toRulesState(): GameState = GameState(
    players = players.map { player ->
        val publicPlayer = public.roster.single { it.id == player.id.raw }
        PlayerState(
            identity = PlayerIdentity(player.id.raw, player.displayName),
            hand = privatePerPlayer.getValue(player.id).hand,
            penaltyAttempts = publicPlayer.penaltyAttempts,
            burnoutStep = hostOnly.burnoutSteps.getValue(player.id),
            eliminated = publicPlayer.eliminated,
        )
    }.immutableSnapshot(),
    phase = phase,
    roundNumber = public.roundNumber,
    tableRank = public.tableRank,
    openerPlayerId = requireNotNull(hostOnly.openerPlayerId),
    turnPlayerId = public.turnPlayerId,
    pendingPlay = public.latestClaim?.let { PendingPlay(it.playerId, hostOnly.pendingCards) },
    discardedCards = hostOnly.discardedCards,
    undealtCards = hostOnly.undealtCards,
    roundOutcome = public.roundOutcome,
    winnerId = public.winnerId,
)

internal fun GameState.toLastLightState(
    roster: List<Player>,
    seed: Long,
    history: List<LastLightHistoryEntry> = emptyList(),
    disconnected: Set<PlayerId> = emptySet(),
    dropped: Set<PlayerId> = emptySet(),
    endedEarly: Boolean = false,
): LastLightState = LastLightState(
    public = LastLightPublic(
        roundNumber = roundNumber,
        tableRank = tableRank,
        roster = players.map { player ->
            PlayerView(
                id = player.identity.id,
                displayName = player.identity.displayName,
                handCount = player.hand.size,
                penaltyAttempts = player.penaltyAttempts,
                eliminated = player.eliminated,
            )
        }.immutableSnapshot(),
        turnPlayerId = turnPlayerId,
        latestClaim = pendingPlay?.let { PublicClaim(it.playerId, it.cards.size) },
        forcedChallenge = phase == GamePhase.PLAYING && players.count { !it.eliminated && it.hand.isNotEmpty() } == 1,
        roundOutcome = roundOutcome?.let { it.copy(revealedCards = it.revealedCards.immutableSnapshot()) },
        winnerId = winnerId,
        acceptedPlaySequence = history.count { it.seat >= 0 && it.cardSlots.isNotEmpty() }.toLong(),
        outcomeSequence = history.count { it.seat >= 0 && it.cardSlots.isEmpty() }.toLong(),
        disconnectedPlayers = disconnected.immutableSetSnapshot(),
        droppedPlayers = dropped.immutableSetSnapshot(),
        endedEarly = endedEarly,
    ),
    privatePerPlayer = players.associate { player ->
        PlayerId(player.identity.id) to LastLightPrivate(player.hand.immutableSnapshot())
    }.immutableMapSnapshot(),
    hostOnly = LastLightHostOnly(
        randomSeed = seed,
        burnoutSteps = players.associate { PlayerId(it.identity.id) to it.burnoutStep }.immutableMapSnapshot(),
        openerPlayerId = openerPlayerId,
        pendingCards = pendingPlay?.cards.orEmpty().immutableSnapshot(),
        discardedCards = discardedCards.immutableSnapshot(),
        undealtCards = undealtCards.immutableSnapshot(),
        history = history.map { it.copy(cardSlots = it.cardSlots.immutableSnapshot()) }.immutableSnapshot(),
    ),
    phase = phase,
    players = roster.immutableSnapshot(),
)

internal fun LastLightPublic.detached(): LastLightPublic = copy(
    roster = roster.immutableSnapshot(),
    roundOutcome = roundOutcome?.let { it.copy(revealedCards = it.revealedCards.immutableSnapshot()) },
    disconnectedPlayers = disconnectedPlayers.immutableSetSnapshot(),
    droppedPlayers = droppedPlayers.immutableSetSnapshot(),
)

internal fun LastLightState.detached(): LastLightState = copy(
    public = public.detached(),
    players = players.immutableSnapshot(),
    privatePerPlayer = privatePerPlayer.mapValues { (_, value) ->
        value.copy(hand = value.hand.immutableSnapshot())
    }.immutableMapSnapshot(),
    hostOnly = hostOnly.copy(
        burnoutSteps = hostOnly.burnoutSteps.immutableMapSnapshot(),
        pendingCards = hostOnly.pendingCards.immutableSnapshot(),
        discardedCards = hostOnly.discardedCards.immutableSnapshot(),
        undealtCards = hostOnly.undealtCards.immutableSnapshot(),
        history = hostOnly.history.map { it.copy(cardSlots = it.cardSlots.immutableSnapshot()) }.immutableSnapshot(),
    ),
)

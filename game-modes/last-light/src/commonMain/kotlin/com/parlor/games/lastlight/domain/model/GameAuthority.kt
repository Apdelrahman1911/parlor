// Adapted from PartyDeck df649e6c896203bdf93130f6497e229757d5da30, core/src/commonMain/kotlin/dev/partydeck/core/.
package com.parlor.games.lastlight.domain.model

import kotlinx.serialization.Serializable

/** Host-only player state. Never serialize this type or pass it to a client or bot. */
@ConsistentCopyVisibility
internal data class PlayerState internal constructor(
    val identity: PlayerIdentity,
    val hand: List<Card>,
    val penaltyAttempts: Int,
    val burnoutStep: Int,
    val eliminated: Boolean,
)

/** Host-only evidence. These cards are already part of [GameState.discardedCards]. */
@ConsistentCopyVisibility
internal data class PendingPlay internal constructor(
    val playerId: PlayerId,
    val cards: List<Card>,
)

/**
 * Full host authority state. Intentionally has no serializer.
 * Card zones are all player hands, [discardedCards], and [undealtCards].
 * [pendingPlay] references cards in the discard zone, so it is not a fourth zone.
 */
@ConsistentCopyVisibility
internal data class GameState internal constructor(
    val players: List<PlayerState>,
    val phase: GamePhase,
    val roundNumber: Int,
    val tableRank: CardRank,
    val openerPlayerId: PlayerId,
    val turnPlayerId: PlayerId?,
    val pendingPlay: PendingPlay?,
    val discardedCards: List<Card>,
    val undealtCards: List<Card>,
    val roundOutcome: RoundOutcome?,
    val winnerId: PlayerId?,
)

/** The session binds [playerId] to an authenticated peer; it is not trusted client input. */
internal sealed interface GameAction {
    val playerId: PlayerId

    data class Play(
        override val playerId: PlayerId,
        val cardIds: List<CardId>,
    ) : GameAction

    data class Challenge(
        override val playerId: PlayerId,
    ) : GameAction
}

@Serializable
internal enum class GameRejection {
    GAME_FINISHED,
    ROUND_NOT_PLAYING,
    ROUND_NOT_ENDED,
    UNKNOWN_PLAYER,
    PLAYER_ELIMINATED,
    NOT_YOUR_TURN,
    MUST_CHALLENGE,
    INVALID_CARD_COUNT,
    DUPLICATE_CARD,
    CARD_NOT_IN_HAND,
    NO_CLAIM,
    CANNOT_CHALLENGE_SELF,
}

internal sealed interface GameDecision {
    data class Applied(val state: GameState) : GameDecision
    data class Rejected(val reason: GameRejection) : GameDecision
}

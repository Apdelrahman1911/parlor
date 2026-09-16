package com.parlor.games.lastlight.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.PlayerView
import com.parlor.games.lastlight.domain.model.PublicClaim
import com.parlor.games.lastlight.domain.model.RoundOutcome
import kotlinx.serialization.Serializable

/**
 * Canonical state has three privacy buckets. The snapshot codec is for local
 * authority recovery only; the wire codec has separate public/private DTOs.
 */
@Serializable
data class LastLightState(
    val public: LastLightPublic,
    val privatePerPlayer: Map<PlayerId, LastLightPrivate>,
    val hostOnly: LastLightHostOnly,
    override val phase: GamePhase,
    override val players: List<Player>,
) : GameState

@Serializable
data class LastLightPublic(
    val roundNumber: Int,
    val tableRank: CardRank,
    val roster: List<PlayerView>,
    val turnPlayerId: String?,
    val latestClaim: PublicClaim?,
    val forcedChallenge: Boolean,
    val roundOutcome: RoundOutcome?,
    val winnerId: String?,
    val acceptedPlaySequence: Long = 0L,
    val outcomeSequence: Long = 0L,
    /** Every disconnected survivor pauses the match, including an empty-hand claimant. */
    val disconnectedPlayers: Set<PlayerId> = emptySet(),
    /** A departed survivor terminates the match; their hidden cards are never redistributed. */
    val droppedPlayers: Set<PlayerId> = emptySet(),
    /** Explicit interruption is distinct from a rules-level last-survivor win. */
    val endedEarly: Boolean = false,
)

@Serializable
data class LastLightPrivate(val hand: List<Card> = emptyList())

/** None of these fields are serializable through the peer projection codec. */
@Serializable
data class LastLightHostOnly(
    val randomSeed: Long = 0L,
    val burnoutSteps: Map<PlayerId, Int> = emptyMap(),
    val openerPlayerId: String? = null,
    val pendingCards: List<Card> = emptyList(),
    val discardedCards: List<Card> = emptyList(),
    val undealtCards: List<Card> = emptyList(),
    /** Bounded replay proof, used to reject impossible or altered persisted authority state. */
    val history: List<LastLightHistoryEntry> = emptyList(),
) {
    companion object {
        val Redacted = LastLightHostOnly()
    }
}

/**
 * Compact authority-only transcript. Nonnegative [seat] plus card slots is a
 * play; a nonnegative seat with no slots is a challenge. The two negative
 * markers are host transitions, never player seats. Slots refer to the
 * freshly shuffled deck of the current round and cannot encode a rank.
 */
@Serializable
data class LastLightHistoryEntry(val seat: Int, val cardSlots: List<Int> = emptyList()) {
    companion object {
        const val NEXT_ROUND = -1
        const val END_GAME = -2
    }
}

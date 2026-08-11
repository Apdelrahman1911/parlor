package com.parlor.games.whodunit.domain.rules

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.modes.ClassicVoteMode
import com.parlor.games.whodunit.domain.modes.EliminationMode
import com.parlor.networking.room.RoomInputPolicy

/**
 * Authoritative, UI-independent Whodunit session rules.
 *
 * Setup screens may use these ranges to guide the player, but session and
 * snapshot boundaries must enforce them again. Keeping round limits here also
 * prevents the reducer, content validator, and persistence validator from
 * silently drifting apart.
 */
object WhodunitRules {
    const val MIN_DISCUSSION_SECONDS: Int = 1
    const val MAX_DISCUSSION_SECONDS: Int = 10 * 60

    fun supportedPlayerCounts(modeId: ModeId): IntRange? = when (modeId) {
        WhodunitIds.ClassicVoteModeId -> ClassicVoteMode.supportedPlayerCounts
        WhodunitIds.EliminationModeId -> EliminationMode.supportedPlayerCounts
        else -> null
    }

    fun isValidRoster(modeId: ModeId, players: List<Player>): Boolean {
        val supportedCounts = supportedPlayerCounts(modeId) ?: return false
        if (players.size !in supportedCounts) return false
        if (players.map { it.id }.toSet().size != players.size) return false
        if (players.any { it.seat < 0 }) return false
        if (!RoomInputPolicy.areValidDistinctDisplayNames(players.map(Player::displayName))) {
            return false
        }
        if (players.withIndex().any { (seat, player) -> player.seat != seat }) return false
        return true
    }

    /** Intersection of engine mode support and one validated case's authored capacity. */
    fun supportedPlayerCountsForCase(
        modeId: ModeId,
        casePlayerCounts: IntRange,
        availableCharacters: Int,
    ): IntRange? {
        val modeCounts = supportedPlayerCounts(modeId) ?: return null
        val first = maxOf(modeCounts.first, casePlayerCounts.first)
        val last = minOf(modeCounts.last, casePlayerCounts.last, availableCharacters)
        return if (first <= last) first..last else null
    }

    /** Complete envelope + payload admission rule for one authoritative session. */
    fun isSupportedByCase(
        case: ValidatedCase<WhodunitCase>,
        caseId: CaseId,
        modeId: ModeId,
        playerCount: Int,
    ): Boolean {
        val envelope = case.envelope
        if (envelope.gameId != WhodunitIds.GameId.raw) return false
        if (caseId.raw != envelope.caseId) return false
        if (modeId.raw !in envelope.supportedModes) return false
        val effectiveCounts = supportedPlayerCountsForCase(
            modeId = modeId,
            casePlayerCounts = envelope.supportedPlayerCounts.toIntRange(),
            availableCharacters = case.payload.characters.size,
        ) ?: return false
        return playerCount in effectiveCounts
    }

    /**
     * Maximum number of evidence/vote rounds before the investigation must
     * resolve. Classic uses the authored three/four-round story shape.
     * Elimination has at most one round per possible innocent elimination,
     * ending when the killer reaches the final two.
     */
    fun maximumRoundCount(modeId: ModeId, initialPlayerCount: Int): Int? = when (modeId) {
        WhodunitIds.ClassicVoteModeId ->
            if (initialPlayerCount <= SHORT_CLASSIC_MAX_PLAYERS) SHORT_CLASSIC_ROUNDS else LONG_CLASSIC_ROUNDS
        WhodunitIds.EliminationModeId ->
            (initialPlayerCount - FINAL_TWO_PLAYERS).takeIf { it > 0 }
        else -> null
    }

    private const val SHORT_CLASSIC_MAX_PLAYERS = 4
    private const val SHORT_CLASSIC_ROUNDS = 3
    private const val LONG_CLASSIC_ROUNDS = 4
    private const val FINAL_TWO_PLAYERS = 2
}

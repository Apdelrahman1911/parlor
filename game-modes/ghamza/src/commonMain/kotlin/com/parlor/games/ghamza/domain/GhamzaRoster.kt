package com.parlor.games.ghamza.domain

import com.parlor.core.text.DisplayNamePolicy
import com.parlor.core.text.DisplayTextScalars
import com.parlor.engine.state.Player

/** Matches the shared admission contract without importing transport into the rules. */
object GhamzaRoster {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 12
    private const val MAX_PLAYER_ID_LENGTH = 64

    fun isValidRoster(players: List<Player>): Boolean {
        if (players.size !in MIN_PLAYERS..MAX_PLAYERS) return false
        if (players.map { it.id }.distinct().size != players.size) return false
        if (players.map { it.displayName }.distinct().size != players.size) return false
        return players.withIndex().all { (seat, player) ->
            player.seat == seat && isValidPlayerId(player.id.raw) && isValidDisplayName(player.displayName)
        }
    }

    fun isValidPlayerId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_PLAYER_ID_LENGTH && DisplayTextScalars.containsOnlySafeScalars(value)

    fun isValidDisplayName(value: String): Boolean =
        value == DisplayNamePolicy.normalizeDisplayName(value) && DisplayNamePolicy.isValidDisplayName(value)
}

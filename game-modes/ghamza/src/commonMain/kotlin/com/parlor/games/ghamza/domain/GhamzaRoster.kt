package com.parlor.games.ghamza.domain

import com.parlor.engine.state.Player

/** Matches the shared admission contract without importing transport into the rules. */
object GhamzaRoster {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 12
    private const val MAX_PLAYER_ID_LENGTH = 64
    private const val MAX_DISPLAY_NAME_LENGTH = 32

    fun isValidRoster(players: List<Player>): Boolean {
        if (players.size !in MIN_PLAYERS..MAX_PLAYERS) return false
        if (players.map { it.id }.distinct().size != players.size) return false
        if (players.map { it.displayName }.distinct().size != players.size) return false
        return players.withIndex().all { (seat, player) ->
            player.seat == seat && isValidPlayerId(player.id.raw) && isValidDisplayName(player.displayName)
        }
    }

    fun isValidPlayerId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_PLAYER_ID_LENGTH && value.hasSafeCharacters()

    fun isValidDisplayName(value: String): Boolean =
        value == value.trim() && value.isNotEmpty() &&
            value.length <= MAX_DISPLAY_NAME_LENGTH && value.hasSafeCharacters()

    private fun String.hasSafeCharacters(): Boolean {
        var index = 0
        while (index < length) {
            val character = this[index]
            when {
                character.isISOControl() || character.category == CharCategory.FORMAT -> return false
                character in '\uD800'..'\uDBFF' -> {
                    if (getOrNull(index + 1)?.let { it in '\uDC00'..'\uDFFF' } != true) return false
                    index += SURROGATE_PAIR_LENGTH
                }
                character in '\uDC00'..'\uDFFF' -> return false
                else -> index++
            }
        }
        return true
    }

    private const val SURROGATE_PAIR_LENGTH = 2
}

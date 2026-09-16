package com.parlor.games.lastlight.domain.rules

import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightIds

/**
 * Pure configuration boundary. Name rules match Parlor admission without
 * importing the networking module into the game domain.
 */
object LastLightSessionRules {
    fun isValidConfig(config: SessionConfig): Boolean =
        config.modeId == LastLightIds.StandardModeId &&
            config.caseId == LastLightIds.CaseId &&
            isValidPlayerId(config.sessionId.raw) &&
            isValidRoster(config.players)

    fun isValidRoster(players: List<Player>): Boolean {
        if (players.size !in LastLightRules.MIN_PLAYERS..LastLightRules.MAX_PLAYERS) return false
        if (players.map { it.id }.distinct().size != players.size) return false
        if (players.map { it.displayName }.distinct().size != players.size) return false
        return players.withIndex().all { (seat, player) ->
            player.seat == seat && isValidPlayerId(player.id.raw) && isValidDisplayName(player.displayName)
        }
    }

    fun requireValidConfig(config: SessionConfig) {
        require(isValidConfig(config)) { "Invalid Last Light Standard session configuration" }
    }

    fun isValidPlayerId(value: String): Boolean =
        value.isNotBlank() && value.length <= LastLightRules.MAX_PLAYER_ID_LENGTH && value.hasSafeCharacters()

    fun isValidDisplayName(value: String): Boolean =
        value == value.trim() && value.isNotEmpty() &&
            value.length <= LastLightRules.MAX_DISPLAY_NAME_LENGTH && value.hasSafeCharacters()

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

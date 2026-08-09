package com.parlor.games.mafia.domain.rules

import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.networking.room.RoomInputPolicy

/**
 * Authoritative, UI-independent boundary for starting or restoring a Mafia session.
 *
 * The setup UI and the room protocol may reject invalid input earlier, but neither is
 * a trust boundary for the game engine. Every path that constructs authoritative Mafia
 * state must enforce these rules again.
 */
object MafiaSessionRules {

    fun isValidConfig(config: SessionConfig): Boolean =
        config.modeId == MafiaIds.ClassicModeId && isValidRoster(config.players)

    fun isValidRoster(players: List<Player>): Boolean {
        if (players.size !in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS) return false
        if (players.map(Player::id).distinct().size != players.size) return false
        if (players.map(Player::seat).distinct().size != players.size) return false
        if (players.withIndex().any { (expectedSeat, player) -> player.seat != expectedSeat }) {
            return false
        }
        if (players.any { player -> !isValidDisplayName(player.displayName) }) return false
        return true
    }

    fun requireValidConfig(config: SessionConfig) {
        require(isValidConfig(config)) {
            "Invalid Mafia Classic session configuration"
        }
    }

    private fun isValidDisplayName(displayName: String): Boolean =
        displayName == RoomInputPolicy.normalizeDisplayName(displayName) &&
            RoomInputPolicy.isValidDisplayName(displayName)
}

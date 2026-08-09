package com.parlor.games.mafia.domain.settings

/**
 * Default presets by player count. Hosts can override every field — these are
 * starting points tuned for balance: roughly 1 Mafia per 3–4 town members,
 * Detective from 6+, Doctor from 7+. Civilians fill the remainder.
 */
object MafiaSettingsPresets {

    fun forPlayerCount(playerCount: Int): MafiaSettings {
        val counts = when {
            playerCount <= SMALL_GAME_MAX_PLAYERS -> MafiaRoleCounts(mafia = 1, detective = 1, doctor = 0)
            playerCount == DOCTOR_ENTRY_PLAYER_COUNT -> MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1)
            playerCount in 7..8 -> MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
            playerCount == NINE_PLAYER_GAME -> MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
            playerCount in 10..12 -> MafiaRoleCounts(mafia = 3, detective = 1, doctor = 1)
            else -> MafiaRoleCounts(mafia = (playerCount / 4).coerceAtLeast(1), detective = 1, doctor = 1)
        }
        return MafiaSettings(
            roleCounts = counts,
            revealRoleOnDeath = true,
            doctorCanSelfHeal = false,
            doctorCanProtectSamePlayerConsecutively = false,
            detectiveCanInspectSelf = false,
            allowSelfVote = false,
            mafiaCanTargetMafia = false,
            voteTieBehavior = TieBehavior.REVOTE_TIED_ONLY,
            maxRevotes = 1,
            mafiaKillTieBehavior = MafiaKillTie.REVOTE,
            nightDurationSeconds = null,
            discussionDurationSeconds = null,
            voteDurationSeconds = null,
        )
    }

    private const val SMALL_GAME_MAX_PLAYERS = 5
    private const val DOCTOR_ENTRY_PLAYER_COUNT = 6
    private const val NINE_PLAYER_GAME = 9
}

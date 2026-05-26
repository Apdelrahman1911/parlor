package com.parlor.games.mafia.domain.settings

/**
 * Default presets by player count. Hosts can override every field — these are
 * starting points tuned for balance: roughly 1 Mafia per 3–4 town members,
 * Detective from 6+, Doctor from 7+. Civilians fill the remainder.
 */
object MafiaSettingsPresets {

    fun forPlayerCount(playerCount: Int): MafiaSettings {
        val counts = when {
            playerCount <= 5 -> MafiaRoleCounts(mafia = 1, detective = 1, doctor = 0)
            playerCount == 6 -> MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1)
            playerCount in 7..8 -> MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
            playerCount == 9 -> MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
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
}

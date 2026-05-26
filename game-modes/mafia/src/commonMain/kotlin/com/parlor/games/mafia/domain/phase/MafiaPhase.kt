package com.parlor.games.mafia.domain.phase

import com.parlor.engine.phase.GamePhase
import kotlinx.serialization.Serializable

/**
 * Mafia phases — the sealed flow from Setup through to PostGame.
 *
 * Night carries a coordination round so the reducer can drive an
 * anonymized Mafia revote when round 1 ends tied — see
 * `MafiaSettings.mafiaKillTieBehavior`.
 */
@Serializable
sealed class MafiaPhase : GamePhase {
    @Serializable data object Setup : MafiaPhase() { override val id = "setup" }
    @Serializable data object RoleAssignment : MafiaPhase() { override val id = "role-assignment" }

    /**
     * Night phase. `round` indexes the Mafia coordination pass: 1 = first
     * pick, 2 = anonymized revote after a tie / forced revote per settings.
     * Detective + Doctor + Civilian submissions are independent of the
     * coordination round — they run alongside.
     */
    @Serializable
    data class Night(val day: Int, val mafiaCoordinationRound: Int = 1) : MafiaPhase() {
        override val id = "night"
    }

    @Serializable
    data class NightAnnouncement(val day: Int) : MafiaPhase() {
        override val id = "night-announcement"
    }

    @Serializable
    data class Discussion(val day: Int) : MafiaPhase() {
        override val id = "discussion"
    }

    @Serializable
    data class Voting(val day: Int, val revoteRound: Int = 0) : MafiaPhase() {
        override val id = "voting"
    }

    @Serializable
    data class VoteAnnouncement(val day: Int) : MafiaPhase() {
        override val id = "vote-announcement"
    }

    @Serializable data object PostGame : MafiaPhase() { override val id = "post-game" }
}

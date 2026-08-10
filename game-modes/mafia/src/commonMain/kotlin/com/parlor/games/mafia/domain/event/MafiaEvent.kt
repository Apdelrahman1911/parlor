package com.parlor.games.mafia.domain.event

import com.parlor.core.ids.PlayerId
import com.parlor.engine.event.GameEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.Team

/**
 * One-shot signals out of the reducer. Current consumers drive UI feedback;
 * the domain contract does not imply an audio or upload provider.
 */
sealed interface MafiaEvent : GameEvent {
    data object SettingsApplied : MafiaEvent
    data object RolesAssigned : MafiaEvent
    data class PhaseEntered(val phase: MafiaPhase) : MafiaEvent

    data class NightStarted(val day: Int) : MafiaEvent
    data class MafiaCoordinationRevoteOpened(val day: Int) : MafiaEvent
    data class NightResolved(val day: Int, val killedPlayerId: PlayerId?, val wasSaved: Boolean) : MafiaEvent

    /** Private — host should route only to the named detective. */
    data class DetectiveInspectionRecorded(val day: Int, val detective: PlayerId, val target: PlayerId) : MafiaEvent

    data class VoteOpened(val day: Int, val revoteRound: Int) : MafiaEvent
    data class VoteCast(val voter: PlayerId, val target: PlayerId) : MafiaEvent
    data class VoteAbstained(val voter: PlayerId) : MafiaEvent
    data class VoteResolved(val day: Int, val eliminatedPlayerId: PlayerId?) : MafiaEvent
    data class VoteTied(val day: Int, val tiedPlayers: List<PlayerId>) : MafiaEvent

    data class WinnerDecided(val winner: Team) : MafiaEvent
    data object GameEnded : MafiaEvent
}

package com.parlor.games.whodunit.domain.event

import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.event.GameEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase

/**
 * One-shot signals out of the reducer. Consumers: sound playback, UI motion,
 * persistence triggers, telemetry.
 */
sealed interface WhodunitEvent : GameEvent {
    // Lifecycle
    data object RolesAssigned : WhodunitEvent
    data class PhaseEntered(val phase: WhodunitPhase) : WhodunitEvent
    data class PrivateRevealRequested(val playerId: PlayerId) : WhodunitEvent
    data object PrivacyConcernRaised : WhodunitEvent

    // Rounds (Phase 5)
    data class ClueRevealed(val clueId: ClueId, val text: String, val roundIndex: Int) : WhodunitEvent
    data class TimerStarted(val seconds: Int) : WhodunitEvent
    data class TimerWarning(val remainingSeconds: Int) : WhodunitEvent
    data object TimerExhausted : WhodunitEvent

    // Voting (Phase 5)
    data object VoteOpened : WhodunitEvent
    data class VoteCast(val voter: PlayerId, val target: PlayerId) : WhodunitEvent
    /** A voter declined to vote via the *refuse* button (player protest). */
    data class VoteRefused(val voter: PlayerId) : WhodunitEvent
    data class VoteTallied(val totals: Map<PlayerId, Int>) : WhodunitEvent
    data class VoteTied(val tiedPlayerIds: List<PlayerId>) : WhodunitEvent

    // Elimination Mode round outcomes
    data class PlayerEliminated(val playerId: PlayerId, val wasKiller: Boolean) : WhodunitEvent

    // Endgame
    data class WinnerDecided(val winner: Verdict) : WhodunitEvent
    data object RevealNarrativePlaying : WhodunitEvent

    // Safety (Phase 6)
    data object PauseEngaged : WhodunitEvent
    data object PauseLifted : WhodunitEvent
    data class RerolledAt(val phaseId: String) : WhodunitEvent
    data class GameEndedEarly(val withReveal: Boolean) : WhodunitEvent
}

sealed interface Verdict {
    data class PlayersWin(val killerCharacterId: String) : Verdict
    data class KillerWins(val killerCharacterId: String, val cause: KillerWinCause) : Verdict
}

enum class KillerWinCause { InnocentAccused, TieUnresolved, SurvivedToFinalTwo }

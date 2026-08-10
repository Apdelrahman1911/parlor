package com.parlor.games.whodunit.domain.event

import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.event.GameEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import kotlinx.serialization.Serializable

/**
 * One-shot signals out of the reducer. Consumers drive UI motion,
 * persistence triggers, and other in-process behavior.
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

@Serializable
sealed interface Verdict {
    @Serializable
    data class PlayersWin(val killerCharacterId: String) : Verdict
    @Serializable
    data class KillerWins(val killerCharacterId: String, val cause: KillerWinCause) : Verdict
}

@Serializable
enum class KillerWinCause {
    InnocentAccused,
    TieUnresolved,
    SurvivedToFinalTwo,
    /**
     * The session ended before a valid accusation because a player left or
     * the table explicitly ended the game. This is not a gameplay victory;
     * it exists so the reveal screen never misreports an early termination as
     * "the killer survived to the final two."
     */
    GameEndedEarly,
}

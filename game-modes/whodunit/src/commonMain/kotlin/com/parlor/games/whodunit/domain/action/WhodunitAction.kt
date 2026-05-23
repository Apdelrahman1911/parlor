package com.parlor.games.whodunit.domain.action

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction

/**
 * Whodunit's full sealed input vocabulary. Phase 4 supplied the lifecycle and
 * reveal actions; Phase 5 adds round/vote/reveal/replay; Phase 6 adds safety.
 */
sealed interface WhodunitAction : GameAction {

    // --- Lifecycle ---
    data class AssignRoles(val seed: Long) : WhodunitAction
    data object AdvanceFromIntro : WhodunitAction
    data class AdvanceBriefingCard(val index: Int) : WhodunitAction

    // --- Reveal ---
    data class StartCharacterReveal(val playerId: PlayerId) : WhodunitAction
    data class CompleteCharacterReveal(val playerId: PlayerId) : WhodunitAction
    data class OpenPrivateReview(val playerId: PlayerId) : WhodunitAction
    data class CloseHide(val playerId: PlayerId) : WhodunitAction

    // --- Rounds (Phase 5) ---
    data object RevealNextClue : WhodunitAction
    data class SubmitStructuredAction(val payload: StructuredActionPayload) : WhodunitAction
    data class StartDiscussionTimer(val seconds: Int) : WhodunitAction
    data object PauseDiscussionTimer : WhodunitAction
    data object ResumeDiscussionTimer : WhodunitAction
    data class TimerTicked(val remainingSeconds: Int) : WhodunitAction
    data object TimerExpired : WhodunitAction
    data object AdvanceFromDiscussion : WhodunitAction

    // --- Voting (Phase 5) ---
    data object OpenVote : WhodunitAction
    data class CastVote(val voter: PlayerId, val target: PlayerId) : WhodunitAction
    data class AbstainVote(val voter: PlayerId) : WhodunitAction
    /**
     * Player-facing intent to skip the ballot. Tally-equivalent to
     * [AbstainVote] (the voter contributes no count), but the reducer emits
     * a distinct `VoteRefused` event so the UI / telemetry can distinguish
     * a deliberate protest from a no-opinion abstention.
     */
    data class RefuseToVote(val voter: PlayerId) : WhodunitAction
    data object CloseVote : WhodunitAction
    data object AcknowledgeRevealCard : WhodunitAction

    // --- Reveal stage + replay ---
    data object AcknowledgeReveal : WhodunitAction
    data object BeginReplay : WhodunitAction

    // --- Safety (Phase 6) ---
    data object Pause : WhodunitAction
    data object Resume : WhodunitAction
    data class EndGameEarly(val withReveal: Boolean) : WhodunitAction
    data object RequestReroll : WhodunitAction
}

/** Payload for the round's structured-action prompt (alibi / question / accusation / monologue). */
sealed interface StructuredActionPayload {
    data class Alibi(val by: PlayerId, val text: String) : StructuredActionPayload
    data class Question(val from: PlayerId, val to: PlayerId, val text: String) : StructuredActionPayload
    data class Accusation(val by: PlayerId, val target: PlayerId) : StructuredActionPayload
    data class Monologue(val by: PlayerId, val text: String) : StructuredActionPayload
    data object NoAction : StructuredActionPayload
}

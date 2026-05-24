package com.parlor.games.whodunit.domain.action

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import kotlinx.serialization.Serializable

/**
 * Whodunit's full sealed input vocabulary. Phase 4 supplied the lifecycle and
 * reveal actions; Phase 5 adds round/vote/reveal/replay; Phase 6 adds safety.
 *
 * Annotated `@Serializable` so peer→host action submissions can ride the
 * `PeerMessage.ActionSubmit(payload: ByteArray)` wire (see
 * [com.parlor.games.whodunit.domain.action.WhodunitActionCodec]).
 * kotlinx-serialization emits a JSON polymorphic discriminator on the sealed
 * interface, so adding a new action variant is forward-compatible — peers
 * running an older app version see the discriminator and can refuse cleanly
 * instead of misinterpreting bytes.
 */
@Serializable
sealed interface WhodunitAction : GameAction {

    // --- Lifecycle ---
    @Serializable data class AssignRoles(val seed: Long) : WhodunitAction
    @Serializable data object AdvanceFromIntro : WhodunitAction
    @Serializable data class AdvanceBriefingCard(val index: Int) : WhodunitAction

    // --- Reveal ---
    @Serializable data class StartCharacterReveal(val playerId: PlayerId) : WhodunitAction
    @Serializable data class CompleteCharacterReveal(val playerId: PlayerId) : WhodunitAction
    @Serializable data class OpenPrivateReview(val playerId: PlayerId) : WhodunitAction
    @Serializable data class CloseHide(val playerId: PlayerId) : WhodunitAction

    // --- Rounds (Phase 5) ---
    @Serializable data object RevealNextClue : WhodunitAction
    @Serializable data class SubmitStructuredAction(val payload: StructuredActionPayload) : WhodunitAction
    @Serializable data class StartDiscussionTimer(val seconds: Int) : WhodunitAction
    @Serializable data object PauseDiscussionTimer : WhodunitAction
    @Serializable data object ResumeDiscussionTimer : WhodunitAction
    @Serializable data class TimerTicked(val remainingSeconds: Int) : WhodunitAction
    @Serializable data object TimerExpired : WhodunitAction
    @Serializable data object AdvanceFromDiscussion : WhodunitAction

    // --- Voting (Phase 5) ---
    @Serializable data object OpenVote : WhodunitAction
    @Serializable data class CastVote(val voter: PlayerId, val target: PlayerId) : WhodunitAction
    @Serializable data class AbstainVote(val voter: PlayerId) : WhodunitAction
    /**
     * Player-facing intent to skip the ballot. Tally-equivalent to
     * [AbstainVote] (the voter contributes no count), but the reducer emits
     * a distinct `VoteRefused` event so the UI / telemetry can distinguish
     * a deliberate protest from a no-opinion abstention.
     */
    @Serializable data class RefuseToVote(val voter: PlayerId) : WhodunitAction
    @Serializable data object CloseVote : WhodunitAction
    @Serializable data object AcknowledgeRevealCard : WhodunitAction

    // --- Reveal stage + replay ---
    @Serializable data object AcknowledgeReveal : WhodunitAction
    @Serializable data object BeginReplay : WhodunitAction

    // --- Safety (Phase 6) ---
    @Serializable data object Pause : WhodunitAction
    @Serializable data object Resume : WhodunitAction
    @Serializable data class EndGameEarly(val withReveal: Boolean) : WhodunitAction
    @Serializable data object RequestReroll : WhodunitAction
}

/** Payload for the round's structured-action prompt (alibi / question / accusation / monologue). */
@Serializable
sealed interface StructuredActionPayload {
    @Serializable data class Alibi(val by: PlayerId, val text: String) : StructuredActionPayload
    @Serializable data class Question(val from: PlayerId, val to: PlayerId, val text: String) : StructuredActionPayload
    @Serializable data class Accusation(val by: PlayerId, val target: PlayerId) : StructuredActionPayload
    @Serializable data class Monologue(val by: PlayerId, val text: String) : StructuredActionPayload
    @Serializable data object NoAction : StructuredActionPayload
}

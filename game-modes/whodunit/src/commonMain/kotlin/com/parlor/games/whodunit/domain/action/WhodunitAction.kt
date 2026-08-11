package com.parlor.games.whodunit.domain.action

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import kotlinx.serialization.Serializable

/**
 * Whodunit's complete shipping input vocabulary. Every action is explicitly
 * authorized and reduced; unsupported legacy structured actions are not part
 * of this sealed contract.
 *
 * Annotated `@Serializable` so peer→host action submissions can ride the
 * `PeerMessage.ClientCommand(payload: ByteArray)` wire (see
 * [com.parlor.games.whodunit.domain.action.WhodunitActionCodec]).
 * kotlinx-serialization emits a JSON polymorphic discriminator on the sealed
 * interface. Compatibility is still governed by the negotiated game version:
 * strict older decoders are not expected to understand new variants or newly
 * required fields.
 */
@Serializable
sealed interface WhodunitAction : GameAction {

    // --- Lifecycle ---
    @Serializable data class AssignRoles(val seed: Long) : WhodunitAction
    @Serializable data object AdvanceFromIntro : WhodunitAction
    @Serializable data class AdvanceBriefingCard(val index: Int) : WhodunitAction

    // --- Reveal ---
    @Serializable
    data class StartCharacterReveal(
        val playerId: PlayerId,
        val roleAssignmentGeneration: Long,
    ) : WhodunitAction

    @Serializable
    data class CompleteCharacterReveal(
        val playerId: PlayerId,
        val roleAssignmentGeneration: Long,
    ) : WhodunitAction

    // --- Multiplayer readiness ---
    /** Peer signals they've read the case intro. SelfActor. */
    @Serializable data class AcknowledgeIntro(val playerId: PlayerId) : WhodunitAction
    /** Peer signals they're ready to start after the briefing. SelfActor. */
    @Serializable data class AcknowledgeBriefing(val playerId: PlayerId) : WhodunitAction
    /**
     * Host advances from CharacterReveal (simultaneous-reveal model) to
     * Round(1). Gated by `PartyReadiness.isComplete(rolesViewed, active)`.
     * HostOnly.
     */
    @Serializable data object AdvanceFromCharacterReveal : WhodunitAction

    // --- Multiplayer connection rules ---
    /** Host bridge submits when it detects a peer drop. HostOnly. */
    @Serializable data class MarkPlayerDisconnected(val playerId: PlayerId) : WhodunitAction
    /** Host bridge submits when a disconnected peer rejoins within the grace period. HostOnly. */
    @Serializable data class MarkPlayerReconnected(val playerId: PlayerId) : WhodunitAction
    /**
     * Compatibility name for the host's disconnect-grace expiry action.
     * Whodunit cannot continue with a missing dossier, so this ends the
     * session and reveals the case; it never removes [playerId] from the
     * roster. HostOnly.
     */
    @Serializable data class ContinueWithoutPlayer(val playerId: PlayerId) : WhodunitAction
    // --- Rounds ---
    @Serializable data object RevealNextClue : WhodunitAction
    @Serializable data class StartDiscussionTimer(val seconds: Int) : WhodunitAction
    @Serializable data object PauseDiscussionTimer : WhodunitAction
    @Serializable data object ResumeDiscussionTimer : WhodunitAction
    @Serializable data class TimerTicked(val remainingSeconds: Int) : WhodunitAction
    @Serializable data object TimerExpired : WhodunitAction
    @Serializable data object AdvanceFromDiscussion : WhodunitAction

    // --- Voting ---
    @Serializable data object OpenVote : WhodunitAction
    @Serializable data class CastVote(val voter: PlayerId, val target: PlayerId) : WhodunitAction
    @Serializable data class AbstainVote(val voter: PlayerId) : WhodunitAction
    /**
     * Player-facing intent to skip the ballot. Tally-equivalent to
     * [AbstainVote] (the voter contributes no count), but the reducer emits
     * a distinct `VoteRefused` event so UI observers can distinguish
     * a deliberate protest from a no-opinion abstention.
     */
    @Serializable data class RefuseToVote(val voter: PlayerId) : WhodunitAction
    @Serializable data object CloseVote : WhodunitAction
    @Serializable data object AcknowledgeRevealCard : WhodunitAction

    // --- Reveal stage + replay ---
    @Serializable data object AcknowledgeReveal : WhodunitAction
    @Serializable data object BeginReplay : WhodunitAction

    // --- Safety and recovery ---
    @Serializable data object Pause : WhodunitAction
    @Serializable data object Resume : WhodunitAction
    @Serializable data class EndGameEarly(val withReveal: Boolean) : WhodunitAction
    @Serializable data object RequestReroll : WhodunitAction
}

package com.parlor.games.whodunit.domain.authority

import com.parlor.core.ids.PlayerId
import com.parlor.games.whodunit.domain.action.StructuredActionPayload
import com.parlor.games.whodunit.domain.action.WhodunitAction

/**
 * Authority scope of a single [WhodunitAction] — who is allowed to submit it.
 *
 * The host is always allowed to submit anything (they ARE the canonical
 * reducer; this policy is only consulted for actions that arrive over the
 * wire from a peer). Peers may only submit:
 *  - [SelfActor] actions where the action's named actor is the peer itself.
 *  - never [HostOnly] actions, which control shared game progression.
 */
sealed interface AuthorityScope {
    data object HostOnly : AuthorityScope
    data class SelfActor(val actor: PlayerId) : AuthorityScope
}

/**
 * Authority policy for Whodunit. Pure — no IO, no DI, no engine coupling
 * beyond the action sealed family — so this is the load-bearing rule peers
 * and host both agree on, exhaustively tested in
 * `WhodunitActionAuthorityTest`.
 */
object WhodunitActionAuthority {

    fun classify(action: WhodunitAction): AuthorityScope = when (action) {
        // Shared game progression — only the host controls these.
        is WhodunitAction.AssignRoles,
        WhodunitAction.AdvanceFromIntro,
        is WhodunitAction.AdvanceBriefingCard,
        is WhodunitAction.StartCharacterReveal,
        WhodunitAction.RevealNextClue,
        is WhodunitAction.StartDiscussionTimer,
        WhodunitAction.PauseDiscussionTimer,
        WhodunitAction.ResumeDiscussionTimer,
        is WhodunitAction.TimerTicked,
        WhodunitAction.TimerExpired,
        WhodunitAction.AdvanceFromDiscussion,
        WhodunitAction.OpenVote,
        WhodunitAction.CloseVote,
        WhodunitAction.AcknowledgeRevealCard,
        WhodunitAction.AcknowledgeReveal,
        WhodunitAction.BeginReplay,
        WhodunitAction.Pause,
        WhodunitAction.Resume,
        is WhodunitAction.EndGameEarly,
        WhodunitAction.RequestReroll,
        is WhodunitAction.MarkPlayerDisconnected,
        is WhodunitAction.MarkPlayerReconnected,
        is WhodunitAction.ContinueWithoutPlayer,
        is WhodunitAction.ReadmitPlayer -> AuthorityScope.HostOnly

        // Self-actor: only the named player may submit.
        is WhodunitAction.CompleteCharacterReveal -> AuthorityScope.SelfActor(action.playerId)
        is WhodunitAction.OpenPrivateReview -> AuthorityScope.SelfActor(action.playerId)
        is WhodunitAction.CloseHide -> AuthorityScope.SelfActor(action.playerId)
        is WhodunitAction.AcknowledgeIntro -> AuthorityScope.SelfActor(action.playerId)
        is WhodunitAction.AcknowledgeBriefing -> AuthorityScope.SelfActor(action.playerId)
        is WhodunitAction.ConfirmRoleViewed -> AuthorityScope.SelfActor(action.playerId)
        is WhodunitAction.CastVote -> AuthorityScope.SelfActor(action.voter)
        is WhodunitAction.AbstainVote -> AuthorityScope.SelfActor(action.voter)
        is WhodunitAction.RefuseToVote -> AuthorityScope.SelfActor(action.voter)
        is WhodunitAction.SubmitStructuredAction -> when (val p = action.payload) {
            is StructuredActionPayload.Alibi -> AuthorityScope.SelfActor(p.by)
            is StructuredActionPayload.Question -> AuthorityScope.SelfActor(p.from)
            is StructuredActionPayload.Accusation -> AuthorityScope.SelfActor(p.by)
            is StructuredActionPayload.Monologue -> AuthorityScope.SelfActor(p.by)
            StructuredActionPayload.NoAction -> AuthorityScope.HostOnly
        }
    }

    /**
     * True iff the wire-attested [senderId] is permitted to submit [action]
     * given the host identity [hostId] and the set of game-dropped players
     * [droppedPlayers].
     *
     * The host is implicitly trusted (they never call this on their own
     * actions); this is consulted by the host on inbound peer
     * [com.parlor.networking.protocol.PeerMessage.ActionSubmit].
     *
     * Three-rule gate:
     *  1. HostOnly actions: only the host may submit.
     *  2. SelfActor actions: only the named actor may submit.
     *  3. SelfActor actions with an actor in [droppedPlayers]: rejected.
     *     A dropped player is a spectator — their stale or queued actions
     *     cannot mutate game state regardless of the sender identity. This
     *     is the load-bearing "dropped spectator enforcement" rule.
     *
     * [droppedPlayers] defaults to `emptySet()` for backwards compatibility
     * with call sites that don't yet pass the dropped set. The host bridge
     * passes `state.public.droppedPlayers` to enforce rule 3.
     */
    fun isAllowed(
        action: WhodunitAction,
        senderId: PlayerId,
        hostId: PlayerId,
        droppedPlayers: Set<PlayerId> = emptySet(),
    ): Boolean = when (val scope = classify(action)) {
        AuthorityScope.HostOnly -> senderId == hostId
        is AuthorityScope.SelfActor -> {
            if (scope.actor in droppedPlayers) false
            else senderId == scope.actor
        }
    }
}

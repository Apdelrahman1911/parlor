package com.parlor.games.mafia.domain.authority

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.action.MafiaAction

/**
 * Authority scope of a single [MafiaAction] — who is allowed to submit it.
 *
 * The host is always allowed to submit anything (they ARE the canonical
 * reducer; this policy is only consulted for actions that arrive over the
 * wire from a peer).
 */
sealed interface AuthorityScope {
    data object HostOnly : AuthorityScope
    data class SelfActor(val actor: PlayerId) : AuthorityScope
}

/**
 * Authority policy for Mafia. Mirrors Whodunit's pattern: a structural rule
 * the host bridge consults on every peer-submitted action.
 *
 * Critical: peer-submitted privacy-bearing actions (Mafia kill vote, Detective
 * inspect, Doctor protect, Civilian suspicion) are classified by submitter
 * identity ONLY. The host bridge MUST additionally check the submitter's
 * role before applying the action — a non-Mafia peer claiming to submit a
 * `SubmitMafiaKillVote` for themselves is rejected at this layer by sender-
 * mismatch, but a Mafia peer's submission is gated by role inside the
 * reducer (`SubmitMafiaKillVote` is a no-op for non-Mafia actors).
 */
object MafiaActionAuthority {

    fun classify(action: MafiaAction): AuthorityScope = when (action) {
        // Host-only lifecycle and announcements.
        is MafiaAction.ApplySettings,
        MafiaAction.StartGame,
        MafiaAction.AdvanceFromRoleAssignment,
        MafiaAction.ResolveNight,
        MafiaAction.OpenDiscussion,
        MafiaAction.OpenVote,
        MafiaAction.CloseVote,
        MafiaAction.AdvanceFromVoteAnnouncement,
        MafiaAction.EndGame,
        is MafiaAction.MarkPlayerDisconnected,
        is MafiaAction.MarkPlayerReconnected,
        is MafiaAction.ContinueWithoutPlayer -> AuthorityScope.HostOnly

        // Self-actor.
        is MafiaAction.AcknowledgeRoleViewed -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.SubmitMafiaKillVote -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.SubmitDoctorProtect -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.SubmitDetectiveInspect -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.SubmitCivilianSuspicion -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.AcknowledgeNightAnnouncement -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.AcknowledgeDetectiveResult -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.CastVote -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.AbstainVote -> AuthorityScope.SelfActor(action.by)
        is MafiaAction.AcknowledgeVoteAnnouncement -> AuthorityScope.SelfActor(action.by)
    }

    /**
     * True iff the wire-attested [senderId] is permitted to submit [action]
     * given the host identity [hostId] and the set of game-dropped players.
     */
    fun isAllowed(
        action: MafiaAction,
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

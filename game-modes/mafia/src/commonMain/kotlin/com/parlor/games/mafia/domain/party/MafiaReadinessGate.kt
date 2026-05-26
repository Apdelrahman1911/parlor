package com.parlor.games.mafia.domain.party

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.session.party.PartyReadinessGate
import com.parlor.session.party.PendingAck

/**
 * Mafia's contribution to the generic local-mode auto-ack contract.
 *
 * The reducer gates three host advances on per-player ack sets:
 *
 *  - `AdvanceFromRoleAssignment` requires every active-roster player's
 *    `MafiaPrivate.roleAcknowledged == true`.
 *  - `OpenDiscussion` requires every active **and alive** player's
 *    `MafiaPrivate.nightAcknowledged == true`.
 *  - `AdvanceFromVoteAnnouncement` requires every active and alive
 *    player's `MafiaPrivate.voteAcknowledged == true`.
 *
 * Multi-device peers send those acks themselves. Single-device modes
 * never do — there are no peer devices — and this gate is what
 * [com.parlor.session.party.PartyAwareSession] uses to fill them in
 * before the gated action lands.
 */
object MafiaReadinessGate : PartyReadinessGate<MafiaState, MafiaAction> {

    override fun pendingAcks(
        state: MafiaState,
        hostAction: MafiaAction,
    ): List<PendingAck<MafiaAction>> = when (hostAction) {

        MafiaAction.AdvanceFromRoleAssignment -> pendingFor(
            ids = activeRoster(state),
            acked = { id -> state.privatePerPlayer[id]?.roleAcknowledged == true },
            buildAck = { id -> MafiaAction.AcknowledgeRoleViewed(id) },
        )

        MafiaAction.OpenDiscussion -> {
            if (state.phase !is MafiaPhase.NightAnnouncement) emptyList()
            else pendingFor(
                ids = activeAlive(state),
                acked = { id -> state.privatePerPlayer[id]?.nightAcknowledged == true },
                buildAck = { id -> MafiaAction.AcknowledgeNightAnnouncement(id) },
            )
        }

        MafiaAction.AdvanceFromVoteAnnouncement -> {
            if (state.phase !is MafiaPhase.VoteAnnouncement) emptyList()
            else pendingFor(
                ids = activeAlive(state),
                acked = { id -> state.privatePerPlayer[id]?.voteAcknowledged == true },
                buildAck = { id -> MafiaAction.AcknowledgeVoteAnnouncement(id) },
            )
        }

        else -> emptyList()
    }

    private inline fun pendingFor(
        ids: List<PlayerId>,
        acked: (PlayerId) -> Boolean,
        buildAck: (PlayerId) -> MafiaAction,
    ): List<PendingAck<MafiaAction>> =
        ids.filterNot(acked).map { id -> PendingAck(id, buildAck(id)) }

    private fun activeRoster(state: MafiaState): List<PlayerId> =
        state.players.map { it.id }.filterNot { it in state.public.droppedPlayers }

    private fun activeAlive(state: MafiaState): List<PlayerId> {
        val aliveIds = state.public.roster.filter { it.alive }.map { it.playerId }.toSet()
        return activeRoster(state).filter { it in aliveIds }
    }
}

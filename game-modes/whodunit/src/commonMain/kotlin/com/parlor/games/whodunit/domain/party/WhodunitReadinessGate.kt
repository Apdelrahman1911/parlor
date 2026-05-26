package com.parlor.games.whodunit.domain.party

import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.state.PartyReadiness
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.session.party.PartyReadinessGate
import com.parlor.session.party.PendingAck

/**
 * Whodunit's contribution to the generic local-mode auto-ack contract.
 *
 * The reducer gates three host advances on per-player ack sets:
 *
 *  - `AdvanceFromIntro` requires every active-roster player in
 *    `state.public.introAcknowledged`.
 *  - `AdvanceBriefingCard(index)` requires every active-roster player
 *    in `state.public.briefingReady` **only when `index` advances past
 *    the final card**; card-by-card flips are ungated.
 *  - `AdvanceFromCharacterReveal` requires every active-roster player
 *    in `state.public.rolesViewed`.
 *
 * Multi-device peers send those acks themselves. Single-device modes
 * never do — there are no peer devices — and this gate is what
 * [com.parlor.session.party.PartyAwareSession] uses to fill them in
 * before the gated action lands.
 *
 * Adding a 4th gated phase later: add one branch here. UI buttons and
 * the router stay untouched.
 */
object WhodunitReadinessGate : PartyReadinessGate<WhodunitState, WhodunitAction> {

    override fun pendingAcks(
        state: WhodunitState,
        hostAction: WhodunitAction,
    ): List<PendingAck<WhodunitAction>> = when (hostAction) {

        is WhodunitAction.AdvanceFromIntro -> pendingFor(
            state = state,
            readinessSet = state.public.introAcknowledged,
            buildAck = { id -> WhodunitAction.AcknowledgeIntro(id) },
        )

        is WhodunitAction.AdvanceBriefingCard ->
            // Only the final advance (past the last card) is gated; card-
            // by-card flips submit AdvanceBriefingCard too but with a
            // smaller index — there's no harm in auto-acking on every
            // call since the reducer's ack action is idempotent.
            pendingFor(
                state = state,
                readinessSet = state.public.briefingReady,
                buildAck = { id -> WhodunitAction.AcknowledgeBriefing(id) },
            )

        is WhodunitAction.AdvanceFromCharacterReveal -> pendingFor(
            state = state,
            readinessSet = state.public.rolesViewed,
            buildAck = { id -> WhodunitAction.ConfirmRoleViewed(id) },
        )

        else -> emptyList()
    }

    private inline fun pendingFor(
        state: WhodunitState,
        readinessSet: Set<com.parlor.core.ids.PlayerId>,
        buildAck: (com.parlor.core.ids.PlayerId) -> WhodunitAction,
    ): List<PendingAck<WhodunitAction>> {
        val active = PartyReadiness.activeRoster(state.players, state.public.droppedPlayers)
        return active
            .map { it.id }
            .filterNot { it in readinessSet }
            .map { id -> PendingAck(id, buildAck(id)) }
    }
}

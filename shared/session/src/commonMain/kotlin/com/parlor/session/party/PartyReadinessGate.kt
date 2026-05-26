package com.parlor.session.party

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.engine.state.GameState

/**
 * A game-specific policy describing which actions are **gated host
 * advances** and which per-player **ack actions** unblock them.
 *
 * The Whodunit reducer (for example) refuses to advance from the public
 * intro until every active-roster player has submitted
 * `AcknowledgeIntro`. Multi-device peers send those acks from their own
 * phones. Single-device modes can't — no peer device exists — so a
 * [com.parlor.session.party.PartyAwareSession] wrapping the local session
 * controller asks this gate "what acks are still pending for this host
 * advance?" and auto-issues them before passing the gated action through.
 *
 * Each game module that has readiness gates implements this contract
 * once. The wrapper is generic; the gate carries the game-specific
 * knowledge.
 */
interface PartyReadinessGate<S : GameState, A : GameAction> {

    /**
     * Given the current state and a host action about to be submitted,
     * return the per-player ack actions that still need to be issued so
     * the gated advance can succeed. Return an empty list if [hostAction]
     * is not a gated advance, or if the gate is already satisfied.
     */
    fun pendingAcks(state: S, hostAction: A): List<PendingAck<A>>
}

/**
 * A pending ack that the wrapper should submit before the gated host
 * action. [playerId] is informational (mostly for tests / logs); the
 * wrapper only submits [ackAction].
 */
data class PendingAck<A : GameAction>(
    val playerId: PlayerId,
    val ackAction: A,
)

package com.parlor.games.whodunit.ui.flow.party

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.PartyReadiness
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState

/**
 * Read-only view over a [WhodunitState] that surfaces *only* the
 * Party Play–relevant slices for UI. The screens never poke the raw
 * controller directly — they read these helpers so the "is the advance
 * gate open" decision lives in domain code, not Compose branches.
 *
 * Stateless. Constructed fresh on every recomposition; the underlying
 * [WhodunitState] is the source of truth.
 */
data class PartyFlowController(val state: WhodunitState) {

    val public: WhodunitPublic get() = state.public
    val phase: WhodunitPhase get() = state.phase
    val players: List<Player> get() = state.players

    /** Active roster = players minus `droppedPlayers`. UI renders against this. */
    val activeRoster: List<Player>
        get() = PartyReadiness.activeRoster(players, public.droppedPlayers)

    /**
     * The readiness set the current phase gates on. The host's advance
     * button is enabled iff every active-roster player's id is in this set.
     * Returns `null` outside the three gated phases.
     */
    fun readinessFor(phase: WhodunitPhase): Set<PlayerId>? = when (phase) {
        WhodunitPhase.PublicIntro -> public.introAcknowledged
        WhodunitPhase.RulesBriefing -> public.briefingReady
        is WhodunitPhase.CharacterReveal -> public.rolesViewed
        else -> null
    }

    /** True iff readiness for [phase] is complete; the advance CTA is enabled. */
    fun canAdvance(phase: WhodunitPhase): Boolean {
        val set = readinessFor(phase) ?: return true
        return PartyReadiness.isComplete(set, activeRoster)
    }

    /** Players whose ack the host is still waiting on, for "Waiting on X, Y" text. */
    fun pendingFor(phase: WhodunitPhase): List<Player> {
        val set = readinessFor(phase) ?: return emptyList()
        return PartyReadiness.pending(set, activeRoster)
    }

    /** "X" in "X of N ready". */
    fun readyCountFor(phase: WhodunitPhase): Int {
        val set = readinessFor(phase) ?: return activeRoster.size
        return PartyReadiness.readyCount(set, activeRoster)
    }

    /** "N" in "X of N ready". */
    val activeCount: Int get() = activeRoster.size

    /** True when at least one active-roster player is currently disconnected. */
    val hasDisconnectedActiveRoster: Boolean
        get() = activeRoster.any { it.id in public.disconnectedPlayers }
}

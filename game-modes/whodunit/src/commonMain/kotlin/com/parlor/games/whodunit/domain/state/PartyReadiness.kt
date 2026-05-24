package com.parlor.games.whodunit.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player

/**
 * Pure helpers for the Party Play readiness invariant: "have all active-roster
 * players acknowledged this step yet?"
 *
 * Active roster = players minus dropped players. Disconnected players are NOT
 * subtracted — they still count, so the host must either wait for them to
 * reconnect or explicitly drop them via `ContinueWithoutPlayer`. This rule is
 * enforced by reducer + UI both reading [isComplete] from this helper, so
 * there is one source of truth for "is the advance gate open".
 *
 * All functions are pure and side-effect free; takeable from reducer or UI.
 */
object PartyReadiness {

    /**
     * Active roster: the set of players whose acknowledgements (or votes,
     * or role-views) the host is *currently* waiting on. Dropped players
     * are excluded — the host has explicitly chosen to continue without them.
     */
    fun activeRoster(
        players: List<Player>,
        droppedPlayers: Set<PlayerId>,
    ): List<Player> = players.filter { it.id !in droppedPlayers }

    /**
     * True iff every active-roster player's id is in [readinessSet].
     * `readinessSet` is one of `public.introAcknowledged`,
     * `public.briefingReady`, `public.rolesViewed`, or the equivalent
     * vote-cast set; the helper is generic over which one.
     */
    fun isComplete(
        readinessSet: Set<PlayerId>,
        activeRoster: List<Player>,
    ): Boolean = activeRoster.all { it.id in readinessSet }

    /**
     * Active-roster players who have NOT yet acknowledged. UI consumes
     * this to render "waiting on Alice, Bob" hints.
     */
    fun pending(
        readinessSet: Set<PlayerId>,
        activeRoster: List<Player>,
    ): List<Player> = activeRoster.filter { it.id !in readinessSet }

    /**
     * Count of active-roster players who have acknowledged. Used for
     * "X of N ready" chips.
     */
    fun readyCount(
        readinessSet: Set<PlayerId>,
        activeRoster: List<Player>,
    ): Int = activeRoster.count { it.id in readinessSet }
}

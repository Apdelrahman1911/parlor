package com.parlor.games.whodunit.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.state.PartyReadiness
import kotlin.test.Test

/**
 * Pure tests for the `PartyReadiness` helper. The helper is consumed by both
 * the reducer (as the canonical advance-gate invariant) and the UI (as the
 * "X of N ready" / "waiting on Alice, Bob" data source). Pinning the small
 * algebra here so both consumers can rely on it.
 */
class PartyReadinessTest {

    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val cara = PlayerId("cara")

    private val table = listOf(
        Player(alice, "Alice", seat = 0),
        Player(bob, "Bob", seat = 1),
        Player(cara, "Cara", seat = 2),
    )

    @Test
    fun active_roster_excludes_dropped_players() {
        val active = PartyReadiness.activeRoster(table, droppedPlayers = setOf(bob))
        assertThat(active.map { it.id }).containsExactly(alice, cara)
    }

    @Test
    fun active_roster_is_full_table_when_nobody_dropped() {
        val active = PartyReadiness.activeRoster(table, droppedPlayers = emptySet())
        assertThat(active).isEqualTo(table)
    }

    @Test
    fun is_complete_false_for_empty_set() {
        val active = PartyReadiness.activeRoster(table, emptySet())
        assertThat(PartyReadiness.isComplete(emptySet(), active)).isFalse()
    }

    @Test
    fun is_complete_false_for_partial_set() {
        val active = PartyReadiness.activeRoster(table, emptySet())
        assertThat(PartyReadiness.isComplete(setOf(alice), active)).isFalse()
        assertThat(PartyReadiness.isComplete(setOf(alice, bob), active)).isFalse()
    }

    @Test
    fun is_complete_true_when_every_active_player_acknowledged() {
        val active = PartyReadiness.activeRoster(table, emptySet())
        assertThat(PartyReadiness.isComplete(setOf(alice, bob, cara), active)).isTrue()
    }

    @Test
    fun is_complete_ignores_dropped_players() {
        val active = PartyReadiness.activeRoster(table, droppedPlayers = setOf(bob))
        // bob is dropped, so only alice + cara count.
        assertThat(PartyReadiness.isComplete(setOf(alice, cara), active)).isTrue()
        // and bob being in the set doesn't help if alice is missing.
        assertThat(PartyReadiness.isComplete(setOf(bob, cara), active)).isFalse()
    }

    @Test
    fun pending_returns_unacknowledged_players() {
        val active = PartyReadiness.activeRoster(table, emptySet())
        assertThat(PartyReadiness.pending(setOf(alice), active).map { it.id })
            .containsExactly(bob, cara)
        assertThat(PartyReadiness.pending(setOf(alice, bob, cara), active)).isEmpty()
    }

    @Test
    fun ready_count_only_counts_active_roster() {
        val active = PartyReadiness.activeRoster(table, droppedPlayers = setOf(bob))
        // bob is dropped: even if his id is in the readinessSet, he doesn't count.
        assertThat(PartyReadiness.readyCount(setOf(alice, bob), active)).isEqualTo(1)
        assertThat(PartyReadiness.readyCount(setOf(alice, cara), active)).isEqualTo(2)
    }

    @Test
    fun unknown_id_in_readiness_set_does_not_affect_completeness() {
        val ghost = PlayerId("ghost-from-an-old-snapshot")
        val active = PartyReadiness.activeRoster(table, emptySet())
        // Ghost id is in the set but not in the active roster — completeness
        // is computed against the active roster, so ghost is ignored.
        assertThat(PartyReadiness.isComplete(setOf(ghost), active)).isFalse()
        assertThat(PartyReadiness.isComplete(setOf(alice, bob, cara, ghost), active)).isTrue()
    }
}

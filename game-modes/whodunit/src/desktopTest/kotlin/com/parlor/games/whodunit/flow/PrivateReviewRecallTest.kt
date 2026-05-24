package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.games.whodunit.ui.flow.party.LocalRolePeekState
import kotlin.test.Test

/**
 * Wave 9H-7: role-recall is purely local — the [LocalRolePeekState]
 * machine cycles through cover / gate / dossier / hide / closed
 * without ever producing a domain action. The test pins the
 * progression and proves the state is opt-in: stage starts Closed
 * and only advances on explicit calls.
 */
class PrivateReviewRecallTest {

    @Test
    fun initial_stage_is_closed_and_overlay_is_hidden() {
        val state = LocalRolePeekState()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Closed)
        assertThat(state.isOpen).isFalse()
    }

    @Test
    fun open_then_advance_walks_through_full_cycle_back_to_closed() {
        val state = LocalRolePeekState()
        state.open()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Cover)
        assertThat(state.isOpen).isTrue()

        state.advance()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Gate)

        state.advance()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Dossier)

        state.advance()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Hide)

        state.advance()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Closed)
        assertThat(state.isOpen).isFalse()
    }

    @Test
    fun close_returns_to_closed_from_any_stage() {
        val state = LocalRolePeekState()
        state.open()
        state.advance()  // Gate
        state.advance()  // Dossier
        state.close()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Closed)
    }

    @Test
    fun advance_from_closed_starts_cycle_at_cover() {
        // Same as open() — defensive sanity that the user can re-enter
        // the cycle via either entry point.
        val state = LocalRolePeekState()
        state.advance()
        assertThat(state.stage).isEqualTo(LocalRolePeekState.Stage.Cover)
    }
}

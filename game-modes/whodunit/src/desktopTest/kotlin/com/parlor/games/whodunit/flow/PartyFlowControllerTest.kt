package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.party.PartyFlowController
import kotlin.test.Test

/**
 * Wave 9H-7: [PartyFlowController] is a thin read-only view over
 * [WhodunitState] that exposes the readiness math the UI needs. The
 * test asserts the views agree with the underlying readiness sets and
 * that dropped players are excluded from the active roster.
 */
class PartyFlowControllerTest {

    private val host = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val players = listOf(
        Player(host, "Host", seat = 0),
        Player(alice, "Alice", seat = 1),
        Player(bob, "Bob", seat = 2),
    )

    private fun state(
        phase: WhodunitPhase,
        intro: Set<PlayerId> = emptySet(),
        dropped: Set<PlayerId> = emptySet(),
    ): WhodunitState = WhodunitState(
        public = WhodunitPublic(
            caseId = CaseId("c"),
            modeId = ModeId("m"),
            playersAtTable = players,
            introAcknowledged = intro,
            droppedPlayers = dropped,
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = WhodunitHostOnly(
            killerId = host,
            killerCharacterId = CharacterId("X"),
            randomSeed = 1L,
            seatToCharacter = emptyMap(),
            redHerringTargets = emptyList(),
        ),
        phase = phase,
        players = players,
    )

    @Test
    fun activeRoster_excludes_dropped_players() {
        val controller = PartyFlowController(state(WhodunitPhase.PublicIntro, dropped = setOf(bob)))
        assertThat(controller.activeRoster.map { it.id })
            .containsExactlyInAnyOrder(host, alice)
    }

    @Test
    fun canAdvance_false_when_some_active_players_have_not_acked() {
        val controller = PartyFlowController(state(WhodunitPhase.PublicIntro, intro = setOf(host)))
        assertThat(controller.canAdvance(WhodunitPhase.PublicIntro)).isFalse()
    }

    @Test
    fun canAdvance_true_when_all_active_players_have_acked() {
        val controller = PartyFlowController(
            state(WhodunitPhase.PublicIntro, intro = setOf(host, alice, bob)),
        )
        assertThat(controller.canAdvance(WhodunitPhase.PublicIntro)).isTrue()
    }

    @Test
    fun canAdvance_ignores_dropped_players_for_readiness() {
        val controller = PartyFlowController(
            state(
                WhodunitPhase.PublicIntro,
                intro = setOf(host, alice),
                dropped = setOf(bob),
            ),
        )
        // Bob is dropped, so the gate opens once Host + Alice are in the set.
        assertThat(controller.canAdvance(WhodunitPhase.PublicIntro)).isTrue()
    }

    @Test
    fun pendingFor_lists_only_active_unacked_players() {
        val controller = PartyFlowController(
            state(WhodunitPhase.PublicIntro, intro = setOf(host), dropped = setOf(bob)),
        )
        assertThat(controller.pendingFor(WhodunitPhase.PublicIntro).map { it.id })
            .containsExactlyInAnyOrder(alice)
    }

    @Test
    fun readinessFor_other_phases_returns_null() {
        val controller = PartyFlowController(state(WhodunitPhase.Round(1)))
        assertThat(controller.readinessFor(WhodunitPhase.Round(1)) ?: emptySet<PlayerId>())
            .isEmpty()
        // Round isn't readiness-gated → canAdvance falls through to true.
        assertThat(controller.canAdvance(WhodunitPhase.Round(1))).isTrue()
    }

    @Test
    fun readyCountFor_counts_active_acked_players() {
        val controller = PartyFlowController(
            state(
                WhodunitPhase.PublicIntro,
                intro = setOf(host, alice),
                dropped = setOf(bob),
            ),
        )
        assertThat(controller.readyCountFor(WhodunitPhase.PublicIntro)).isEqualTo(2)
        assertThat(controller.activeCount).isEqualTo(2)
    }
}

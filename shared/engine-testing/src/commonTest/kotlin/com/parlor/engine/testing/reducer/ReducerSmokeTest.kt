package com.parlor.engine.testing.reducer

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import com.parlor.engine.testing.fakes.RrAction
import com.parlor.engine.testing.fakes.RrEvent
import com.parlor.engine.testing.fakes.RrPhase
import kotlin.time.Instant
import kotlin.test.Test

/**
 * Engine smoke test: a trivial test game runs end-to-end through the reducer
 * with phase transitions and event emission. Lives in :shared:engine-testing
 * so the fakes it depends on don't create a project cycle back to engine.
 */
class ReducerSmokeTest {

    private val clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000))
    private val ctx = DefaultReducerContext(clock = clock, random = RandomSource.seeded(42))

    private val game = RoundRobinAnnounceGame()
    private val reducer = game.reducer()
    private val players = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
        Player(PlayerId("p3"), "Cara", seat = 2),
    )

    private fun initialState() = game.createInitialState(
        SessionConfig(
            sessionId = SessionId("s1"),
            caseId = CaseId("none"),
            modeId = ModeId("round-robin"),
            players = players,
            randomSeed = 42L,
        ),
    )

    @Test
    fun reducer_runs_to_completion() {
        var state = initialState()
        val allEvents = mutableListOf<RrEvent>()

        players.forEach { player ->
            val reduction = reducer.reduce(state, RrAction.Announce(player.id), ctx)
            state = reduction.newState
            allEvents += reduction.events
        }

        assertThat(state.phase).isInstanceOf(RrPhase.Finished::class)
        assertThat(state.announcedBy).containsExactly(
            PlayerId("p1"), PlayerId("p2"), PlayerId("p3"),
        )
        assertThat(allEvents).hasSize(4)
        assertThat(allEvents.last()).isInstanceOf(RrEvent.SessionEnded::class)
    }

    @Test
    fun out_of_turn_announcement_is_rejected() {
        val state = initialState()
        val reduction = reducer.reduce(state, RrAction.Announce(PlayerId("p2")), ctx)
        assertThat(reduction.newState).isEqualTo(state)
        assertThat(reduction.events).hasSize(0)
    }
}

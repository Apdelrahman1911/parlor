package com.parlor.session.passandplay

import assertk.assertThat
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
import com.parlor.engine.testing.fakes.RrPhase
import com.parlor.engine.testing.fakes.RrState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

/**
 * Phase 6.2 contract: `PassAndPlaySessionController` accepts an optional
 * `restoredState`. When provided, the controller starts at that state instead
 * of `definition.createInitialState(config)` — the foundation of resume.
 */
class RestoredStateTest {

    private fun fakeConfig() = SessionConfig(
        sessionId = SessionId("resumed"),
        caseId = CaseId("none"),
        modeId = ModeId("round-robin"),
        players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
            Player(PlayerId("p3"), "Cara", seat = 2),
        ),
        randomSeed = 42L,
    )

    private fun reducerCtx() = DefaultReducerContext(
        clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(42),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun null_restoredState_starts_at_initial_state() = runTest {
        val controllerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val game = RoundRobinAnnounceGame()
        val config = fakeConfig()
        val controller = PassAndPlaySessionController(game, config, reducerCtx(), controllerScope)

        // Default state: Announcing(seat = 0), nobody has announced.
        val state = controller.publicState.value.state
        assertThat(state.phase).isInstanceOf(RrPhase.Announcing::class)
        assertThat((state.phase as RrPhase.Announcing).currentSeat).isEqualTo(0)
        assertThat(state.announcedBy).isEqualTo(emptyList())
        controller.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restoredState_boots_controller_at_provided_state() = runTest {
        val controllerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val game = RoundRobinAnnounceGame()
        val config = fakeConfig()

        // A mid-game state: two players have already announced; expected next
        // seat is 2 (Cara).
        val midState = RrState(
            phase = RrPhase.Announcing(currentSeat = 2),
            players = config.players,
            announcedBy = listOf(PlayerId("p1"), PlayerId("p2")),
        )

        val controller = PassAndPlaySessionController(
            definition = game,
            config = config,
            reducerContext = reducerCtx(),
            scope = controllerScope,
            restoredState = midState,
        )

        // Boot state is exactly the restored state (not the default).
        assertThat(controller.publicState.value.state).isEqualTo(midState)

        // Submitting the *current* expected action advances correctly, proving
        // the reducer sees the restored state — not a fresh one.
        controller.submit(RrAction.Announce(PlayerId("p3")))
        val finalState = controller.publicState.value.state
        assertThat(finalState.phase).isInstanceOf(RrPhase.Finished::class)
        assertThat(finalState.announcedBy).isEqualTo(
            listOf(PlayerId("p1"), PlayerId("p2"), PlayerId("p3")),
        )
        controller.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun current_state_observes_the_committed_reduction_before_projection_flows_dispatch() = runTest {
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = PassAndPlaySessionController(
            RoundRobinAnnounceGame(),
            fakeConfig(),
            reducerCtx(),
            controllerScope,
        )

        controller.submit(RrAction.Announce(PlayerId("p1")))

        assertThat(controller.currentState().announcedBy).isEqualTo(listOf(PlayerId("p1")))
        assertThat(controller.hostState.value.state.announcedBy).isEqualTo(emptyList())

        runCurrent()
        assertThat(controller.hostState.value.state.announcedBy).isEqualTo(listOf(PlayerId("p1")))
        controller.close()
    }
}

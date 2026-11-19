package com.parlor.session.passandplay

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import com.parlor.engine.testing.fakes.RrAction
import com.parlor.engine.testing.fakes.RrEvent
import com.parlor.engine.testing.fakes.RrState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerEventCancellationTest {
    @Test
    fun cancelling_a_queued_submit_preserves_commit_order_under_real_flow_backpressure() = runTest {
        val players = List(3) { seat -> Player(PlayerId("p$seat"), "Player $seat", seat) }
        val definition = BurstEventDefinition(players.first().id)
        val controller = PassAndPlaySessionController(
            definition,
            SessionConfig(SessionId("event-order"), CaseId("none"), ModeId("round-robin"), players, 42L),
            DefaultReducerContext(FakeClock(Instant.fromEpochSeconds(1_700_000_000)), RandomSource.seeded(42)),
            backgroundScope,
        )
        val releaseCollector = CompletableDeferred<Unit>()
        val emitted = mutableListOf<RrEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.events.collect { event ->
                emitted += event
                releaseCollector.await()
            }
        }
        val first = backgroundScope.launch { controller.submit(RrAction.Announce(players[0].id)) }
        runCurrent()
        assertEquals<List<RrEvent>>(listOf(RrEvent.PlayerAnnounced(players[0].id)), emitted)
        val cancelled = backgroundScope.launch { controller.submit(RrAction.Announce(players[1].id)) }
        runCurrent()
        cancelled.cancelAndJoin()
        val last = backgroundScope.launch { controller.submit(RrAction.Announce(players[2].id)) }
        runCurrent()
        // Event backpressure must not hold the reducer mutex or undo a commit.
        assertEquals(players.map { it.id }, controller.currentState().announcedBy)
        assertEquals(3, definition.reductions)

        releaseCollector.complete(Unit)
        runCurrent()
        assertEquals<List<RrEvent>>(
            List(BURST_SIZE) { RrEvent.PlayerAnnounced(players[0].id) } +
                listOf(RrEvent.PlayerAnnounced(players[2].id), RrEvent.SessionEnded),
            emitted,
        )
        assertTrue(first.isCompleted && last.isCompleted && cancelled.isCancelled)
        controller.close()
        assertEquals(Result.Failure(SubmitError.SessionClosed), controller.submit(RrAction.Announce(players[0].id)))
    }
}

private const val BURST_SIZE = 100

private class BurstEventDefinition(private val firstPlayer: PlayerId) :
    GameDefinition<RrState, RrAction, RrEvent> by RoundRobinAnnounceGame() {
    var reductions = 0
        private set

    override fun reducer(): GameReducer<RrState, RrAction, RrEvent> = object : GameReducer<RrState, RrAction, RrEvent> {
        override fun reduce(state: RrState, action: RrAction, ctx: ReducerContext): Reduction<RrState, RrEvent> {
            reductions++
            val reduction = RoundRobinAnnounceGame().reducer().reduce(state, action, ctx)
            return if (action == RrAction.Announce(firstPlayer)) {
                reduction.copy(events = List(BURST_SIZE) { RrEvent.PlayerAnnounced(firstPlayer) })
            } else reduction
        }
    }
}

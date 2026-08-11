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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class PassAndPlaySessionControllerCloseRaceTest {

    @Test
    fun close_waits_for_the_active_commit_and_rejects_every_later_submit() = runBlocking {
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { testDispatcher ->
            val reducerEntered = CountDownLatch(1)
            val releaseReducer = CountDownLatch(1)
            val definition = BlockingDefinition(reducerEntered, releaseReducer)
            val controllerScope = CoroutineScope(testDispatcher + SupervisorJob())
            val controller = PassAndPlaySessionController(
                definition = definition,
                config = config(),
                reducerContext = DefaultReducerContext(
                    clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
                    random = RandomSource.seeded(42L),
                ),
                scope = controllerScope,
            )

            val activeSubmit = async(testDispatcher) {
                controller.submit(RrAction.Announce(PlayerId("p1")))
            }
            assertTrue(reducerEntered.await(5, TimeUnit.SECONDS), "Reducer did not enter")

            val close = async(start = CoroutineStart.UNDISPATCHED) { controller.close() }
            assertFalse(close.isCompleted, "close returned while a canonical commit was still active")

            releaseReducer.countDown()
            assertIs<Result.Success<*>>(activeSubmit.await())
            close.await()

            val afterClose = controller.submit(RrAction.Announce(PlayerId("p2")))
            assertEquals(Result.Failure(SubmitError.SessionClosed), afterClose)
            assertEquals(listOf(PlayerId("p1")), controller.currentState().announcedBy)
            controllerScope.cancel()
        }
    }

    private fun config() = SessionConfig(
        sessionId = SessionId("close-race"),
        caseId = CaseId("none"),
        modeId = ModeId("round-robin"),
        players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
        ),
        randomSeed = 42L,
    )
}

private class BlockingDefinition(
    reducerEntered: CountDownLatch,
    releaseReducer: CountDownLatch,
) : GameDefinition<RrState, RrAction, RrEvent> by RoundRobinAnnounceGame() {
    private val blockingReducer = object : GameReducer<RrState, RrAction, RrEvent> {
        override fun reduce(
            state: RrState,
            action: RrAction,
            ctx: ReducerContext,
        ): Reduction<RrState, RrEvent> {
            reducerEntered.countDown()
            check(releaseReducer.await(5, TimeUnit.SECONDS)) { "Reducer release timed out" }
            return RoundRobinAnnounceGame().reducer().reduce(state, action, ctx)
        }
    }

    override fun reducer(): GameReducer<RrState, RrAction, RrEvent> = blockingReducer
}

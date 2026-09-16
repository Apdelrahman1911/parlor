package com.parlor.games.lastlight.ui.flow.passandplay

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertTrue
import kotlin.time.Instant

internal class LastLightLocalFixture(
    scope: CoroutineScope,
    val store: SnapshotStore = InMemorySnapshotStore(),
    val config: SessionConfig = createLocalLastLightSessionConfig(
        players = lastLightLocalPlayers(listOf("Alice", "Bob", "Charlie", "Dalia", "Eve", "Faris")),
        randomSeed = 812L,
        sessionIdGenerator = { "local-match" },
    ),
    restoredState: LastLightState? = null,
    beforeSubmit: suspend () -> Unit = {},
    visibilityReader: (() -> LastLightProcessVisibility)? = null,
) {
    val definition = LastLightDefinition()
    val clock = FakeClock(Instant.fromEpochMilliseconds(1_000))
    val raw = PassAndPlaySessionController(
        definition = definition,
        config = config,
        reducerContext = DefaultReducerContext(clock, RandomSource.seeded(config.randomSeed)),
        scope = scope,
        restoredState = restoredState,
    )
    var submissions: Int = 0
        private set
    private val counted = object : SessionController<LastLightState, LastLightAction, LastLightEvent> by raw {
        override suspend fun submit(action: LastLightAction): Result<SubmissionReceipt, SubmitError> {
            submissions++
            beforeSubmit()
            return raw.submit(action)
        }
    }
    val runtime = LastLightLocalSession(
        config = config,
        controller = counted,
        definition = definition,
        store = store,
        clock = clock,
        scope = scope,
        writeContext = EmptyCoroutineContext,
        visibilityReader = visibilityReader,
    ).also { it.setVisibility(LastLightProcessVisibility(true, 0L)) }

    fun submitNextLegalAction() {
        when (raw.currentState().phase) {
            GamePhase.PLAYING -> {
                assertTrue(runtime.takeDevice())
                val view = runtime.presentation.value.game
                if (view.availableActions.canChallenge) {
                    assertTrue(runtime.challenge())
                } else {
                    assertTrue(runtime.play(listOf(view.yourHand.first().id)))
                }
            }
            GamePhase.ROUND_ENDED -> assertTrue(runtime.nextRound())
            GamePhase.FINISHED -> error("Fixture match already finished")
        }
    }
}

internal class FailingLocalSnapshotStore : SnapshotStore {
    private val backing = InMemorySnapshotStore()
    var failSave: Boolean = false
    var failDelete: Boolean = false
    var deleteCalls: Int = 0
        private set

    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> =
        if (failSave) Result.Failure(DataError.DiskFull) else backing.save(snapshot)

    override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> = backing.load(sessionId)

    override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> {
        deleteCalls++
        return if (failDelete) Result.Failure(DataError.IoError("fixture_delete")) else backing.delete(sessionId)
    }

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = backing.listUnfinished()
}

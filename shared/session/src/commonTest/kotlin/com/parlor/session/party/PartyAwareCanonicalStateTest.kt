package com.parlor.session.party

import assertk.assertThat
import assertk.assertions.containsExactly
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.phase.GamePhase
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.ViewerContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PartyAwareCanonicalStateTest {
    @Test
    fun readiness_uses_directly_committed_state_when_public_projection_is_stale() = runTest {
        val playerId = PlayerId("p1")
        val committed = TestState(acknowledged = false)
        val stalePublic = TestState(acknowledged = true)
        val delegate = StaleProjectionController(committed, stalePublic)
        val gate = object : PartyReadinessGate<TestState, TestAction> {
            override fun pendingAcks(
                state: TestState,
                hostAction: TestAction,
            ): List<PendingAck<TestAction>> =
                if (hostAction == TestAction.Advance && !state.acknowledged) {
                    listOf(PendingAck(playerId, TestAction.Acknowledge))
                } else {
                    emptyList()
                }
        }
        val session = PartyAwareSession(delegate, PlayMode.PassAndPlay, gate)

        session.submit(TestAction.Advance)

        assertThat(delegate.submitted).containsExactly(
            TestAction.Acknowledge,
            TestAction.Advance,
        )
    }
}

private data class TestState(
    val acknowledged: Boolean,
) : GameState {
    override val phase: GamePhase = object : GamePhase { override val id = "test" }
    override val players: List<Player> = listOf(Player(PlayerId("p1"), "Player", 0))
}

private enum class TestAction : GameAction { Acknowledge, Advance }
private data object TestEvent : GameEvent

private class StaleProjectionController(
    committed: TestState,
    stalePublic: TestState,
) : SessionController<TestState, TestAction, TestEvent> {
    private val committedState = MutableStateFlow(committed)
    override val canonicalState: StateFlow<TestState> = committedState
    override val publicState: StateFlow<PublicProjection<TestState>> =
        MutableStateFlow(PublicProjection(stalePublic))
    override val hostState: StateFlow<HostProjection<TestState>> =
        MutableStateFlow(HostProjection(committed))
    override val events: SharedFlow<TestEvent> = MutableSharedFlow()
    override val activeViewer: StateFlow<ViewerContext> = MutableStateFlow(ViewerContext.Public)
    val submitted = mutableListOf<TestAction>()

    override fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<TestState>> =
        MutableStateFlow(PrivateProjection(committedState.value, playerId))

    override suspend fun submit(
        action: TestAction,
    ): Result<SubmissionReceipt, SubmitError> {
        submitted += action
        if (action == TestAction.Acknowledge) {
            committedState.value = committedState.value.copy(acknowledged = true)
        }
        return Result.Success(SubmissionReceipt(stateChanged = true))
    }

    override suspend fun setActiveViewer(viewer: ViewerContext) = Unit
    override suspend fun close() = Unit
}

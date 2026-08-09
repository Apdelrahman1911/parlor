package com.parlor.session.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.testing.fakes.RrAction
import com.parlor.engine.testing.fakes.RrEvent
import com.parlor.engine.testing.fakes.RrPhase
import com.parlor.engine.testing.fakes.RrState
import com.parlor.engine.state.Player
import com.parlor.session.SubmissionReceipt
import com.parlor.session.ViewerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ShadowSessionControllerTest {

    private val selfId = PlayerId("self")
    private val otherId = PlayerId("other")
    private val players = listOf(
        Player(selfId, "Alice", seat = 0),
        Player(otherId, "Bob", seat = 1),
    )
    private val initialState = RrState(
        phase = RrPhase.Announcing(currentSeat = 0),
        players = players,
        announcedBy = emptyList(),
    )

    @Test
    fun installPlayerSnapshotUpdatesBothViewsFromOneValidatedCall() {
        val controller = controller()
        val nextState = initialState.copy(
            phase = RrPhase.Announcing(currentSeat = 1),
            announcedBy = listOf(selfId),
        )

        controller.installPlayerSnapshot(
            publicProjection = PublicProjection(nextState),
            playerProjection = PrivateProjection(nextState, selfId),
        )

        assertThat(controller.publicState.value).isEqualTo(PublicProjection(nextState))
        assertThat(controller.privateStateFor(selfId).value)
            .isEqualTo(PrivateProjection(nextState, selfId))
    }

    @Test
    fun wrongPlayerSnapshotIsRejectedBeforeEitherViewChanges() {
        val controller = controller()
        val previousPublic = controller.publicState.value
        val previousPrivate = controller.privateStateFor(selfId).value
        val replacement = initialState.copy(announcedBy = listOf(otherId))

        assertFailsWith<IllegalArgumentException> {
            controller.installPlayerSnapshot(
                publicProjection = PublicProjection(replacement),
                playerProjection = PrivateProjection(replacement, otherId),
            )
        }

        assertThat(controller.publicState.value).isEqualTo(previousPublic)
        assertThat(controller.privateStateFor(selfId).value).isEqualTo(previousPrivate)
    }

    @Test
    fun peerCannotAssumeHostViewerContext() = runTest {
        val controller = controller()

        assertFailsWith<IllegalArgumentException> {
            controller.setActiveViewer(ViewerContext.Host)
        }

        assertThat(controller.activeViewer.value).isEqualTo(ViewerContext.Player(selfId))
    }

    private fun controller() = ShadowSessionController<RrState, RrAction, RrEvent>(
        selfPlayerId = selfId,
        sendActionToHost = { Result.Success(SubmissionReceipt(stateChanged = false)) },
        initialPublic = PublicProjection(initialState),
        initialPrivate = PrivateProjection(initialState, selfId),
    )
}

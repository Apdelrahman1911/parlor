package com.parlor.session.party

import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.ViewerContext
import com.parlor.session.isLocal
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [SessionController] decorator that closes the local-mode readiness
 * gap once, at the session boundary, instead of inside every UI button.
 *
 * In [PlayMode.MultiDevice] this is a transparent pass-through — peers
 * send their own ack actions and the host's gated advance waits on
 * them as designed.
 *
 * In [PlayMode.Solo] / [PlayMode.PassAndPlay] (single device, no peers
 * to send acks) it consults the game's [PartyReadinessGate]: if the
 * action about to be submitted is a gated host advance, the wrapper
 * first submits each pending per-player ack the gate reports, then the
 * gated action itself. The UI is unaware of any of this — `Continue`
 * buttons just `submit(WhodunitAction.AdvanceFromIntro)` and it works
 * in every mode.
 *
 * Wraps any concrete controller — including pass-and-play and peer/shadow
 * implementations — that satisfies [SessionController].
 */
class PartyAwareSession<S : GameState, A : GameAction, E : GameEvent>(
    private val delegate: SessionController<S, A, E>,
    private val playMode: PlayMode,
    private val gate: PartyReadinessGate<S, A>,
) : SessionController<S, A, E> {

    override val publicState: StateFlow<PublicProjection<S>> get() = delegate.publicState
    override val hostState: StateFlow<HostProjection<S>>? get() = delegate.hostState
    override val canonicalState: StateFlow<S>? get() = delegate.canonicalState
    override val events: SharedFlow<E> get() = delegate.events
    override val activeViewer: StateFlow<ViewerContext> get() = delegate.activeViewer

    override fun privateStateFor(playerId: com.parlor.core.ids.PlayerId): StateFlow<PrivateProjection<S>> =
        delegate.privateStateFor(playerId)

    override suspend fun submit(action: A): Result<SubmissionReceipt, SubmitError> {
        var stateChanged = false
        var awaitingAuthority = false
        if (playMode.isLocal) {
            val currentState = requireNotNull(delegate.canonicalState) {
                "A local party session requires an authoritative controller"
            }.value
            val pending = gate.pendingAcks(currentState, action)
            // Auto-issue every still-missing per-player ack so the gated
            // action that follows passes the reducer's readiness check.
            for (pendingAck in pending) {
                when (val result = delegate.submit(pendingAck.ackAction)) {
                    is Result.Failure -> return result
                    is Result.Success -> {
                        stateChanged = stateChanged || result.data.stateChanged
                        awaitingAuthority = awaitingAuthority || result.data.awaitingAuthority
                    }
                }
            }
        }
        return when (val result = delegate.submit(action)) {
            is Result.Failure -> result
            is Result.Success -> Result.Success(
                SubmissionReceipt(
                    stateChanged = stateChanged || result.data.stateChanged,
                    awaitingAuthority = awaitingAuthority || result.data.awaitingAuthority,
                ),
            )
        }
    }

    override suspend fun setActiveViewer(viewer: ViewerContext) = delegate.setActiveViewer(viewer)
    override suspend fun close() = delegate.close()
}

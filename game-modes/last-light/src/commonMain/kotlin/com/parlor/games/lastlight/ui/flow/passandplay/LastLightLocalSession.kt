package com.parlor.games.lastlight.ui.flow.passandplay

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.session.SessionController
import com.parlor.session.ViewerContext
import com.parlor.storage.snapshot.SerializedSnapshotWriter
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.storage.snapshot.SnapshotWriteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

internal enum class LastLightLocalIssue { ActionRejected, SaveFailed }

/** This is the entire rendering boundary: one recipient's view, never canonical state. */
internal data class LastLightLocalPresentation(
    val game: GameView,
    val handoffPlayerName: String?,
    val pendingAction: PendingAction?,
    val canSendAction: Boolean,
    val exitConfirmationOpen: Boolean,
    val exitInFlight: Boolean,
    val issue: LastLightLocalIssue?,
)

private data class LocalTurn(val round: Int, val playerId: PlayerId, val playSequence: Long)

private class LocalReservation(
    val kind: PendingAction,
    val state: LastLightState,
    val epoch: Long,
)

private data class LocalInput(
    val visibility: LastLightProcessVisibility = LastLightProcessVisibility(false, 0L),
    val readyTurn: LocalTurn? = null,
    val pending: LocalReservation? = null,
    val exitConfirmation: Boolean = false,
    val exiting: Boolean = false,
    val closed: Boolean = false,
    val retrying: Boolean = false,
    val issue: LastLightLocalIssue? = null,
) {
    val available: Boolean
        get() = visibility.isForeground && pending == null && !exitConfirmation && !exiting && !closed
}

/**
 * Owns local input, private handoff and persistence above the shared session controller.
 * Reservations happen in the callback, before launch; the serialized worker rechecks
 * foreground, interruption epoch and canonical state before making any mutation.
 */
internal class LastLightLocalSession(
    val config: SessionConfig,
    private val controller: SessionController<LastLightState, LastLightAction, LastLightEvent>,
    definition: LastLightDefinition,
    store: SnapshotStore,
    clock: Clock,
    private val scope: CoroutineScope,
    writeContext: CoroutineContext = Dispatchers.Default,
    private val visibilityReader: (() -> LastLightProcessVisibility)? = null,
) {
    private val canonical = requireNotNull(controller.canonicalState)
    private val policy = definition.projectionPolicy()
    private val input = MutableStateFlow(LocalInput())
    private val operations = Mutex()
    private val writer = SerializedSnapshotWriter(
        store = store,
        sessionId = config.sessionId,
        snapshotFor = { state: LastLightState ->
            GameSnapshot(
                sessionId = config.sessionId,
                gameId = LastLightIds.GameId,
                engineVersion = LastLightSnapshotRecovery.VERSION,
                createdAt = clock.now(),
                phaseId = state.phase.id,
                payload = definition.snapshotCodec().encode(state),
                metadata = mapOf(
                    LastLightSnapshotRecovery.PLAY_MODE_KEY to LastLightSnapshotRecovery.PASS_AND_PLAY_MODE,
                ),
            )
        },
        isCompleted = { state -> state.phase == GamePhase.FINISHED },
        writeContext = writeContext,
    )
    private val mutablePresentation = MutableStateFlow(project())
    val presentation: StateFlow<LastLightLocalPresentation> = mutablePresentation.asStateFlow()
    val persistenceStatus: StateFlow<SnapshotWriteStatus> = writer.status

    init {
        scope.launch {
            canonical.collect {
                refresh()
                // Sample inside the collector so a delayed save cannot replace a newer flush.
                writer.persist(canonical.value)
            }
        }
    }

    fun setVisibility(visibility: LastLightProcessVisibility) {
        val actual = visibilityReader?.invoke() ?: visibility
        input.update { current ->
            val interrupted = !actual.isForeground || actual.concealmentEpoch != current.visibility.concealmentEpoch
            current.copy(visibility = actual, readyTurn = current.readyTurn.takeUnless { interrupted })
        }
        refresh()
        if (!actual.isForeground) scope.launch { controller.setActiveViewer(ViewerContext.Public) }
    }

    /** The next viewer must take the device, and then separately reveal the concealed hand. */
    fun takeDevice(): Boolean {
        refreshVisibility()
        val current = input.value
        val state = canonical.value
        val turn = currentTurn(state) ?: return false
        if (state.public.disconnectedPlayers.isNotEmpty() || state.public.endedEarly) return false
        if (!scope.isActive || !current.available) return false
        if (!input.compareAndSet(current, current.copy(readyTurn = turn, issue = null))) return false
        refresh()
        scope.launch {
            val latest = input.value
            val viewer = if (latest.available && latest.readyTurn == turn && currentTurn() == turn) {
                ViewerContext.Player(turn.playerId)
            } else {
                ViewerContext.Public
            }
            controller.setActiveViewer(viewer)
        }
        return true
    }

    fun play(cardIds: List<CardId>): Boolean {
        val turn = currentTurn() ?: return false
        return submit(PendingAction.PLAY_CARDS, LastLightAction.PlayCards(turn.playerId, cardIds.toList()))
    }

    fun challenge(): Boolean {
        val turn = currentTurn() ?: return false
        return submit(PendingAction.CHALLENGE, LastLightAction.Challenge(turn.playerId))
    }

    fun nextRound(): Boolean = submit(PendingAction.NEXT_ROUND, LastLightAction.NextRound)

    fun requestExit() {
        input.update { if (it.closed || it.exiting) it else it.copy(exitConfirmation = true, readyTurn = null) }
        refresh()
        scope.launch { controller.setActiveViewer(ViewerContext.Public) }
    }

    fun stay() {
        input.update { if (it.closed || it.exiting) it else it.copy(exitConfirmation = false, readyTurn = null) }
        refresh()
    }

    fun saveAndExit(onSaved: () -> Unit): Boolean = finish(discard = false, onFinished = onSaved)

    /** Returning from the winner starts setup; a future match gets a new writer, ID and seed. */
    fun returnToSetup(onDiscarded: () -> Unit): Boolean {
        if (canonical.value.phase != GamePhase.FINISHED) return false
        return finish(discard = true, onFinished = onDiscarded)
    }

    fun retrySave() {
        val current = input.value
        if (!scope.isActive || current.exiting || current.closed || current.retrying) return
        if (!input.compareAndSet(current, current.copy(retrying = true))) return
        scope.launch {
            try {
                if (writer.persist(canonical.value) is Result.Success) {
                    input.update { it.copy(issue = null) }
                }
            } finally {
                input.update { it.copy(retrying = false) }
                refresh()
            }
        }
    }

    /** Disposal closes the authority before its final flush, even if the screen scope was cancelled. */
    suspend fun dispose() {
        input.update { it.copy(closed = true, readyTurn = null) }
        refresh()
        operations.withLock {
            controller.setActiveViewer(ViewerContext.Public)
            controller.close()
            writer.persist(canonical.value)
        }
    }

    private fun submit(kind: PendingAction, action: LastLightAction): Boolean {
        refreshVisibility()
        val current = input.value
        val state = canonical.value
        if (!scope.isActive || !current.available || !canSubmit(kind, state, current)) return false
        val reservation = LocalReservation(kind, state, current.visibility.concealmentEpoch)
        if (!input.compareAndSet(current, current.copy(pending = reservation, readyTurn = null, issue = null))) return false
        // Remove private content synchronously, before the reducer can advance the viewer.
        refresh()
        scope.launch {
            try {
                operations.withLock {
                    if (!reservationIsCurrent(reservation)) return@withLock
                    controller.setActiveViewer(ViewerContext.Public)
                    val result = controller.submit(action)
                    if (result !is Result.Success || !result.data.stateChanged) {
                        input.update { it.copy(issue = LastLightLocalIssue.ActionRejected) }
                    }
                }
            } finally {
                input.update { if (it.pending === reservation) it.copy(pending = null) else it }
                refresh()
            }
        }
        return true
    }

    private fun canSubmit(kind: PendingAction, state: LastLightState, current: LocalInput): Boolean {
        if (state.public.disconnectedPlayers.isNotEmpty() || state.public.endedEarly) return false
        return when (kind) {
            PendingAction.PLAY_CARDS, PendingAction.CHALLENGE ->
                state.phase == GamePhase.PLAYING && current.readyTurn != null && current.readyTurn == currentTurn(state)
            PendingAction.NEXT_ROUND -> state.phase == GamePhase.ROUND_ENDED
            PendingAction.RETURN_TO_LOBBY -> false
        }
    }

    private fun reservationIsCurrent(reservation: LocalReservation): Boolean {
        refreshVisibility()
        val latest = input.value
        return latest.pending === reservation && latest.visibility.isForeground &&
            latest.visibility.concealmentEpoch == reservation.epoch && !latest.exitConfirmation &&
            !latest.exiting && !latest.closed && canonical.value == reservation.state
    }

    private fun finish(discard: Boolean, onFinished: () -> Unit): Boolean {
        refreshVisibility()
        val current = input.value
        if (!scope.isActive || !current.visibility.isForeground || current.exiting || current.closed) return false
        if (discard && !current.available) return false
        if (!discard && !current.exitConfirmation) return false
        if (!input.compareAndSet(current, current.copy(exiting = true, readyTurn = null, issue = null))) return false
        refresh()
        scope.launch {
            try {
                operations.withLock {
                    refreshVisibility()
                    val latest = input.value
                    if (!latest.visibility.isForeground || latest.closed ||
                        latest.visibility.concealmentEpoch != current.visibility.concealmentEpoch
                    ) return@withLock
                    controller.setActiveViewer(ViewerContext.Public)
                    val result = if (discard) writer.discard() else writer.persist(canonical.value)
                    if (result is Result.Success) {
                        controller.close()
                        input.update { it.copy(closed = true) }
                        onFinished()
                    } else {
                        input.update { it.copy(issue = LastLightLocalIssue.SaveFailed) }
                    }
                }
            } finally {
                input.update { it.copy(exiting = false) }
                refresh()
            }
        }
        return true
    }

    private fun currentTurn(state: LastLightState = canonical.value): LocalTurn? =
        if (state.phase == GamePhase.PLAYING) {
            state.public.turnPlayerId?.let {
                LocalTurn(state.public.roundNumber, PlayerId(it), state.public.acceptedPlaySequence)
            }
        } else {
            null
        }

    private fun project(): LastLightLocalPresentation {
        val current = input.value
        val state = canonical.value
        val turn = currentTurn(state)
        val viewer = turn?.playerId?.takeIf {
            current.available && current.readyTurn == turn && state.public.disconnectedPlayers.isEmpty()
        }
        val recipientState = if (viewer == null) policy.toPublic(state).state else policy.toPlayer(state, viewer).state
        val game = LastLightProjectionPolicy.viewFor(recipientState, viewer)
        return LastLightLocalPresentation(
            game = game,
            handoffPlayerName = turn?.takeIf { viewer == null }?.let { active ->
                state.players.firstOrNull { it.id == active.playerId }?.displayName
            },
            pendingAction = if (current.exiting) PendingAction.RETURN_TO_LOBBY else current.pending?.kind,
            canSendAction = scope.isActive && current.available && state.public.disconnectedPlayers.isEmpty() &&
                (turn == null || viewer != null),
            exitConfirmationOpen = current.exitConfirmation,
            exitInFlight = current.exiting,
            issue = current.issue,
        )
    }

    private fun refresh() {
        mutablePresentation.value = project()
    }

    private fun refreshVisibility() {
        visibilityReader?.invoke()?.let(::setVisibility)
    }
}

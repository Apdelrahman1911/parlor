package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.engine.session.SubmitError
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.peer_command_invalid
import com.parlor.games.lastlight.resources.peer_command_session_error
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.games.lastlight.ui.flow.common.LastLightSessionTable
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.MutableStateFlow

/** A seated projection and callbacks are the complete multiplayer rendering contract. */
@Composable
internal fun LastLightMultiplayerTable(
    sessionId: String,
    state: LastLightState,
    selfPlayerId: PlayerId,
    isHost: Boolean,
    actions: LastLightActionSubmission,
    connected: Boolean,
    onReturnToLobby: () -> Unit,
    onRequestLeave: () -> Unit,
    modifier: Modifier = Modifier,
    commandsAllowed: Boolean = true,
    recoveryEpoch: Long = 0L,
    recoveryEpochReader: () -> Long = { recoveryEpoch },
) {
    val visibility = LocalLastLightProcessVisibility.current
    val currentVisibility by rememberUpdatedState(visibility)
    val visibilityReader = LocalLastLightVisibilityReader.current
    val surfaceActive = remember(sessionId, actions) { MutableStateFlow(true) }
    DisposableEffect(surfaceActive) {
        onDispose { surfaceActive.value = false }
    }
    val submissionStillAllowed = {
        val current = visibilityReader?.invoke() ?: currentVisibility
        surfaceActive.value && current.isForeground && current.concealmentEpoch == visibility.concealmentEpoch &&
            recoveryEpochReader() == recoveryEpoch
    }
    val pending by actions.pendingAction.collectAsState()
    val failure by actions.failure.collectAsState()
    val toastState = LocalParlorToastState.current
    val invalidCopy = stringResource(Res.string.peer_command_invalid)
    val sessionCopy = stringResource(Res.string.peer_command_session_error)
    LaunchedEffect(failure, invalidCopy, sessionCopy) {
        failure?.let { failed ->
            val message = when (failed.error) {
                SubmitError.SessionClosed,
                SubmitError.SessionSuspended,
                SubmitError.CommandPending -> sessionCopy
                else -> invalidCopy
            }
            toastState.show(message, ParlorToastSeverity.Warning)
            actions.acknowledgeFailure(failed)
        }
    }
    val view = LastLightProjectionPolicy.viewFor(state, selfPlayerId)
    val enabled = connected && commandsAllowed && visibility.isForeground && pending == null
    LastLightSessionTable(
        sessionId = sessionId,
        view = view,
        isHost = isHost,
        canSendAction = enabled,
        pendingAction = pending,
        privateContentVisible = connected && visibility.isForeground,
        privacyEpoch = combinedLastLightPrivacyEpoch(visibility.concealmentEpoch, recoveryEpoch),
        connected = connected,
        canAdvanceRound = isHost && state.phase == GamePhase.ROUND_ENDED,
        canReturnToLobby = isHost && state.phase == GamePhase.FINISHED,
        onPlay = { cardIds ->
            actions.trySubmit(
                PendingAction.PLAY_CARDS,
                LastLightAction.PlayCards(selfPlayerId, cardIds),
                enabled && view.availableActions.canPlay,
                submissionStillAllowed,
            )
        },
        onChallenge = {
            actions.trySubmit(
                PendingAction.CHALLENGE,
                LastLightAction.Challenge(selfPlayerId),
                enabled && view.availableActions.canChallenge,
                submissionStillAllowed,
            )
        },
        onNextRound = {
            actions.trySubmit(
                PendingAction.NEXT_ROUND,
                LastLightAction.NextRound,
                enabled && isHost && state.phase == GamePhase.ROUND_ENDED,
                submissionStillAllowed,
            )
        },
        onReturnToLobby = {
            if (enabled && submissionStillAllowed() && isHost && state.phase == GamePhase.FINISHED) onReturnToLobby()
        },
        onRequestLeave = onRequestLeave,
        modifier = modifier,
    )
}

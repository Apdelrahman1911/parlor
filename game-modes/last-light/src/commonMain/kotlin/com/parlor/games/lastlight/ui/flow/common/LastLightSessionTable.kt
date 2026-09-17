package com.parlor.games.lastlight.ui.flow.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.games.lastlight.ui.awake.LastLightScreenAwakeEffect
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackEffect
import com.parlor.games.lastlight.ui.game.GameplayScreen
import com.parlor.games.lastlight.ui.game.TableBackdrop
import com.parlor.games.lastlight.ui.theme.LastLightTheme

/** Common recipient-only table for local, host and peer controllers. */
@Composable
internal fun LastLightSessionTable(
    sessionId: String,
    view: GameView,
    isHost: Boolean,
    canSendAction: Boolean,
    pendingAction: PendingAction?,
    privateContentVisible: Boolean,
    canAdvanceRound: Boolean,
    canReturnToLobby: Boolean,
    onPlay: (List<CardId>) -> Unit,
    onChallenge: () -> Unit,
    onNextRound: () -> Unit,
    onReturnToLobby: () -> Unit,
    onRequestLeave: () -> Unit,
    privacyEpoch: Long = 0L,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
    celebrateAnyWinner: Boolean = false,
    cover: (@Composable () -> Unit)? = null,
) {
    var soundEnabled by rememberSaveable(sessionId) { mutableStateOf(true) }
    var hapticsEnabled by rememberSaveable(sessionId) { mutableStateOf(true) }
    var keepScreenAwake by rememberSaveable(sessionId) { mutableStateOf(false) }
    var optionsOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    val visibility = LocalLastLightProcessVisibility.current
    val visibilityReader = LocalLastLightVisibilityReader.current
    LastLightScreenAwakeEffect(
        sessionId,
        enabled = keepScreenAwake && visibility.isForeground && connected && view.phase == GamePhase.PLAYING,
    )
    val epochKey = privacyEpoch to visibility.concealmentEpoch
    var observedEpochKey by remember(sessionId) { mutableStateOf(epochKey) }
    var interruptionEpoch by remember(sessionId) { mutableLongStateOf(0L) }
    // Either source can interrupt privacy independently; maxOf would miss a change
    // in the smaller counter. Mask in this composition, then commit the observed pair.
    val effectiveEpoch = interruptionEpoch + if (observedEpochKey == epochKey) 0L else 1L
    SideEffect {
        observedEpochKey = epochKey
        interruptionEpoch = effectiveEpoch
    }

    // This effect consumes accepted public transitions. It stays mounted across local
    // handoff and is deliberately outside the per-viewer private presentation key.
    LastLightFeedbackEffect(
        sessionKey = sessionId,
        game = view,
        connected = connected,
        foreground = visibility.isForeground,
        soundEnabled = soundEnabled,
        hapticsEnabled = hapticsEnabled,
        interruptionEpoch = effectiveEpoch,
        celebrateAnyWinner = celebrateAnyWinner,
    )
    LastLightTheme {
        TableBackdrop(modifier = modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().parlorSafeContentPadding(0.dp)) {
                    LastLightTableToolbar(
                        onRequestLeave = onRequestLeave,
                        onOptions = { optionsOpen = true },
                        enabled = visibility.isForeground,
                    )
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (cover != null) {
                            cover()
                        } else {
                            key(sessionId, view.viewerId) {
                                GameplayScreen(
                                    view = view,
                                    isHost = isHost,
                                    canSendAction = canSendAction && !optionsOpen,
                                    pendingAction = pendingAction,
                                    privateContentVisible = privateContentVisible && visibility.isForeground && !optionsOpen,
                                    canAdvanceRound = canAdvanceRound,
                                    canReturnToLobby = canReturnToLobby,
                                    onPlay = onPlay,
                                    onChallenge = onChallenge,
                                    onNextRound = onNextRound,
                                    onReturnToLobby = onReturnToLobby,
                                    privacyEpoch = effectiveEpoch,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                if (optionsOpen) {
                    LastLightTableOptions(
                        soundEnabled = soundEnabled,
                        hapticsEnabled = hapticsEnabled,
                        keepScreenAwake = keepScreenAwake,
                        onSoundChanged = { soundEnabled = it },
                        onHapticsChanged = { hapticsEnabled = it },
                        onKeepScreenAwakeChanged = {
                            val current = visibilityReader?.invoke() ?: visibility
                            if (current.isForeground && current.concealmentEpoch == visibility.concealmentEpoch) {
                                keepScreenAwake = it
                            }
                        },
                        onClose = { optionsOpen = false },
                        availableWidth = maxWidth,
                        availableHeight = maxHeight,
                    )
                }
            }
        }
    }
}

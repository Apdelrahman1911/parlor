package com.parlor.games.lastlight.ui.flow.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.local_options_close
import com.parlor.games.lastlight.resources.local_options_haptics
import com.parlor.games.lastlight.resources.local_options_sound
import com.parlor.games.lastlight.resources.local_options_title
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.games.lastlight.ui.feedback.LastLightFeedbackEffect
import com.parlor.games.lastlight.ui.game.GameplayScreen
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.LastLightTheme
import org.jetbrains.compose.resources.stringResource

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
    privacyEpoch: Long = 0L,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
    celebrateAnyWinner: Boolean = false,
    cover: (@Composable () -> Unit)? = null,
) {
    var soundEnabled by rememberSaveable(sessionId) { mutableStateOf(true) }
    var hapticsEnabled by rememberSaveable(sessionId) { mutableStateOf(true) }
    var optionsOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    val visibility = LocalLastLightProcessVisibility.current
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
        Surface(modifier = modifier.fillMaxSize(), color = LastLightColors.Ink) {
            Column(modifier = Modifier.fillMaxSize().parlorSafeContentPadding(0.dp)) {
                TextButton(
                    onClick = { optionsOpen = true },
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.local_options_title))
                }
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
                FeedbackOptionsDialog(
                    soundEnabled = soundEnabled,
                    hapticsEnabled = hapticsEnabled,
                    onSoundChanged = { soundEnabled = it },
                    onHapticsChanged = { hapticsEnabled = it },
                    onClose = { optionsOpen = false },
                )
            }
        }
    }
}

@Composable
private fun FeedbackOptionsDialog(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    onSoundChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.local_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackToggle(stringResource(Res.string.local_options_sound), soundEnabled, onSoundChanged)
                FeedbackToggle(stringResource(Res.string.local_options_haptics), hapticsEnabled, onHapticsChanged)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.local_options_close))
            }
        },
    )
}

@Composable
private fun FeedbackToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

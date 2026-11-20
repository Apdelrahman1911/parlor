package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.engine.state.Player
import com.parlor.games.mafia.ui.screens.handoff.MafiaRoleRevealGateScreen

@Composable
internal fun MafiaPrivateAcknowledgement(
    player: Player,
    onAcknowledged: () -> Unit,
    content: @Composable (onHidden: () -> Unit) -> Unit,
) {
    var activeReveal by remember(player.id) { mutableStateOf<Any?>(null) }
    DisposableEffect(player.id) {
        onDispose { activeReveal = null }
    }
    val reveal = activeReveal
    InPlaceBackHandler(reveal != null) { activeReveal = null }
    if (reveal != null) {
        content {
            if (activeReveal === reveal) {
                activeReveal = null
                onAcknowledged()
            }
        }
    } else {
        MafiaRoleRevealGateScreen(
            playerName = player.displayName,
            eyebrow = player.displayName,
            onRevealed = { activeReveal = Any() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

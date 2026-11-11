package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
    var revealed by remember(player.id) { mutableStateOf(false) }
    InPlaceBackHandler(revealed) { revealed = false }
    if (revealed) {
        content {
            revealed = false
            onAcknowledged()
        }
    } else {
        MafiaRoleRevealGateScreen(
            playerName = player.displayName,
            eyebrow = player.displayName,
            onRevealed = { revealed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

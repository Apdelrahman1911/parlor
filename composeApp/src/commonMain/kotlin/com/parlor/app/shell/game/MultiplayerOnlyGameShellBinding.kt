package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.app.shell.game.multiplayer.MultiplayerGameSpec
import com.parlor.app.shell.game.multiplayer.MultiplayerShellContent
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState

/** Reuses the registry's existing Host/Join routes without installing a second navigation engine. */
internal abstract class MultiplayerOnlyGameShellBinding<S : GameState, A : GameAction, E : GameEvent> :
    GameShellBinding, MultiplayerGameSpec<S, A, E> {
    override val capabilities = GameShellCapabilities(setOf(GameEntryMode.Host, GameEntryMode.Join))
    override val multiplayerContract: GameShellMultiplayerContract
        get() = GameShellMultiplayerContract(definition.id, version, definition.supportedPlayerCounts)

    @Composable
    override fun Content(
        launch: GameShellLaunch, onExit: () -> Unit, backRequest: GameShellBackRequest, modifier: Modifier,
    ) {
        require(launch.gameId == definition.id)
        MultiplayerShellContent(this, launch, capabilities, onExit, backRequest, modifier)
    }
}

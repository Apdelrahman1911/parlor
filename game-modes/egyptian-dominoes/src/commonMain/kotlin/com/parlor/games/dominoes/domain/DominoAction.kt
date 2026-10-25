package com.parlor.games.dominoes.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DominoAction : GameAction {
    @Serializable @SerialName("place")
    data class Place(val by: PlayerId, val token: Long, val move: Int, val tileId: Int, val end: DominoEnd) : DominoAction
    @Serializable @SerialName("draw")
    data class Draw(val by: PlayerId, val token: Long, val move: Int) : DominoAction
    @Serializable @SerialName("pass")
    data class Pass(val by: PlayerId, val token: Long, val move: Int) : DominoAction
    @Serializable @SerialName("next")
    data class NextRound(val token: Long) : DominoAction
    @Serializable @SerialName("rematch")
    data class Rematch(val token: Long) : DominoAction
    @Serializable @SerialName("offline")
    data class Disconnected(val playerId: PlayerId) : DominoAction
    @Serializable @SerialName("online")
    data class Reconnected(val playerId: PlayerId) : DominoAction
    @Serializable @SerialName("abort")
    data object Abort : DominoAction
}

data class DominoChanged(val roundToken: Long, val move: Int) : GameEvent

object DominoAuthority {
    fun isPlayerAction(action: DominoAction): Boolean = when (action) {
        is DominoAction.Disconnected, is DominoAction.Reconnected, DominoAction.Abort -> false
        else -> true
    }

    fun allowed(action: DominoAction, actor: PlayerId, host: PlayerId, state: DominoState): Boolean {
        if (state.players.none { it.id == actor }) return false
        return when (action) {
            is DominoAction.Place -> action.by == actor
            is DominoAction.Draw -> action.by == actor
            is DominoAction.Pass -> action.by == actor
            else -> actor == host
        }
    }
}

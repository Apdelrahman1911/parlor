package com.parlor.games.ghamza.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GhamzaAction : GameAction {
    @Serializable @SerialName("ready")
    data class Ready(val by: PlayerId, val token: Long) : GhamzaAction
    @Serializable @SerialName("winked")
    data class Winked(val by: PlayerId, val token: Long, val attempt: Int) : GhamzaAction
    @Serializable @SerialName("guess")
    data class Guess(val by: PlayerId, val token: Long, val target: PlayerId) : GhamzaAction
    @Serializable @SerialName("next")
    data class NextRound(val token: Long) : GhamzaAction
    @Serializable @SerialName("rematch")
    data class Rematch(val token: Long) : GhamzaAction
    @Serializable @SerialName("offline")
    data class Disconnected(val playerId: PlayerId) : GhamzaAction
    @Serializable @SerialName("online")
    data class Reconnected(val playerId: PlayerId) : GhamzaAction
    @Serializable @SerialName("abort")
    data object Abort : GhamzaAction
}

data class GhamzaChanged(val token: Long) : GameEvent

object GhamzaAuthority {
    fun isPlayerAction(action: GhamzaAction): Boolean = when (action) {
        is GhamzaAction.Disconnected, is GhamzaAction.Reconnected, GhamzaAction.Abort -> false
        else -> true
    }

    fun allowed(action: GhamzaAction, actor: PlayerId, host: PlayerId, state: GhamzaState): Boolean {
        if (state.players.none { it.id == actor }) return false
        return when (action) {
            is GhamzaAction.Ready -> action.by == actor
            is GhamzaAction.Winked -> action.by == actor
            is GhamzaAction.Guess -> action.by == actor
            else -> actor == host
        }
    }
}

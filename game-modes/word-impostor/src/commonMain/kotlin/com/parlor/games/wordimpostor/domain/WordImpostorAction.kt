package com.parlor.games.wordimpostor.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WordImpostorAction : GameAction {
    @Serializable @SerialName("ready")
    data class Ready(val by: PlayerId, val token: Long) : WordImpostorAction
    @Serializable @SerialName("answered")
    data class Answered(val by: PlayerId, val token: Long, val question: Int) : WordImpostorAction
    @Serializable @SerialName("open-voting")
    data class OpenVoting(val token: Long) : WordImpostorAction
    @Serializable @SerialName("vote")
    data class Vote(val by: PlayerId, val token: Long, val target: PlayerId) : WordImpostorAction
    @Serializable @SerialName("guess")
    data class Guess(val by: PlayerId, val token: Long, val wordId: String) : WordImpostorAction
    @Serializable @SerialName("next")
    data class NextRound(val token: Long) : WordImpostorAction
    @Serializable @SerialName("rematch")
    data class Rematch(val token: Long) : WordImpostorAction
    @Serializable @SerialName("offline")
    data class Disconnected(val playerId: PlayerId) : WordImpostorAction
    @Serializable @SerialName("online")
    data class Reconnected(val playerId: PlayerId) : WordImpostorAction
    @Serializable @SerialName("abort")
    data object Abort : WordImpostorAction
}

data class WordImpostorChanged(val token: Long, val phase: WordImpostorPhase) : GameEvent

object WordImpostorAuthority {
    fun isPlayerAction(action: WordImpostorAction): Boolean = when (action) {
        is WordImpostorAction.Disconnected, is WordImpostorAction.Reconnected, WordImpostorAction.Abort -> false
        else -> true
    }

    fun allowed(action: WordImpostorAction, actor: PlayerId, host: PlayerId, state: WordImpostorState): Boolean {
        if (state.players.none { it.id == actor }) return false
        return when (action) {
            is WordImpostorAction.Ready -> action.by == actor
            is WordImpostorAction.Answered -> action.by == actor
            is WordImpostorAction.Vote -> action.by == actor
            is WordImpostorAction.Guess -> action.by == actor
            else -> actor == host
        }
    }
}

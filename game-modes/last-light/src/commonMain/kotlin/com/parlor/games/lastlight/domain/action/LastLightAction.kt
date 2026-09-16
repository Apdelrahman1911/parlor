package com.parlor.games.lastlight.domain.action

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.games.lastlight.domain.model.CardId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Actors are attested by SessionController; IDs carried in payloads are never authority. */
@Serializable
sealed interface LastLightAction : GameAction {
    @Serializable
    @SerialName("play-cards")
    data class PlayCards(val by: PlayerId, val cardIds: List<CardId>) : LastLightAction

    @Serializable
    @SerialName("challenge")
    data class Challenge(val by: PlayerId) : LastLightAction

    @Serializable
    @SerialName("next-round")
    data object NextRound : LastLightAction

    @Serializable
    @SerialName("end-game")
    data object EndGame : LastLightAction

    @Serializable
    @SerialName("disconnected")
    data class MarkPlayerDisconnected(val playerId: PlayerId) : LastLightAction

    @Serializable
    @SerialName("reconnected")
    data class MarkPlayerReconnected(val playerId: PlayerId) : LastLightAction

    @Serializable
    @SerialName("continue-without")
    data class ContinueWithoutPlayer(val playerId: PlayerId) : LastLightAction
}

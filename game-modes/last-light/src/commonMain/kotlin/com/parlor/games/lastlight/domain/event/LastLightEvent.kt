package com.parlor.games.lastlight.domain.event

import com.parlor.core.ids.PlayerId
import com.parlor.engine.event.GameEvent

/** Public transitions only: an accepted play event never contains selected card IDs or ranks. */
sealed interface LastLightEvent : GameEvent {
    data class CardsPlayed(val playerId: PlayerId, val cardCount: Int, val sequence: Long) : LastLightEvent
    data class ChallengeResolved(val sequence: Long) : LastLightEvent
    data class RoundStarted(val roundNumber: Int) : LastLightEvent
    data class GameEnded(val winnerId: PlayerId?) : LastLightEvent
    data class PlayerDisconnected(val playerId: PlayerId) : LastLightEvent
    data class PlayerReconnected(val playerId: PlayerId) : LastLightEvent
}

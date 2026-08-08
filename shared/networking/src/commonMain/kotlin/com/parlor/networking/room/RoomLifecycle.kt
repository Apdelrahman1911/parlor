package com.parlor.networking.room

/** Logical multiplayer lifecycle shared by every transport and game. */
sealed interface RoomLifecycleState {
    data object Active : RoomLifecycleState

    data class Suspended(
        val resumeDeadlineEpochMillis: Long,
    ) : RoomLifecycleState

    data class Resuming(
        val resumeDeadlineEpochMillis: Long,
    ) : RoomLifecycleState

    data object Expired : RoomLifecycleState
    data object Closed : RoomLifecycleState
}

package com.parlor.session.multidevice

import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.JoinError

/**
 * Sealed states the Join screen can be in. Pure data; the UI maps each
 * one to a render.
 *
 *  - [Idle]      The controller hasn't started discovery yet.
 *  - [Scanning]  Looking for rooms — show a progress affordance.
 *  - [Found]     At least one room is visible — show the list.
 *  - [Empty]     Scan timed out with no rooms found — offer manual entry.
 *  - [Manual]    User opted to type a code by hand.
 *  - [Joining]   Submitting to the transport — show "connecting".
 *  - [Joined]    Transport accepted; host owns the [com.parlor.networking.room.LocalRoom].
 *  - [Failed]    Typed error — show the localized message for the variant.
 */
sealed interface JoinState {
    data object Idle : JoinState
    data object Scanning : JoinState
    data class Found(val rooms: List<DiscoveredRoom>) : JoinState
    data object Empty : JoinState
    data object Manual : JoinState
    data class Joining(val code: String) : JoinState
    data class Joined(val code: String) : JoinState
    data class Failed(val error: JoinError) : JoinState
}

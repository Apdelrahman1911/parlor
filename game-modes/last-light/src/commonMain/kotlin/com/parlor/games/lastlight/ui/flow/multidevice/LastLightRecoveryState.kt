package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.networking.room.RoomLifecycleState
import com.parlor.session.multidevice.PeerConnectionState

/** Public connection context only; never accepts a hand or a host projection. */
internal sealed interface LastLightRecoveryState {
    data object Reconnecting : LastLightRecoveryState
    data object Syncing : LastLightRecoveryState
    data class ReconnectingToHost(val displayName: String) : LastLightRecoveryState
    data class WaitingForPlayer(val displayName: String) : LastLightRecoveryState
}

internal fun lastLightHostRecoveryState(
    lifecycle: RoomLifecycleState,
    foregroundReady: Boolean,
    disconnectedPlayerName: String?,
): LastLightRecoveryState? = when {
    lifecycle != RoomLifecycleState.Active -> LastLightRecoveryState.Reconnecting
    !foregroundReady -> LastLightRecoveryState.Syncing
    disconnectedPlayerName != null -> LastLightRecoveryState.WaitingForPlayer(disconnectedPlayerName)
    else -> null
}

internal fun lastLightPeerRecoveryState(
    connection: PeerConnectionState,
    lifecycle: RoomLifecycleState,
    foregroundReady: Boolean,
    hostDisplayName: String,
    disconnectedPlayerName: String?,
): LastLightRecoveryState? = when {
    // A lost host is not yet represented in a fresh public game snapshot.
    // Name the reconnection target, without claiming why they are unavailable.
    connection.hostLost -> LastLightRecoveryState.ReconnectingToHost(hostDisplayName)
    connection.selfOffline || lifecycle != RoomLifecycleState.Active -> LastLightRecoveryState.Reconnecting
    !foregroundReady -> LastLightRecoveryState.Syncing
    disconnectedPlayerName != null -> LastLightRecoveryState.WaitingForPlayer(disconnectedPlayerName)
    else -> null
}

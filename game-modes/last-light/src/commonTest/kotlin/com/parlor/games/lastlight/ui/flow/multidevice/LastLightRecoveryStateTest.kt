package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.networking.room.RoomLifecycleState
import com.parlor.session.multidevice.PeerConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastLightRecoveryStateTest {
    @Test
    fun hostLossNamesThePublicHostBeforeAnyNewSnapshotCanArrive() {
        assertEquals(
            LastLightRecoveryState.ReconnectingToHost("Alex"),
            lastLightPeerRecoveryState(
                PeerConnectionState(hostLost = true), RoomLifecycleState.Resuming(120_000L), true, "Alex", null,
            ),
        )
    }

    @Test
    fun localOfflineAndValidationDoNotClaimThatAnotherPlayerLeft() {
        assertEquals(
            LastLightRecoveryState.Reconnecting,
            lastLightPeerRecoveryState(PeerConnectionState(selfOffline = true), RoomLifecycleState.Active, true, "Alex", "Sam"),
        )
        assertEquals(
            LastLightRecoveryState.Syncing,
            lastLightPeerRecoveryState(PeerConnectionState(), RoomLifecycleState.Active, false, "Alex", "Sam"),
        )
        assertEquals(LastLightRecoveryState.Syncing, lastLightHostRecoveryState(RoomLifecycleState.Active, false, "Sam"))
        assertEquals(
            LastLightRecoveryState.Reconnecting,
            lastLightHostRecoveryState(RoomLifecycleState.Resuming(120_000L), true, "Sam"),
        )
    }

    @Test
    fun connectedHostAndPeerNameTheSamePublicMissingPlayer() {
        val expected = LastLightRecoveryState.WaitingForPlayer("Sam")
        assertEquals(expected, lastLightHostRecoveryState(RoomLifecycleState.Active, true, "Sam"))
        assertEquals(expected, lastLightPeerRecoveryState(PeerConnectionState(), RoomLifecycleState.Active, true, "Alex", "Sam"))
    }

    @Test
    fun onlyReadyConnectedTablesWithoutMissingPlayersShowGameplay() {
        assertNull(lastLightHostRecoveryState(RoomLifecycleState.Active, true, null))
        assertNull(lastLightPeerRecoveryState(PeerConnectionState(), RoomLifecycleState.Active, true, "Alex", null))
        assertEquals(
            LastLightRecoveryState.Reconnecting,
            lastLightPeerRecoveryState(PeerConnectionState(), RoomLifecycleState.Resuming(120_000L), true, "Alex", null),
        )
    }
}

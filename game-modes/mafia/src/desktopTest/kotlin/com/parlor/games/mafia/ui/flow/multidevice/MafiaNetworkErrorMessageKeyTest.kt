package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.networking.room.NetError
import kotlin.test.Test
import kotlin.test.assertEquals

class MafiaNetworkErrorMessageKeyTest {
    @Test
    fun everyNetworkFailureKeepsItsActionableUiClassification() {
        val expected = listOf(
            NetError.NotConnected to MafiaNetworkErrorMessageKey.NotConnected,
            NetError.Timeout to MafiaNetworkErrorMessageKey.Timeout,
            NetError.PayloadTooLarge to MafiaNetworkErrorMessageKey.PayloadTooLarge,
            NetError.WrongCode to MafiaNetworkErrorMessageKey.WrongCode,
            NetError.HostDeclined to MafiaNetworkErrorMessageKey.HostDeclined,
            NetError.RoomFull to MafiaNetworkErrorMessageKey.RoomFull,
            NetError.SessionStarted to MafiaNetworkErrorMessageKey.SessionStarted,
            NetError.IncompatibleProtocol to MafiaNetworkErrorMessageKey.Incompatible,
            NetError.RateLimited to MafiaNetworkErrorMessageKey.RateLimited,
            NetError.RejoinExpired to MafiaNetworkErrorMessageKey.RejoinExpired,
            NetError.AlreadyConnected to MafiaNetworkErrorMessageKey.AlreadyConnected,
            NetError.SecureStorageUnavailable to MafiaNetworkErrorMessageKey.SecureStorage,
            NetError.CommandInFlight to MafiaNetworkErrorMessageKey.CommandInFlight,
            NetError.SessionSuspended to MafiaNetworkErrorMessageKey.Suspended,
            NetError.TransportFailure("must not reach UI") to MafiaNetworkErrorMessageKey.Transport,
            NetError.DisplayNameInUse to MafiaNetworkErrorMessageKey.NameInUse,
            NetError.Unauthorized to MafiaNetworkErrorMessageKey.Unauthorized,
            NetError.InvalidInput to MafiaNetworkErrorMessageKey.InvalidInput,
        )

        expected.forEach { (error, key) ->
            assertEquals(key, mafiaNetworkErrorMessageKey(error), error.toString())
        }
    }
}

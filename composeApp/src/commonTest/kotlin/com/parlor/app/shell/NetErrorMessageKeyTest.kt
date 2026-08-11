package com.parlor.app.shell

import com.parlor.networking.room.NetError
import kotlin.test.Test
import kotlin.test.assertEquals

class NetErrorMessageKeyTest {
    @Test
    fun everyNetworkFailureKeepsItsActionableUiClassification() {
        val expected = listOf(
            NetError.NotConnected to NetErrorMessageKey.NotConnected,
            NetError.Timeout to NetErrorMessageKey.Timeout,
            NetError.PayloadTooLarge to NetErrorMessageKey.PayloadTooLarge,
            NetError.WrongCode to NetErrorMessageKey.WrongCode,
            NetError.HostDeclined to NetErrorMessageKey.HostDeclined,
            NetError.RoomFull to NetErrorMessageKey.RoomFull,
            NetError.SessionStarted to NetErrorMessageKey.SessionStarted,
            NetError.IncompatibleProtocol to NetErrorMessageKey.Incompatible,
            NetError.RateLimited to NetErrorMessageKey.RateLimited,
            NetError.RejoinExpired to NetErrorMessageKey.RejoinExpired,
            NetError.AlreadyConnected to NetErrorMessageKey.AlreadyConnected,
            NetError.SecureStorageUnavailable to NetErrorMessageKey.SecureStorage,
            NetError.CommandInFlight to NetErrorMessageKey.CommandInFlight,
            NetError.SessionSuspended to NetErrorMessageKey.Suspended,
            NetError.TransportFailure("must not reach UI") to NetErrorMessageKey.Transport,
            NetError.DisplayNameInUse to NetErrorMessageKey.NameInUse,
            NetError.Unauthorized to NetErrorMessageKey.Unauthorized,
            NetError.InvalidInput to NetErrorMessageKey.InvalidInput,
        )

        expected.forEach { (error, key) ->
            assertEquals(key, netErrorMessageKey(error), error.toString())
        }
    }
}

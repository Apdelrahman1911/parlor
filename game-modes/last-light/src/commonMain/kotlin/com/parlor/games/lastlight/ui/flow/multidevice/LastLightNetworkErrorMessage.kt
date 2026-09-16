package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.runtime.Composable
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.md_net_error_already_connected
import com.parlor.games.lastlight.resources.md_net_error_command_in_flight
import com.parlor.games.lastlight.resources.md_net_error_host_declined
import com.parlor.games.lastlight.resources.md_net_error_incompatible
import com.parlor.games.lastlight.resources.md_net_error_invalid_input
import com.parlor.games.lastlight.resources.md_net_error_name_in_use
import com.parlor.games.lastlight.resources.md_net_error_not_connected
import com.parlor.games.lastlight.resources.md_net_error_payload_too_large
import com.parlor.games.lastlight.resources.md_net_error_rate_limited
import com.parlor.games.lastlight.resources.md_net_error_rejoin_expired
import com.parlor.games.lastlight.resources.md_net_error_room_full
import com.parlor.games.lastlight.resources.md_net_error_secure_storage
import com.parlor.games.lastlight.resources.md_net_error_session_started
import com.parlor.games.lastlight.resources.md_net_error_suspended
import com.parlor.games.lastlight.resources.md_net_error_timeout
import com.parlor.games.lastlight.resources.md_net_error_transport
import com.parlor.games.lastlight.resources.md_net_error_unauthorized
import com.parlor.games.lastlight.resources.md_net_error_wrong_code
import com.parlor.networking.room.NetError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun lastlightNetworkErrorMessage(error: NetError): String = stringResource(
    when (lastlightNetworkErrorMessageKey(error)) {
        LastLightNetworkErrorMessageKey.NotConnected -> Res.string.md_net_error_not_connected
        LastLightNetworkErrorMessageKey.Timeout -> Res.string.md_net_error_timeout
        LastLightNetworkErrorMessageKey.PayloadTooLarge -> Res.string.md_net_error_payload_too_large
        LastLightNetworkErrorMessageKey.WrongCode -> Res.string.md_net_error_wrong_code
        LastLightNetworkErrorMessageKey.HostDeclined -> Res.string.md_net_error_host_declined
        LastLightNetworkErrorMessageKey.RoomFull -> Res.string.md_net_error_room_full
        LastLightNetworkErrorMessageKey.SessionStarted -> Res.string.md_net_error_session_started
        LastLightNetworkErrorMessageKey.Incompatible -> Res.string.md_net_error_incompatible
        LastLightNetworkErrorMessageKey.RateLimited -> Res.string.md_net_error_rate_limited
        LastLightNetworkErrorMessageKey.RejoinExpired -> Res.string.md_net_error_rejoin_expired
        LastLightNetworkErrorMessageKey.AlreadyConnected -> Res.string.md_net_error_already_connected
        LastLightNetworkErrorMessageKey.SecureStorage -> Res.string.md_net_error_secure_storage
        LastLightNetworkErrorMessageKey.CommandInFlight -> Res.string.md_net_error_command_in_flight
        LastLightNetworkErrorMessageKey.Suspended -> Res.string.md_net_error_suspended
        LastLightNetworkErrorMessageKey.Transport -> Res.string.md_net_error_transport
        LastLightNetworkErrorMessageKey.NameInUse -> Res.string.md_net_error_name_in_use
        LastLightNetworkErrorMessageKey.Unauthorized -> Res.string.md_net_error_unauthorized
        LastLightNetworkErrorMessageKey.InvalidInput -> Res.string.md_net_error_invalid_input
    },
)

internal enum class LastLightNetworkErrorMessageKey {
    NotConnected,
    Timeout,
    PayloadTooLarge,
    WrongCode,
    HostDeclined,
    RoomFull,
    SessionStarted,
    Incompatible,
    RateLimited,
    RejoinExpired,
    AlreadyConnected,
    SecureStorage,
    CommandInFlight,
    Suspended,
    Transport,
    NameInUse,
    Unauthorized,
    InvalidInput,
}

internal fun lastlightNetworkErrorMessageKey(error: NetError): LastLightNetworkErrorMessageKey =
    when (error) {
        NetError.NotConnected -> LastLightNetworkErrorMessageKey.NotConnected
        NetError.Timeout -> LastLightNetworkErrorMessageKey.Timeout
        NetError.PayloadTooLarge -> LastLightNetworkErrorMessageKey.PayloadTooLarge
        NetError.WrongCode -> LastLightNetworkErrorMessageKey.WrongCode
        NetError.HostDeclined -> LastLightNetworkErrorMessageKey.HostDeclined
        NetError.RoomFull -> LastLightNetworkErrorMessageKey.RoomFull
        NetError.SessionStarted -> LastLightNetworkErrorMessageKey.SessionStarted
        NetError.IncompatibleProtocol -> LastLightNetworkErrorMessageKey.Incompatible
        NetError.RateLimited -> LastLightNetworkErrorMessageKey.RateLimited
        NetError.RejoinExpired -> LastLightNetworkErrorMessageKey.RejoinExpired
        NetError.AlreadyConnected -> LastLightNetworkErrorMessageKey.AlreadyConnected
        NetError.SecureStorageUnavailable -> LastLightNetworkErrorMessageKey.SecureStorage
        NetError.CommandInFlight -> LastLightNetworkErrorMessageKey.CommandInFlight
        NetError.SessionSuspended -> LastLightNetworkErrorMessageKey.Suspended
        is NetError.TransportFailure -> LastLightNetworkErrorMessageKey.Transport
        NetError.DisplayNameInUse -> LastLightNetworkErrorMessageKey.NameInUse
        NetError.Unauthorized -> LastLightNetworkErrorMessageKey.Unauthorized
        NetError.InvalidInput -> LastLightNetworkErrorMessageKey.InvalidInput
    }

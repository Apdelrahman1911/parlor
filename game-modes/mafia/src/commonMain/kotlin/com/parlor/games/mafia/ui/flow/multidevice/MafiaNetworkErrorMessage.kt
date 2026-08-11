package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.runtime.Composable
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_net_error_already_connected
import com.parlor.games.mafia.resources.md_net_error_command_in_flight
import com.parlor.games.mafia.resources.md_net_error_host_declined
import com.parlor.games.mafia.resources.md_net_error_incompatible
import com.parlor.games.mafia.resources.md_net_error_invalid_input
import com.parlor.games.mafia.resources.md_net_error_name_in_use
import com.parlor.games.mafia.resources.md_net_error_not_connected
import com.parlor.games.mafia.resources.md_net_error_payload_too_large
import com.parlor.games.mafia.resources.md_net_error_rate_limited
import com.parlor.games.mafia.resources.md_net_error_rejoin_expired
import com.parlor.games.mafia.resources.md_net_error_room_full
import com.parlor.games.mafia.resources.md_net_error_secure_storage
import com.parlor.games.mafia.resources.md_net_error_session_started
import com.parlor.games.mafia.resources.md_net_error_suspended
import com.parlor.games.mafia.resources.md_net_error_timeout
import com.parlor.games.mafia.resources.md_net_error_transport
import com.parlor.games.mafia.resources.md_net_error_unauthorized
import com.parlor.games.mafia.resources.md_net_error_wrong_code
import com.parlor.networking.room.NetError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun mafiaNetworkErrorMessage(error: NetError): String = stringResource(
    when (mafiaNetworkErrorMessageKey(error)) {
        MafiaNetworkErrorMessageKey.NotConnected -> Res.string.md_net_error_not_connected
        MafiaNetworkErrorMessageKey.Timeout -> Res.string.md_net_error_timeout
        MafiaNetworkErrorMessageKey.PayloadTooLarge -> Res.string.md_net_error_payload_too_large
        MafiaNetworkErrorMessageKey.WrongCode -> Res.string.md_net_error_wrong_code
        MafiaNetworkErrorMessageKey.HostDeclined -> Res.string.md_net_error_host_declined
        MafiaNetworkErrorMessageKey.RoomFull -> Res.string.md_net_error_room_full
        MafiaNetworkErrorMessageKey.SessionStarted -> Res.string.md_net_error_session_started
        MafiaNetworkErrorMessageKey.Incompatible -> Res.string.md_net_error_incompatible
        MafiaNetworkErrorMessageKey.RateLimited -> Res.string.md_net_error_rate_limited
        MafiaNetworkErrorMessageKey.RejoinExpired -> Res.string.md_net_error_rejoin_expired
        MafiaNetworkErrorMessageKey.AlreadyConnected -> Res.string.md_net_error_already_connected
        MafiaNetworkErrorMessageKey.SecureStorage -> Res.string.md_net_error_secure_storage
        MafiaNetworkErrorMessageKey.CommandInFlight -> Res.string.md_net_error_command_in_flight
        MafiaNetworkErrorMessageKey.Suspended -> Res.string.md_net_error_suspended
        MafiaNetworkErrorMessageKey.Transport -> Res.string.md_net_error_transport
        MafiaNetworkErrorMessageKey.NameInUse -> Res.string.md_net_error_name_in_use
        MafiaNetworkErrorMessageKey.Unauthorized -> Res.string.md_net_error_unauthorized
        MafiaNetworkErrorMessageKey.InvalidInput -> Res.string.md_net_error_invalid_input
    },
)

internal enum class MafiaNetworkErrorMessageKey {
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

internal fun mafiaNetworkErrorMessageKey(error: NetError): MafiaNetworkErrorMessageKey =
    when (error) {
        NetError.NotConnected -> MafiaNetworkErrorMessageKey.NotConnected
        NetError.Timeout -> MafiaNetworkErrorMessageKey.Timeout
        NetError.PayloadTooLarge -> MafiaNetworkErrorMessageKey.PayloadTooLarge
        NetError.WrongCode -> MafiaNetworkErrorMessageKey.WrongCode
        NetError.HostDeclined -> MafiaNetworkErrorMessageKey.HostDeclined
        NetError.RoomFull -> MafiaNetworkErrorMessageKey.RoomFull
        NetError.SessionStarted -> MafiaNetworkErrorMessageKey.SessionStarted
        NetError.IncompatibleProtocol -> MafiaNetworkErrorMessageKey.Incompatible
        NetError.RateLimited -> MafiaNetworkErrorMessageKey.RateLimited
        NetError.RejoinExpired -> MafiaNetworkErrorMessageKey.RejoinExpired
        NetError.AlreadyConnected -> MafiaNetworkErrorMessageKey.AlreadyConnected
        NetError.SecureStorageUnavailable -> MafiaNetworkErrorMessageKey.SecureStorage
        NetError.CommandInFlight -> MafiaNetworkErrorMessageKey.CommandInFlight
        NetError.SessionSuspended -> MafiaNetworkErrorMessageKey.Suspended
        is NetError.TransportFailure -> MafiaNetworkErrorMessageKey.Transport
        NetError.DisplayNameInUse -> MafiaNetworkErrorMessageKey.NameInUse
        NetError.Unauthorized -> MafiaNetworkErrorMessageKey.Unauthorized
        NetError.InvalidInput -> MafiaNetworkErrorMessageKey.InvalidInput
    }

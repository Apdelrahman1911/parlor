package com.parlor.games.whodunit.ui

import androidx.compose.runtime.Composable
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.data_error_corrupted
import com.parlor.games.whodunit.resources.data_error_disk_full
import com.parlor.games.whodunit.resources.data_error_io
import com.parlor.games.whodunit.resources.data_error_not_found
import com.parlor.games.whodunit.resources.data_error_permission_denied
import com.parlor.games.whodunit.resources.data_error_unknown
import com.parlor.games.whodunit.resources.net_error_not_connected
import com.parlor.games.whodunit.resources.net_error_name_in_use
import com.parlor.games.whodunit.resources.net_error_already_connected
import com.parlor.games.whodunit.resources.net_error_command_in_flight
import com.parlor.games.whodunit.resources.net_error_host_declined
import com.parlor.games.whodunit.resources.net_error_incompatible
import com.parlor.games.whodunit.resources.net_error_invalid_input
import com.parlor.games.whodunit.resources.net_error_timeout
import com.parlor.games.whodunit.resources.net_error_payload_too_large
import com.parlor.games.whodunit.resources.net_error_rate_limited
import com.parlor.games.whodunit.resources.net_error_rejoin_expired
import com.parlor.games.whodunit.resources.net_error_room_full
import com.parlor.games.whodunit.resources.net_error_secure_storage
import com.parlor.games.whodunit.resources.net_error_session_started
import com.parlor.games.whodunit.resources.net_error_suspended
import com.parlor.games.whodunit.resources.net_error_transport
import com.parlor.games.whodunit.resources.net_error_unauthorized
import com.parlor.games.whodunit.resources.net_error_wrong_code
import com.parlor.core.result.DataError
import com.parlor.networking.room.NetError
import org.jetbrains.compose.resources.stringResource

/**
 * Friendly, localised message for a [DataError]. Use this instead of
 * `error.toString()` at every UI display site — the raw `toString` of a
 * data-class error leaks internal structure ("DataError$Unknown(message=...)")
 * to the user and is never translated.
 */
@Composable
internal fun dataErrorMessage(error: DataError): String = stringResource(
    when (error) {
        is DataError.NotFound -> Res.string.data_error_not_found
        is DataError.CorruptedData -> Res.string.data_error_corrupted
        is DataError.IoError -> Res.string.data_error_io
        is DataError.DiskFull -> Res.string.data_error_disk_full
        is DataError.PermissionDenied -> Res.string.data_error_permission_denied
        is DataError.Unknown -> Res.string.data_error_unknown
    },
)

/**
 * Friendly, localised message for a [NetError]. Same rationale as
 * [dataErrorMessage] — `TransportFailure(reason=...)` etc. is for logs,
 * not for the user.
 */
@Composable
internal fun netErrorMessage(error: NetError): String = stringResource(
    when (netErrorMessageKey(error)) {
        NetErrorMessageKey.NotConnected -> Res.string.net_error_not_connected
        NetErrorMessageKey.Timeout -> Res.string.net_error_timeout
        NetErrorMessageKey.PayloadTooLarge -> Res.string.net_error_payload_too_large
        NetErrorMessageKey.WrongCode -> Res.string.net_error_wrong_code
        NetErrorMessageKey.HostDeclined -> Res.string.net_error_host_declined
        NetErrorMessageKey.RoomFull -> Res.string.net_error_room_full
        NetErrorMessageKey.SessionStarted -> Res.string.net_error_session_started
        NetErrorMessageKey.Incompatible -> Res.string.net_error_incompatible
        NetErrorMessageKey.RateLimited -> Res.string.net_error_rate_limited
        NetErrorMessageKey.RejoinExpired -> Res.string.net_error_rejoin_expired
        NetErrorMessageKey.AlreadyConnected -> Res.string.net_error_already_connected
        NetErrorMessageKey.SecureStorage -> Res.string.net_error_secure_storage
        NetErrorMessageKey.CommandInFlight -> Res.string.net_error_command_in_flight
        NetErrorMessageKey.Suspended -> Res.string.net_error_suspended
        NetErrorMessageKey.Transport -> Res.string.net_error_transport
        NetErrorMessageKey.NameInUse -> Res.string.net_error_name_in_use
        NetErrorMessageKey.Unauthorized -> Res.string.net_error_unauthorized
        NetErrorMessageKey.InvalidInput -> Res.string.net_error_invalid_input
    },
)

internal enum class NetErrorMessageKey {
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

internal fun netErrorMessageKey(error: NetError): NetErrorMessageKey = when (error) {
    NetError.NotConnected -> NetErrorMessageKey.NotConnected
    NetError.Timeout -> NetErrorMessageKey.Timeout
    NetError.PayloadTooLarge -> NetErrorMessageKey.PayloadTooLarge
    NetError.WrongCode -> NetErrorMessageKey.WrongCode
    NetError.HostDeclined -> NetErrorMessageKey.HostDeclined
    NetError.RoomFull -> NetErrorMessageKey.RoomFull
    NetError.SessionStarted -> NetErrorMessageKey.SessionStarted
    NetError.IncompatibleProtocol -> NetErrorMessageKey.Incompatible
    NetError.RateLimited -> NetErrorMessageKey.RateLimited
    NetError.RejoinExpired -> NetErrorMessageKey.RejoinExpired
    NetError.AlreadyConnected -> NetErrorMessageKey.AlreadyConnected
    NetError.SecureStorageUnavailable -> NetErrorMessageKey.SecureStorage
    NetError.CommandInFlight -> NetErrorMessageKey.CommandInFlight
    NetError.SessionSuspended -> NetErrorMessageKey.Suspended
    is NetError.TransportFailure -> NetErrorMessageKey.Transport
    NetError.DisplayNameInUse -> NetErrorMessageKey.NameInUse
    NetError.Unauthorized -> NetErrorMessageKey.Unauthorized
    NetError.InvalidInput -> NetErrorMessageKey.InvalidInput
}

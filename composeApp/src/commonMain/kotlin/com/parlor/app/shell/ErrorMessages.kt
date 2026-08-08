package com.parlor.app.shell

import androidx.compose.runtime.Composable
import com.parlor.app.resources.Res
import com.parlor.app.resources.data_error_corrupted
import com.parlor.app.resources.data_error_disk_full
import com.parlor.app.resources.data_error_io
import com.parlor.app.resources.data_error_not_found
import com.parlor.app.resources.data_error_permission_denied
import com.parlor.app.resources.data_error_unknown
import com.parlor.app.resources.net_error_not_connected
import com.parlor.app.resources.net_error_timeout
import com.parlor.app.resources.net_error_transport
import com.parlor.app.resources.net_error_unauthorized
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
fun dataErrorMessage(error: DataError): String = stringResource(
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
fun netErrorMessage(error: NetError): String = stringResource(
    when (error) {
        is NetError.NotConnected -> Res.string.net_error_not_connected
        is NetError.Timeout -> Res.string.net_error_timeout
        is NetError.PayloadTooLarge,
        is NetError.WrongCode,
        is NetError.HostDeclined,
        is NetError.RoomFull,
        is NetError.SessionStarted,
        is NetError.IncompatibleProtocol,
        is NetError.RateLimited,
        is NetError.CommandInFlight,
        is NetError.SessionSuspended,
        is NetError.TransportFailure -> Res.string.net_error_transport
        is NetError.Unauthorized -> Res.string.net_error_unauthorized
    },
)

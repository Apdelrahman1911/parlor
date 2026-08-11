package com.parlor.app.shell.home

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.ResumableSessionInfo

/** Non-sensitive result of checking both local and multiplayer recovery stores. */
internal sealed interface HomeRecoveryAvailability {
    data object Loading : HomeRecoveryAvailability

    data class Ready(
        val unfinishedSessions: List<SessionId>,
        val resumableMultiplayer: ResumableSessionInfo?,
        /** At least one source could not be read or contained an unsupported record. */
        val hasUnavailableSource: Boolean,
    ) : HomeRecoveryAvailability
}

/**
 * Keeps partial successes visible while ensuring a failed recovery lookup is
 * never misrepresented as an empty store. Raw persistence/transport errors
 * intentionally do not enter UI state because they may contain diagnostics
 * that are unsuitable for presentation.
 */
internal fun resolveHomeRecoveryAvailability(
    localResult: Result<List<SessionId>, DataError>,
    multiplayerResult: Result<ResumableSessionInfo?, NetError>,
    supportsMultiplayerResume: (ResumableSessionInfo) -> Boolean,
): HomeRecoveryAvailability.Ready {
    val unfinishedSessions = when (localResult) {
        is Result.Success -> localResult.data
        is Result.Failure -> emptyList()
    }
    val localUnavailable = localResult is Result.Failure

    val multiplayer = when (multiplayerResult) {
        is Result.Success -> multiplayerResult.data?.takeIf(supportsMultiplayerResume)
        is Result.Failure -> null
    }
    val multiplayerUnavailable = when (multiplayerResult) {
        is Result.Failure -> true
        is Result.Success -> {
            val candidate = multiplayerResult.data
            candidate != null && multiplayer == null
        }
    }

    return HomeRecoveryAvailability.Ready(
        unfinishedSessions = unfinishedSessions,
        resumableMultiplayer = multiplayer,
        hasUnavailableSource = localUnavailable || multiplayerUnavailable,
    )
}

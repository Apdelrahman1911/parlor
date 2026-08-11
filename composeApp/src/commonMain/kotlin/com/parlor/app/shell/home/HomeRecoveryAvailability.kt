package com.parlor.app.shell.home

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.storage.snapshot.SnapshotStore

/** Minimal, non-private envelope data needed to identify a local recovery tile. */
internal data class LocalRecoveryEntry(
    val sessionId: SessionId,
    /** Null when the record is unreadable or its authenticated envelope is inconsistent. */
    val gameId: GameId?,
)

internal data class LocalRecoveryInventory(
    val entries: List<LocalRecoveryEntry>,
    val hasUnreadableRecord: Boolean,
)

/** Non-sensitive result of checking both local and multiplayer recovery stores. */
internal sealed interface HomeRecoveryAvailability {
    data object Loading : HomeRecoveryAvailability

    data class Ready(
        val unfinishedSessions: List<LocalRecoveryEntry>,
        val resumableMultiplayer: ResumableSessionInfo?,
        /** At least one source could not be read or contained an unsupported record. */
        val hasUnavailableSource: Boolean,
    ) : HomeRecoveryAvailability
}

/**
 * Reads only each authenticated snapshot envelope. Unreadable records remain
 * addressable so the player can open the existing Retry / Discard recovery
 * surface; one damaged record cannot hide healthy saves.
 */
internal suspend fun readLocalRecoveryInventory(
    store: SnapshotStore,
): Result<LocalRecoveryInventory, DataError> = when (val listed = store.listUnfinished()) {
    is Result.Failure -> listed
    is Result.Success -> {
        var hasUnreadableRecord = false
        val entries = listed.data.distinct().map { sessionId ->
            when (val loaded = store.load(sessionId)) {
                is Result.Success -> {
                    val snapshot = loaded.data
                    if (snapshot.sessionId == sessionId) {
                        LocalRecoveryEntry(sessionId, snapshot.gameId)
                    } else {
                        hasUnreadableRecord = true
                        LocalRecoveryEntry(sessionId, null)
                    }
                }

                is Result.Failure -> {
                    hasUnreadableRecord = true
                    LocalRecoveryEntry(sessionId, null)
                }
            }
        }
        Result.Success(LocalRecoveryInventory(entries, hasUnreadableRecord))
    }
}

/**
 * Keeps partial successes visible while ensuring a failed recovery lookup is
 * never misrepresented as an empty store. Raw persistence/transport errors
 * intentionally do not enter UI state because they may contain diagnostics
 * that are unsuitable for presentation.
 */
internal fun resolveHomeRecoveryAvailability(
    localResult: Result<LocalRecoveryInventory, DataError>,
    multiplayerResult: Result<ResumableSessionInfo?, NetError>,
    supportsLocalResume: (LocalRecoveryEntry) -> Boolean,
    supportsMultiplayerResume: (ResumableSessionInfo) -> Boolean,
): HomeRecoveryAvailability.Ready {
    val unfinishedSessions = when (localResult) {
        is Result.Success -> localResult.data.entries
        is Result.Failure -> emptyList()
    }
    val localUnavailable = when (localResult) {
        is Result.Failure -> true
        is Result.Success -> localResult.data.hasUnreadableRecord ||
            localResult.data.entries.any { entry ->
                entry.gameId == null || !supportsLocalResume(entry)
            }
    }

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

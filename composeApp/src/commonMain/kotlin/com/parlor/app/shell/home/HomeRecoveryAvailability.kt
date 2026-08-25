package com.parlor.app.shell.home

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Home keeps recovery scanning bounded to a small, actionable set of tiles. */
internal const val MAX_LOCAL_RECOVERY_ENTRIES: Int = 8

/** Minimal, non-private envelope data needed to identify a local recovery tile. */
internal data class LocalRecoveryEntry(
    val sessionId: SessionId,
    /** Null when the record is unreadable or its authenticated envelope is inconsistent. */
    val gameId: GameId?,
)

internal data class LocalRecoveryInventory(
    val entries: List<LocalRecoveryEntry>,
    val hasUnreadableRecord: Boolean,
    /** More records exist than Home can inspect and offer in one bounded scan. */
    val hasAdditionalRecords: Boolean = false,
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
 * Reads only metadata from each authenticated snapshot envelope. Unreadable records remain
 * addressable so the player can open the existing Retry / Discard recovery
 * surface; one damaged record cannot hide healthy saves. The scan is bounded
 * because Home can only offer [MAX_LOCAL_RECOVERY_ENTRIES] recovery actions.
 */
internal suspend fun readLocalRecoveryInventory(
    store: SnapshotStore,
): Result<LocalRecoveryInventory, DataError> = when (val listed = store.listUnfinished()) {
    is Result.Failure -> listed
    is Result.Success -> {
        var hasUnreadableRecord = false
        val sessionIds = listed.data
            .asSequence()
            .distinct()
            .take(MAX_LOCAL_RECOVERY_ENTRIES + 1)
            .toList()
        val entries = sessionIds.take(MAX_LOCAL_RECOVERY_ENTRIES).map { sessionId ->
            when (val loaded = store.loadMetadata(sessionId)) {
                is Result.Success -> {
                    val metadata = loaded.data
                    if (metadata.sessionId == sessionId) {
                        LocalRecoveryEntry(sessionId, metadata.gameId)
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
        Result.Success(
            LocalRecoveryInventory(
                entries = entries,
                hasUnreadableRecord = hasUnreadableRecord,
                hasAdditionalRecords = sessionIds.size > MAX_LOCAL_RECOVERY_ENTRIES,
            ),
        )
    }
}

/** Starts local and multiplayer recovery probes together so neither delays the other. */
internal suspend fun loadHomeRecoveryAvailability(
    store: SnapshotStore,
    loadMultiplayer: suspend () -> Result<ResumableSessionInfo?, NetError>,
    supportsLocalResume: (LocalRecoveryEntry) -> Boolean,
    supportsMultiplayerResume: (ResumableSessionInfo) -> Boolean,
): HomeRecoveryAvailability.Ready = coroutineScope {
    val local = async { readLocalRecoveryInventory(store) }
    val multiplayer = async { loadMultiplayer() }
    resolveHomeRecoveryAvailability(
        localResult = local.await(),
        multiplayerResult = multiplayer.await(),
        supportsLocalResume = supportsLocalResume,
        supportsMultiplayerResume = supportsMultiplayerResume,
    )
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
            localResult.data.hasAdditionalRecords ||
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

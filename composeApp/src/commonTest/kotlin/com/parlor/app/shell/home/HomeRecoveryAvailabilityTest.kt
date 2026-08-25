package com.parlor.app.shell.home

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotMetadata
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HomeRecoveryAvailabilityTest {
    private val resumable = ResumableSessionInfo(
        gameId = GameId("whodunit"),
        gameVersion = 1,
        displayName = "Player",
        expiresAtEpochMillis = 10_000,
    )

    @Test
    fun failedStoresAreReportedInsteadOfMasqueradingAsEmpty() {
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Failure(DataError.PermissionDenied),
            multiplayerResult = Result.Failure(NetError.SecureStorageUnavailable),
            supportsLocalResume = { true },
            supportsMultiplayerResume = { true },
        )

        assertTrue(state.hasUnavailableSource)
        assertTrue(state.unfinishedSessions.isEmpty())
        assertNull(state.resumableMultiplayer)
    }

    @Test
    fun successfulSourceRemainsAvailableWhenTheOtherSourceFails() {
        val session = LocalRecoveryEntry(SessionId("local-session"), GameId("mafia"))
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(LocalRecoveryInventory(listOf(session), false)),
            multiplayerResult = Result.Failure(NetError.SecureStorageUnavailable),
            supportsLocalResume = { true },
            supportsMultiplayerResume = { true },
        )

        assertEquals(listOf(session), state.unfinishedSessions)
        assertNull(state.resumableMultiplayer)
        assertTrue(state.hasUnavailableSource)
    }

    @Test
    fun compatibleRecoveryRecordsAreExposedWithoutAnError() {
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(LocalRecoveryInventory(emptyList(), false)),
            multiplayerResult = Result.Success(resumable),
            supportsLocalResume = { true },
            supportsMultiplayerResume = { it.gameId == GameId("whodunit") },
        )

        assertEquals(resumable, state.resumableMultiplayer)
        assertFalse(state.hasUnavailableSource)
    }

    @Test
    fun unsupportedSavedMultiplayerRecordIsReportedAndCannotBeOpened() {
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(LocalRecoveryInventory(emptyList(), false)),
            multiplayerResult = Result.Success(resumable),
            supportsLocalResume = { true },
            supportsMultiplayerResume = { false },
        )

        assertNull(state.resumableMultiplayer)
        assertTrue(state.hasUnavailableSource)
    }

    @Test
    fun localInventoryIdentifiesHealthyGamesAndRetainsUnreadableRecords() = runTest {
        val store = InMemorySnapshotStore()
        val healthy = SessionId("healthy")
        val mismatched = SessionId("mismatched-file")
        store.save(snapshot(healthy, GameId("mafia")))
        store.save(snapshot(SessionId("other-envelope"), GameId("whodunit")))
        val listedStore = ListedSnapshotStore(
            delegate = store,
            listed = listOf(healthy, healthy, mismatched),
            mismatchedSnapshot = snapshot(SessionId("other-envelope"), GameId("whodunit")),
        )

        val inventory = (readLocalRecoveryInventory(listedStore) as Result.Success).data

        assertEquals(
            listOf(
                LocalRecoveryEntry(healthy, GameId("mafia")),
                LocalRecoveryEntry(mismatched, null),
            ),
            inventory.entries,
        )
        assertTrue(inventory.hasUnreadableRecord)
    }

    @Test
    fun localInventoryBoundsMetadataReadsWithoutLoadingSnapshotPayloads() = runTest {
        val sessionIds = (0..MAX_LOCAL_RECOVERY_ENTRIES).map { index ->
            SessionId("session-$index")
        }
        val store = MetadataOnlyStore(sessionIds)

        val inventory = (readLocalRecoveryInventory(store) as Result.Success).data

        assertEquals(
            sessionIds.take(MAX_LOCAL_RECOVERY_ENTRIES),
            inventory.entries.map { it.sessionId },
        )
        assertEquals(sessionIds.take(MAX_LOCAL_RECOVERY_ENTRIES), store.metadataRequests)
        assertEquals(0, store.fullLoadRequests)
        assertTrue(inventory.hasAdditionalRecords)
    }

    @Test
    fun multiplayerRecoveryProbeStartsWhileLocalInventoryIsSlow() = runTest {
        val localReadStarted = CompletableDeferred<Unit>()
        val releaseLocalRead = CompletableDeferred<Unit>()
        val multiplayerProbeStarted = CompletableDeferred<Unit>()
        val store = object : SnapshotStore by InMemorySnapshotStore() {
            override suspend fun listUnfinished(): Result<List<SessionId>, DataError> {
                localReadStarted.complete(Unit)
                releaseLocalRead.await()
                return Result.Success(emptyList())
            }
        }

        val availability = async {
            loadHomeRecoveryAvailability(
                store = store,
                loadMultiplayer = {
                    multiplayerProbeStarted.complete(Unit)
                    Result.Success(null)
                },
                supportsLocalResume = { true },
                supportsMultiplayerResume = { true },
            )
        }

        localReadStarted.await()
        multiplayerProbeStarted.await()
        releaseLocalRead.complete(Unit)

        assertFalse(availability.await().hasUnavailableSource)
    }

    @Test
    fun unsupportedLocalGameIsVisibleButMarksRecoverySourceUnavailable() {
        val entry = LocalRecoveryEntry(SessionId("unknown"), GameId("future-game"))

        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(LocalRecoveryInventory(listOf(entry), false)),
            multiplayerResult = Result.Success(null),
            supportsLocalResume = { false },
            supportsMultiplayerResume = { true },
        )

        assertEquals(listOf(entry), state.unfinishedSessions)
        assertTrue(state.hasUnavailableSource)
    }

    private fun snapshot(sessionId: SessionId, gameId: GameId) = GameSnapshot(
        sessionId = sessionId,
        gameId = gameId,
        engineVersion = SemVer(1, 0, 0),
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = "setup",
        payload = byteArrayOf(1),
    )

    private class ListedSnapshotStore(
        private val delegate: SnapshotStore,
        private val listed: List<SessionId>,
        private val mismatchedSnapshot: GameSnapshot,
    ) : SnapshotStore by delegate {
        override suspend fun listUnfinished(): Result<List<SessionId>, DataError> =
            Result.Success(listed)

        override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> =
            if (sessionId == SessionId("mismatched-file")) {
                Result.Success(mismatchedSnapshot)
            } else {
                delegate.load(sessionId)
            }
    }

    private class MetadataOnlyStore(
        private val sessionIds: List<SessionId>,
    ) : SnapshotStore {
        val metadataRequests = mutableListOf<SessionId>()
        var fullLoadRequests: Int = 0
            private set

        override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> = EmptyOk

        override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> {
            fullLoadRequests += 1
            return Result.Failure(DataError.NotFound)
        }

        override suspend fun loadMetadata(
            sessionId: SessionId,
        ): Result<SnapshotMetadata, DataError> {
            metadataRequests += sessionId
            return Result.Success(SnapshotMetadata(sessionId, GameId("mafia")))
        }

        override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = EmptyOk

        override suspend fun listUnfinished(): Result<List<SessionId>, DataError> =
            Result.Success(sessionIds)
    }
}

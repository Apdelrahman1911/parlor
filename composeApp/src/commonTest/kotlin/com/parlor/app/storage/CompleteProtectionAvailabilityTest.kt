package com.parlor.app.storage

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotFileSystem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CompleteProtectionAvailabilityTest {
    @Test
    fun unqualifiedProtectionDoesNotClaimUnreadSnapshotsAreCorrupt() = runTest {
        val metadata = listOf(
            Metadata(false, true, COMPLETE, COMPLETE),
            Metadata(null, true, COMPLETE, COMPLETE),
            Metadata(true, false, COMPLETE, COMPLETE),
            Metadata(true, true, null, COMPLETE),
            Metadata(true, true, "none", COMPLETE),
            Metadata(true, true, COMPLETE, null),
        )
        for (sample in metadata) {
            val filesystem = PolicyFileSystem(sample)
            val store = FileBackedSnapshotStore(filesystem, Json)
            val sessionId = SessionId("unread-synthetic-snapshot")
            assertEquals(Result.Failure(DataError.IoError("snapshot_io")), store.load(sessionId))
            assertEquals(Result.Failure(DataError.IoError("snapshot_io")), store.loadMetadata(sessionId))
            assertEquals(0, filesystem.plaintextReads)
        }
    }

    private data class Metadata(
        val volumeSupportsProtection: Boolean?,
        val attributesReadSucceeded: Boolean,
        val observedProtection: String?,
        val completeProtection: String?,
    )

    private class PolicyFileSystem(private val metadata: Metadata) : SnapshotFileSystem {
        var plaintextReads = 0
            private set

        override suspend fun read(name: String): ByteArray? {
            requireVerifiedCompleteSnapshotProtection(
                metadata.volumeSupportsProtection,
                metadata.attributesReadSucceeded,
                metadata.observedProtection,
                metadata.completeProtection,
            )
            plaintextReads++
            error("Rejected metadata must never reach plaintext")
        }

        override suspend fun write(name: String, bytes: ByteArray) = error("Unexpected write")
        override suspend fun delete(name: String) = error("Unexpected delete")
        override suspend fun list(): List<String> = error("Unexpected inventory")
    }

    private companion object {
        const val COMPLETE = "synthetic-complete"
    }
}

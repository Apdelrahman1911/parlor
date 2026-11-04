@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotFileSystem
import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionNone
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Negative native controls; neither case qualifies Complete protection or physical lock behavior. */
class IosCompleteSnapshotProtectionTest {
    @Test
    fun missingNativeProtectionReportsOpaqueIoWithoutReadingBytes(): Unit = runBlocking {
        val path = newFixturePath()
        var plaintextReads = 0
        val filesystem = object : SnapshotFileSystem {
            override suspend fun read(name: String): ByteArray? {
                requireCompleteSnapshotProtection(fileManager, path)
                plaintextReads++
                return null
            }

            override suspend fun write(name: String, bytes: ByteArray) = error("Unexpected write")
            override suspend fun delete(name: String) = error("Unexpected delete")
            override suspend fun list(): List<String> = error("Unexpected inventory")
        }
        val store = FileBackedSnapshotStore(filesystem, Json)
        val sessionId = SessionId("missing-native-protection")
        assertFalse(fileManager.fileExistsAtPath(path))
        assertEquals(Result.Failure(DataError.IoError("snapshot_io")), store.load(sessionId))
        assertEquals(Result.Failure(DataError.IoError("snapshot_io")), store.loadMetadata(sessionId))
        assertEquals(0, plaintextReads)
        assertFalse(fileManager.fileExistsAtPath(path))
    }

    @Test
    fun aMissingFileCannotBeQualifiedByMissingMetadata() {
        val path = newFixturePath()
        assertFailsWith<SnapshotProtectionException> {
            requireCompleteSnapshotProtection(fileManager, path)
        }
    }

    @Test
    fun aRejectedProtectionSetterCannotCreateOrQualifyAMissingItem() {
        val path = newFixturePath()
        assertFailsWith<SnapshotProtectionException> {
            repairCompleteSnapshotProtection(fileManager, path)
        }
        assertFalse(fileManager.fileExistsAtPath(path))
    }

    @Test
    fun anUnprotectedFileCannotBeQualifiedByASuccessfulWrite() {
        val path = newFixturePath()
        val bytes = byteArrayOf(1, 2, 3)
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        try {
            assertTrue(data.writeToFile(
                path,
                options = NSDataWritingAtomic or NSDataWritingFileProtectionNone,
                error = null,
            ))
            assertFailsWith<SnapshotProtectionException> {
                requireCompleteSnapshotProtection(fileManager, path)
            }
        } finally {
            if (fileManager.fileExistsAtPath(path)) {
                assertTrue(fileManager.removeItemAtPath(path, error = null))
            }
        }
    }

    private fun newFixturePath(): String =
        "${NSTemporaryDirectory()}parlor-complete-negative-${NSUUID.UUID().UUIDString}.bin"

    private companion object {
        val fileManager: NSFileManager = NSFileManager.defaultManager
    }
}

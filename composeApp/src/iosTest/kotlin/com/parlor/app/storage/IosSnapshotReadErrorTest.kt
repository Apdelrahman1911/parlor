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
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.EBADF
import platform.posix.F_GETFD
import platform.posix.O_RDONLY
import platform.posix.O_WRONLY
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.open
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual Foundation I/O failures on owned synthetic files, not device-lock simulation. */
class IosSnapshotReadErrorTest {
    @Test
    fun zeroLimitAcceptsAnEmptyFile() {
        withTemporaryData(ByteArray(0)) { path ->
            assertContentEquals(ByteArray(0), readBoundedSnapshotBytes(path, 0))
        }
    }

    @Test
    fun zeroLimitStillRejectsOneByte() {
        withTemporaryData(byteArrayOf(1)) { path ->
            assertFailsWith<SnapshotProtectionException> { readBoundedSnapshotBytes(path, 0) }
            assertContentEquals(byteArrayOf(1), readBoundedSnapshotBytes(path, 1))
        }
    }

    @Test
    fun negativeLimitIsRejectedBeforeOpeningOrCreatingAFile() {
        val path = temporaryPath()
        assertFailsWith<IllegalArgumentException> { readBoundedSnapshotBytes(path, -1) }
        assertFalse(fileManager.fileExistsAtPath(path))
    }

    @Test
    fun missingFileOpenFailureDoesNotExposeItsPathOrCreateIt() {
        val path = temporaryPath()
        val failure = assertFailsWith<IllegalStateException> { readBoundedSnapshotBytes(path, 8) }
        assertEquals("Couldn't open protected snapshot", failure.message)
        assertFalse(fileManager.fileExistsAtPath(path))
    }

    @Test
    fun successfulReadClosesItsOwnedDescriptor() {
        val original = byteArrayOf(2, 3, 4)
        withTemporaryData(original) { path ->
            val descriptor = openDescriptor(path, O_RDONLY)
            assertContentEquals(original, readOwnedSnapshotHandle(ownedHandle(descriptor), 3))
            assertClosed(descriptor)
        }
    }

    @Test
    fun oversizedReadClosesItsOwnedDescriptorAndPreservesTheFile() {
        val original = byteArrayOf(2, 3, 4)
        withTemporaryData(original) { path ->
            val descriptor = openDescriptor(path, O_RDONLY)
            assertFailsWith<SnapshotProtectionException> {
                readOwnedSnapshotHandle(ownedHandle(descriptor), 2)
            }
            assertClosed(descriptor)
            assertContentEquals(original, readBoundedSnapshotBytes(path, 3))
        }
    }

    @Test
    fun invalidLimitStillClosesAnAlreadyTransferredHandle() {
        withTemporaryData(byteArrayOf(2)) { path ->
            val descriptor = openDescriptor(path, O_RDONLY)
            assertFailsWith<IllegalArgumentException> {
                readOwnedSnapshotHandle(ownedHandle(descriptor), -1)
            }
            assertClosed(descriptor)
            assertContentEquals(byteArrayOf(2), readBoundedSnapshotBytes(path, 1))
        }
    }

    @Test
    fun nativeReadErrorBecomesOpaqueIoWithoutRetriesDeletionOrDescriptorLeaks(): Unit = runBlocking {
        val original = byteArrayOf(2, 3, 4)
        withTemporaryData(original) { path ->
            var reads = 0
            val filesystem = object : SnapshotFileSystem {
                override suspend fun read(name: String): ByteArray {
                    reads++
                    // Valid, exclusively owned write-only FD: read must fail
                    // through NSError, not by using a closed/global handle.
                    val descriptor = openDescriptor(path, O_WRONLY)
                    try {
                        return readOwnedSnapshotHandle(ownedHandle(descriptor), 8)
                    } finally {
                        assertClosed(descriptor)
                    }
                }
                override suspend fun write(name: String, bytes: ByteArray): Unit = error("Unexpected write")
                override suspend fun delete(name: String): Unit = error("Unexpected delete")
                override suspend fun list(): List<String> = error("Unexpected inventory")
            }
            val store = FileBackedSnapshotStore(filesystem, Json)
            val sessionId = SessionId("synthetic-read-error")
            assertEquals(Result.Failure(DataError.IoError("snapshot_io")), store.load(sessionId))
            assertEquals(1, reads)
            assertEquals(Result.Failure(DataError.IoError("snapshot_io")), store.loadMetadata(sessionId))
            assertEquals(2, reads)
            assertContentEquals(original, readBoundedSnapshotBytes(path, original.size))
        }
    }

    private fun openDescriptor(path: String, flags: Int): Int = open(path, flags).also {
        assertTrue(it >= 0, "Synthetic descriptor must open successfully")
    }

    private fun ownedHandle(descriptor: Int): NSFileHandle = NSFileHandle(descriptor, closeOnDealloc = true)

    private fun assertClosed(descriptor: Int) {
        val status = fcntl(descriptor, F_GETFD)
        val error = errno
        assertEquals(-1, status)
        assertEquals(EBADF, error)
    }

    private inline fun withTemporaryData(bytes: ByteArray, block: (String) -> Unit) {
        val path = temporaryPath()
        val data = if (bytes.isEmpty()) {
            NSData.create(bytes = null, length = 0u)
        } else {
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        }
        check(data.writeToFile(path, atomically = true))
        try {
            block(path)
        } finally {
            check(fileManager.removeItemAtPath(path, error = null))
        }
    }

    private fun temporaryPath(): String = "${NSTemporaryDirectory()}parlor-read-${NSUUID.UUID().UUIDString}.bin"

    private companion object {
        val fileManager: NSFileManager = NSFileManager.defaultManager
    }
}

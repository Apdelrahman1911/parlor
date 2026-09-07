@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Native Foundation checks using only UUID-named synthetic test records; no Keychain dependency. */
class IosLegacySnapshotBackupTest {
    @Test
    fun coldInventoryProtectsMalformedLegacyWithoutDestroyingItsLastCopy(): Unit = runBlocking {
        val plaintext = malformedLegacy()
        withSyntheticRecord(plaintext) { record ->
            val filesystem = IosSnapshotFileSystem()
            assertTrue(record.name in filesystem.list())
            assertTrue(isExcluded(record.legacyDirectory))
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
        }
    }

    @Test
    fun directReadProtectsMalformedLegacyWithoutPriorInventory(): Unit = runBlocking {
        val plaintext = malformedLegacy()
        withSyntheticRecord(plaintext) { record ->
            assertFailsWith<SnapshotProtectionException> {
                IosSnapshotFileSystem().read(record.name)
            }
            assertTrue(isExcluded(record.legacyDirectory))
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
        }
    }

    @Test
    fun damagedCurrentHeaderKeepsPrecedenceAndExcludesRetainedLegacy(): Unit = runBlocking {
        val plaintext = "{\"hostOnly\":\"synthetic-fixture\"}".encodeToByteArray()
        val damagedCurrent = "DAMAGED-PROTECTED-HEADER".encodeToByteArray()
        withSyntheticRecord(plaintext, damagedCurrent) { record ->
            val filesystem = IosSnapshotFileSystem()
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertTrue(record.name in filesystem.list())
            assertTrue(isExcluded(record.legacyDirectory))
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
            assertContentEquals(damagedCurrent, readBoundedSnapshotBytes(record.protectedPath, 1024))
        }
    }

    @Test
    fun oversizedLegacyIsExcludedBeforeBoundedReadRejectsIt(): Unit = runBlocking {
        val plaintext = ByteArray(MAX_PLAINTEXT_SNAPSHOT_BYTES + 1) { '!'.code.toByte() }
        withSyntheticRecord(plaintext) { record ->
            assertFailsWith<SnapshotProtectionException> {
                IosSnapshotFileSystem().read(record.name)
            }
            assertTrue(isExcluded(record.legacyDirectory))
            assertTrue(
                plaintext.contentEquals(readBoundedSnapshotBytes(record.legacyPath, plaintext.size)),
                "Oversized synthetic legacy bytes must remain unchanged",
            )
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
        }
    }

    @Test
    fun exclusionFailureIsNotSwallowedByInventoryAndDoesNotPreventExplicitDiscard(): Unit = runBlocking {
        val plaintext = "{\"hostOnly\":\"synthetic-fixture\"}".encodeToByteArray()
        withSyntheticRecord(plaintext) { record ->
            val failure = IllegalStateException("synthetic backup-exclusion failure")
            var legacyAttempts = 0
            val filesystem = IosSnapshotFileSystem(
                backupExcluder = { url ->
                    if (url.path == record.legacyDirectory) {
                        legacyAttempts++
                        throw failure
                    }
                    excludeSnapshotFromBackup(url)
                },
            )
            assertSame(failure, assertFailsWith<IllegalStateException> { filesystem.list() })
            assertSame(failure, assertFailsWith<IllegalStateException> { filesystem.read(record.name) })
            assertEquals(2, legacyAttempts)
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))

            filesystem.delete(record.name)
            assertFalse(fileManager.fileExistsAtPath(record.legacyPath))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
            assertEquals(2, legacyAttempts, "Discard must not retry failing backup protection")
        }
    }

    @Test
    fun retryReappliesLegacyExclusionInsteadOfCachingIt(): Unit = runBlocking {
        withSyntheticRecord(malformedLegacy()) { record ->
            var legacyAttempts = 0
            val filesystem = IosSnapshotFileSystem(
                backupExcluder = { url ->
                    if (url.path == record.legacyDirectory) legacyAttempts++
                    excludeSnapshotFromBackup(url)
                },
            )
            repeat(2) {
                assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            }
            assertEquals(2, legacyAttempts)
            assertTrue(isExcluded(record.legacyDirectory))
        }
    }

    @Test
    fun nativeExclusionRejectsMissingPathsRatherThanClaimingProtection() {
        val path = "${NSTemporaryDirectory()}parlor-absent-${NSUUID.UUID().UUIDString}"
        assertFalse(fileManager.fileExistsAtPath(path))
        assertFailsWith<IllegalStateException> {
            excludeSnapshotFromBackup(NSURL.fileURLWithPath(path, isDirectory = true))
        }
        assertFalse(fileManager.fileExistsAtPath(path))
    }

    @Test
    fun nativeExclusionIsReappliedAfterSyntheticDirectoryRecreation() {
        val path = "${NSTemporaryDirectory()}parlor-exclusion-${NSUUID.UUID().UUIDString}"
        val url = NSURL.fileURLWithPath(path, isDirectory = true)
        assertFalse(fileManager.fileExistsAtPath(path))
        try {
            repeat(2) {
                check(fileManager.createDirectoryAtPath(path, false, null, null))
                excludeSnapshotFromBackup(url)
                assertTrue(isExcluded(path))
                check(fileManager.removeItemAtPath(path, null))
            }
        } finally {
            removeOwnFileIfPresent(path)
        }
    }

    private data class Record(
        val name: String,
        val legacyDirectory: String,
        val protectedDirectory: String,
    ) {
        val legacyPath: String get() = "$legacyDirectory/$name"
        val protectedPath: String get() = "$protectedDirectory/$name"
    }

    private suspend fun withSyntheticRecord(
        legacyBytes: ByteArray,
        protectedBytes: ByteArray? = null,
        block: suspend (Record) -> Unit,
    ) {
        val name = "backup-test-${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val documents = requireNotNull(
            fileManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)?.path,
        )
        val support = requireNotNull(
            fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)?.path,
        )
        val record = Record(name, "$documents/snapshots", "$support/Parlor/snapshots")
        check(fileManager.createDirectoryAtPath(record.legacyDirectory, true, null, null))
        check(fileManager.createDirectoryAtPath(record.protectedDirectory, true, null, null))
        check(!fileManager.fileExistsAtPath(record.legacyPath))
        check(!fileManager.fileExistsAtPath(record.protectedPath))
        try {
            writeBytes(record.legacyPath, legacyBytes)
            if (protectedBytes != null) writeBytes(record.protectedPath, protectedBytes)
            block(record)
        } finally {
            removeOwnFileIfPresent(record.legacyPath)
            removeOwnFileIfPresent(record.protectedPath)
            legacyBytes.fill(0)
            protectedBytes?.fill(0)
        }
    }

    private fun isExcluded(path: String): Boolean {
        val key = requireNotNull(NSURLIsExcludedFromBackupKey)
        val values = requireNotNull(NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(key), null))
        return requireNotNull(values[key] as? NSNumber).boolValue
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        check(data.writeToFile(path, atomically = true))
    }

    private fun removeOwnFileIfPresent(path: String) {
        if (fileManager.fileExistsAtPath(path)) check(fileManager.removeItemAtPath(path, null))
    }

    private fun malformedLegacy(): ByteArray =
        "!\"hostOnly\":\"synthetic-fixture\"}".encodeToByteArray()

    private val fileManager = NSFileManager.defaultManager
}

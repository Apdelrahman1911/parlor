@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

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
import kotlin.test.assertTrue

/** Audit-only: invoke exclusively inside the root lane's disposable fresh simulator. */
class STC01LegacyBackupEligibilityAuditTest {
    @Test
    fun witnessDamagedProtectedHeaderRetainsBackupEligibleLegacyPlaintext() = runBlocking {
        withSyntheticRecord(protectedHeader = true) { name, legacy, protected, plaintext ->
            val fs = IosSnapshotFileSystem()
            assertTrue(name in fs.list())
            assertFailsWith<SnapshotProtectionException> { fs.read(name) }
            assertTrue(fileManager.fileExistsAtPath(legacy))
            assertContentEquals(plaintext, readBoundedSnapshotBytes(legacy, 1024))
            assertFalse(isExcluded(legacy))
            assertFalse(isExcluded(legacy.substringBeforeLast('/')))
            assertTrue(isExcluded(protected.substringBeforeLast('/')))
        }
    }

    @Test
    fun witnessDamagedLegacyPrefixSurvivesColdListWithoutBackupExclusion() = runBlocking {
        withSyntheticRecord(protectedHeader = false) { name, legacy, _, plaintext ->
            val fs = IosSnapshotFileSystem()
            assertTrue(name in fs.list())
            assertFailsWith<SnapshotProtectionException> { fs.read(name) }
            assertTrue(fileManager.fileExistsAtPath(legacy))
            assertContentEquals(plaintext, readBoundedSnapshotBytes(legacy, 1024))
            assertFalse(isExcluded(legacy))
            assertFalse(isExcluded(legacy.substringBeforeLast('/')))
        }
    }

    @Test
    fun regressionRetainedDamagedLegacyMustNotRemainBackupEligible() = runBlocking {
        withSyntheticRecord(protectedHeader = false) { name, legacy, _, _ ->
            val fs = IosSnapshotFileSystem()
            assertTrue(name in fs.list())
            assertTrue(
                !fileManager.fileExistsAtPath(legacy) ||
                    isExcluded(legacy) || isExcluded(legacy.substringBeforeLast('/')),
                "A retained legacy record must be quarantined from backup even when migration fails",
            )
        }
    }

    private suspend fun withSyntheticRecord(
        protectedHeader: Boolean,
        block: suspend (String, String, String, ByteArray) -> Unit,
    ) {
        val name = "audit-${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val documents = requireNotNull(fileManager.URLForDirectory(
            NSDocumentDirectory, NSUserDomainMask, null, false, null,
        )?.path)
        val support = requireNotNull(fileManager.URLForDirectory(
            NSApplicationSupportDirectory, NSUserDomainMask, null, true, null,
        )?.path)
        val legacyDirectory = "$documents/snapshots"
        val protectedDirectory = "$support/Parlor/snapshots"
        check(fileManager.createDirectoryAtPath(legacyDirectory, true, null, null))
        check(fileManager.createDirectoryAtPath(protectedDirectory, true, null, null))
        val legacy = "$legacyDirectory/$name"
        val protected = "$protectedDirectory/$name"
        val plaintext = ((if (protectedHeader) "{" else "!") +
            "\"hostOnly\":\"AUDIT_SYNTHETIC_NOT_PLAYER_DATA\"}").encodeToByteArray()
        try {
            writeBytes(legacy, plaintext)
            if (protectedHeader) writeBytes(protected, "DAMAGED-PROTECTED-HEADER".encodeToByteArray())
            block(name, legacy, protected, plaintext)
        } finally {
            if (fileManager.fileExistsAtPath(legacy)) check(fileManager.removeItemAtPath(legacy, null))
            if (fileManager.fileExistsAtPath(protected)) check(fileManager.removeItemAtPath(protected, null))
            plaintext.fill(0)
        }
    }

    private fun isExcluded(path: String): Boolean {
        val key = requireNotNull(NSURLIsExcludedFromBackupKey)
        val values = requireNotNull(NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(key), null))
        val result = requireNotNull(values[key] as? NSNumber) { "Missing native backup-exclusion result" }
        return result.boolValue
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        check(data.writeToFile(path, atomically = true))
    }

    private val fileManager = NSFileManager.defaultManager
}

@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionNone
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Negative native controls; neither case qualifies Complete protection or physical lock behavior. */
class IosCompleteSnapshotProtectionTest {
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

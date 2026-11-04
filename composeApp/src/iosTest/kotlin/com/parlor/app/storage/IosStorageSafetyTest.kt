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
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSData
import platform.Foundation.NSApplicationSupportDirectory
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

class IosStorageSafetyTest {
    @Test
    fun boundedReaderRejectsAfterReadingOnlyLimitPlusOneBytes() {
        val bytes = ByteArray(9) { it.toByte() }
        withTemporaryData(bytes) { path ->
            assertFailsWith<SnapshotProtectionException> {
                readBoundedSnapshotBytes(path, maximumBytes = 8)
            }
        }
    }

    @Test
    fun boundedReaderAcceptsAFileExactlyAtTheLimit() {
        val bytes = ByteArray(8) { (it + 1).toByte() }
        withTemporaryData(bytes) { path ->
            assertContentEquals(bytes, readBoundedSnapshotBytes(path, maximumBytes = 8))
        }
    }

    @Test
    fun keychainDataSupportsAnEmptyValueWithoutAddressingAMissingElement() {
        val data = createKeychainData(ByteArray(0))
        try {
            assertEquals(0L, CFDataGetLength(data))
        } finally {
            CFRelease(data)
        }
    }

    @Test
    fun commonCryptoRoundTripsAnEmptySnapshotPayload() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val iv = ByteArray(16) { (it + 17).toByte() }
        val ciphertext = iosAesCbc(kCCEncrypt, key, iv, ByteArray(0))
        try {
            assertContentEquals(
                ByteArray(0),
                iosAesCbc(kCCDecrypt, key, iv, ciphertext),
            )
        } finally {
            key.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    @Test
    fun currentDirectoryBackupExclusionFailurePrecedesProtectionAndDoesNotReadSecrets() {
        val name = "${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val path = protectedSnapshotPath(name)
        val directory = path.substringBeforeLast('/')
        val original = "PARSNAP".encodeToByteArray() + byteArrayOf(0, 16)
        val failure = IllegalStateException("synthetic current backup-exclusion failure")
        var exclusionAttempts = 0
        var keyReads = 0
        try {
            writeBytes(path, original)
            val filesystem = IosSnapshotFileSystem(
                snapshotKeyReader = { keyReads++; null },
                backupExcluder = { url ->
                    if (url.path == directory) {
                        exclusionAttempts++
                        throw failure
                    }
                    excludeSnapshotFromBackup(url)
                },
            )
            assertSame(failure, assertFailsWith<IllegalStateException> {
                runBlocking { filesystem.read(name) }
            })
            assertEquals(1, exclusionAttempts)
            assertEquals(0, keyReads)
            assertContentEquals(original, readBoundedSnapshotBytes(path, original.size))
            runBlocking { filesystem.delete(name) }
            assertFalse(fileManager.fileExistsAtPath(path))
            assertEquals(1, exclusionAttempts, "Discard must not retry failed exclusion")
        } finally {
            fileManager.removeItemAtPath(path, error = null)
        }
    }

    @Test
    fun rejectedCurrentReadStillExcludesItsDirectoryAndPreservesItsLastCopy() {
        val name = "${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val path = protectedSnapshotPath(name)
        val directory = path.substringBeforeLast('/')
        val original = "PARSNAP".encodeToByteArray() + byteArrayOf(0, 16)
        var exclusionAttempts = 0
        var keyReads = 0
        try {
            writeBytes(path, original)
            val filesystem = IosSnapshotFileSystem(
                snapshotKeyReader = { keyReads++; null },
                backupExcluder = { url ->
                    if (url.path == directory) exclusionAttempts++
                    excludeSnapshotFromBackup(url)
                },
            )
            assertFailsWith<SnapshotProtectionException> {
                runBlocking { filesystem.read(name) }
            }
            assertEquals(1, exclusionAttempts)
            val key = requireNotNull(NSURLIsExcludedFromBackupKey)
            val values = requireNotNull(NSURL.fileURLWithPath(directory).resourceValuesForKeys(listOf(key), null))
            assertTrue(requireNotNull(values[key] as? NSNumber).boolValue)
            assertEquals(0, keyReads)
            assertContentEquals(original, readBoundedSnapshotBytes(path, original.size))
            assertTrue(fileManager.fileExistsAtPath(path))
        } finally {
            fileManager.removeItemAtPath(path, error = null)
        }
    }

    @Test
    fun unreadableProtectedSnapshotPreservesMatchingLegacyPlaintext() {
        val name = "${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val protectedPath = protectedSnapshotPath(name)
        val legacyPath = legacySnapshotPath(name)
        val plaintext = "{\"hiddenRole\":\"secret\"}".encodeToByteArray()
        try {
            writeBytes(protectedPath, "PARSNAP".encodeToByteArray() + byteArrayOf(0, 16))
            writeBytes(legacyPath, plaintext)

            assertFailsWith<SnapshotProtectionException> {
                runBlocking {
                    IosSnapshotFileSystem().read(name)
                }
            }
            assertContentEquals(plaintext, readBoundedSnapshotBytes(legacyPath, plaintext.size))
            assertTrue(fileManager.fileExistsAtPath(protectedPath))
        } finally {
            fileManager.removeItemAtPath(protectedPath, error = null)
            fileManager.removeItemAtPath(legacyPath, error = null)
        }
    }

    @Test
    fun protectedSnapshotWithMissingKeyPreservesMatchingLegacyPlaintext() {
        val name = "${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val protectedPath = protectedSnapshotPath(name)
        val legacyPath = legacySnapshotPath(name)
        val plaintext = "{\"hiddenRole\":\"secret\"}".encodeToByteArray()
        try {
            writeBytes(protectedPath, structurallyValidProtectedRecord())
            writeBytes(legacyPath, plaintext)

            assertFailsWith<SnapshotProtectionException> {
                runBlocking {
                    IosSnapshotFileSystem(snapshotKeyReader = { null }).read(name)
                }
            }
            assertContentEquals(plaintext, readBoundedSnapshotBytes(legacyPath, plaintext.size))
            assertTrue(fileManager.fileExistsAtPath(protectedPath))
        } finally {
            fileManager.removeItemAtPath(protectedPath, error = null)
            fileManager.removeItemAtPath(legacyPath, error = null)
        }
    }

    @Test
    fun explicitDiscardRemovesBothCopiesWithoutInitializingProtection() {
        val name = "${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}"
        val protectedPath = protectedSnapshotPath(name)
        val legacyPath = legacySnapshotPath(name)
        val bytes = byteArrayOf(4, 5, 6)
        var protectionAttempts = 0
        try {
            writeBytes(protectedPath, bytes)
            writeBytes(legacyPath, bytes)
            val filesystem = IosSnapshotFileSystem(
                backupExcluder = {
                    protectionAttempts++
                    error("Discard must not initialize directory protection")
                },
            )

            runBlocking { filesystem.delete(name) }

            assertFalse(fileManager.fileExistsAtPath(protectedPath))
            assertFalse(fileManager.fileExistsAtPath(legacyPath))
            assertEquals(0, protectionAttempts)
        } finally {
            fileManager.removeItemAtPath(protectedPath, error = null)
            fileManager.removeItemAtPath(legacyPath, error = null)
        }
    }

    private fun structurallyValidProtectedRecord(): ByteArray =
        "PARSNAP".encodeToByteArray() +
            byteArrayOf(1, 16) +
            ByteArray(16) +
            ByteArray(16) +
            ByteArray(32)

    private inline fun withTemporaryData(bytes: ByteArray, block: (String) -> Unit) {
        val path = "${NSTemporaryDirectory()}parlor-${NSUUID.UUID().UUIDString}.bin"
        writeBytes(path, bytes)
        try {
            block(path)
        } finally {
            fileManager.removeItemAtPath(path, error = null)
        }
    }

    private fun protectedSnapshotPath(name: String): String {
        val applicationSupportUrl = requireNotNull(
            fileManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        )
        val applicationSupportPath = requireNotNull(applicationSupportUrl.path)
        return snapshotPath(applicationSupportPath, "Parlor/snapshots", name)
    }

    private fun legacySnapshotPath(name: String): String {
        val documentsUrl = requireNotNull(
            fileManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            ),
        )
        val documentsPath = requireNotNull(documentsUrl.path)
        return snapshotPath(documentsPath, "snapshots", name)
    }

    private fun snapshotPath(rootPath: String, directory: String, name: String): String {
        val path = "$rootPath/$directory"
        check(
            fileManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        )
        return "$path/$name"
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        val data = if (bytes.isEmpty()) {
            NSData.create(bytes = null, length = 0u)
        } else {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        check(data.writeToFile(path, atomically = true))
    }

    private companion object {
        val fileManager: NSFileManager = NSFileManager.defaultManager
    }
}

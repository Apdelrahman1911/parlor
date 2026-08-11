@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private inline fun withTemporaryData(bytes: ByteArray, block: (String) -> Unit) {
        val path = "${NSTemporaryDirectory()}parlor-${NSUUID.UUID().UUIDString}.bin"
        val data = if (bytes.isEmpty()) {
            NSData.create(bytes = null, length = 0u)
        } else {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        check(data.writeToFile(path, atomically = true))
        try {
            block(path)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}

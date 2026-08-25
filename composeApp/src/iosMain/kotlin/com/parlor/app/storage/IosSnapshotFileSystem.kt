@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotProtectionException
import com.parlor.storage.snapshot.SnapshotFileSystem
import com.parlor.storage.snapshot.isSafeSnapshotFileName
import com.parlor.storage.snapshot.requireSafeSnapshotFileName
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCKeySizeAES256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.writeToFile
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.memcpy

/**
 * iOS snapshot storage with device-only key material and authenticated
 * encryption.
 *
 * AES-256-CBC + HMAC-SHA-256 is used as encrypt-then-MAC with independent keys
 * stored in a non-synchronizing, ThisDeviceOnly Keychain item. Files live in
 * Application Support, are excluded from backup, and use complete iOS Data
 * Protection in addition to application-layer encryption.
 */
internal class IosSnapshotFileSystem(
    private val keychain: IosSnapshotKeychain = IosSnapshotKeychain(),
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SnapshotFileSystem {

    private val basePath: String by lazy {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val root = fileManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = error.ptr,
            ) ?: throw IllegalStateException("Application Support directory unavailable")
            val rootPath = root.path
                ?: throw IllegalStateException("Application Support directory has no path")
            val directory = "$rootPath/Parlor/snapshots"
            val protectionKey = NSFileProtectionKey
                ?: throw IllegalStateException("iOS file-protection key unavailable")
            val completeProtection = NSFileProtectionComplete
                ?: throw IllegalStateException("iOS complete file protection unavailable")
            error.value = null
            if (
                !fileManager.createDirectoryAtPath(
                    path = directory,
                    withIntermediateDirectories = true,
                    attributes = mapOf(protectionKey to completeProtection),
                    error = error.ptr,
                )
            ) {
                throw IllegalStateException("Couldn't create protected snapshot directory")
            }
            excludeFromBackup(NSURL.fileURLWithPath(directory, isDirectory = true))
            directory
        }
    }

    /** Location used by builds before authenticated storage shipped. */
    private val legacyBasePath: String by lazy {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val documents = fileManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = error.ptr,
            ) ?: throw IllegalStateException("Documents directory unavailable")
            val documentsPath = documents.path
                ?: throw IllegalStateException("Documents directory has no path")
            "$documentsPath/snapshots"
        }
    }

    override suspend fun read(name: String): ByteArray? = withContext(dispatcher) {
        requireSafeSnapshotFileName(name)
        readProtectedOrMigrate(name)
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(dispatcher) {
            requireSafeSnapshotFileName(name)
            if (bytes.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                throw SnapshotProtectionException()
            }
            writeProtected(name, encrypt(name, bytes))
        }
    }

    override suspend fun delete(name: String) {
        withContext(dispatcher) {
            requireSafeSnapshotFileName(name)
            val path = filePath(name)
            deletePathIfPresent(path)
            deleteLegacy(name)
        }
    }

    override suspend fun list(): List<String> = withContext(dispatcher) {
        // listUnfinished() runs on cold start, so upgrade old plaintext even
        // if the user does not open its resume tile. Migrate independently so
        // one damaged legacy record cannot hide every healthy saved game.
        val legacyNames = if (fileManager.fileExistsAtPath(legacyBasePath)) {
            listDirectory(legacyBasePath)
                .asSequence()
                .filter(::isSafeSnapshotFileName)
                .filter { it.endsWith(FileBackedSnapshotStore.SUFFIX) }
                .toList()
        } else {
            emptyList()
        }
        migrateSnapshotRecordsIndependently(legacyNames, ::readProtectedOrMigrate)
        val protectedNames = listDirectory(basePath).filter(::isSafeSnapshotFileName)
        (legacyNames + protectedNames).distinct()
    }

    private fun readProtectedOrMigrate(name: String): ByteArray? {
        val path = filePath(name)
        if (!fileManager.fileExistsAtPath(path)) return migrateLegacy(name)

        val protectedBytes = readBytes(path, MAX_PROTECTED_SNAPSHOT_BYTES)
        return when {
            protectedBytes.hasMagic() -> decrypt(name, protectedBytes).also {
                deleteLegacy(name)
            }
            protectedBytes.looksLikeLegacyJson() -> {
                if (protectedBytes.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                    throw SnapshotProtectionException()
                }
                writeProtected(name, encrypt(name, protectedBytes))
                deleteLegacy(name)
                protectedBytes
            }
            else -> throw SnapshotProtectionException()
        }
    }

    /**
     * Bounded one-way upgrade from `Documents/snapshots`.
     *
     * The old plaintext is deleted only after authenticated ciphertext with
     * complete Data Protection has been atomically installed.
     */
    private fun migrateLegacy(name: String): ByteArray? {
        val legacyPath = "$legacyBasePath/$name"
        if (!fileManager.fileExistsAtPath(legacyPath)) return null
        val plaintext = readBytes(legacyPath, MAX_PLAINTEXT_SNAPSHOT_BYTES)
        if (!plaintext.looksLikeLegacyJson()) throw SnapshotProtectionException()
        writeProtected(name, encrypt(name, plaintext))
        deletePathIfPresent(legacyPath)
        return plaintext
    }

    private fun deleteLegacy(name: String) {
        deletePathIfPresent("$legacyBasePath/$name")
    }

    private fun readBytes(path: String, maximumBytes: Int): ByteArray {
        return readBoundedSnapshotBytes(path, maximumBytes)
    }

    private fun listDirectory(path: String): List<String> =
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val contents = fileManager.contentsOfDirectoryAtPath(path, error.ptr)
                ?: throw IllegalStateException("Couldn't list protected snapshots")
            contents
                .mapNotNull { it as? String }
                .filterNot { it.endsWith(".tmp") }
        }

    private fun deletePathIfPresent(path: String) {
        if (!fileManager.fileExistsAtPath(path)) return
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            if (!fileManager.removeItemAtPath(path, error.ptr)) {
                throw IllegalStateException("Couldn't delete snapshot")
            }
        }
    }

    private fun encrypt(name: String, plaintext: ByteArray): ByteArray {
        val keyMaterial = keychain.loadOrCreate()
        val encryptionKey = keyMaterial.copyOfRange(0, AES_KEY_BYTES)
        val macKey = keyMaterial.copyOfRange(AES_KEY_BYTES, IosSnapshotKeychain.KEY_BYTES)
        return try {
            val iv = secureRandomBytes(IV_BYTES)
            val ciphertext = iosAesCbc(kCCEncrypt, encryptionKey, iv, plaintext)
            val header = header()
            val authenticated = header + name.encodeToByteArray() + iv + ciphertext
            val tag = hmacSha256(macKey, authenticated)
            header + iv + ciphertext + tag
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SnapshotProtectionException) {
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            throw SnapshotProtectionException(cause = failure)
        } finally {
            encryptionKey.fill(0)
            macKey.fill(0)
            keyMaterial.fill(0)
        }
    }

    private fun decrypt(name: String, protectedBytes: ByteArray): ByteArray {
        try {
            var offset = MAGIC.size
            val version = protectedBytes[offset++]
            val ivSize = protectedBytes[offset++].toInt() and UNSIGNED_BYTE_MASK
            if (
                version != FORMAT_VERSION ||
                ivSize != IV_BYTES ||
                protectedBytes.size < offset + ivSize + MAC_BYTES + AES_BLOCK_BYTES
            ) {
                throw SnapshotProtectionException()
            }
            val iv = protectedBytes.copyOfRange(offset, offset + ivSize)
            offset += ivSize
            val ciphertextEnd = protectedBytes.size - MAC_BYTES
            val ciphertext = protectedBytes.copyOfRange(offset, ciphertextEnd)
            val receivedTag = protectedBytes.copyOfRange(ciphertextEnd, protectedBytes.size)
            if (ciphertext.size % AES_BLOCK_BYTES != 0) throw SnapshotProtectionException()

            val keyMaterial = keychain.readExisting() ?: throw SnapshotProtectionException()
            val encryptionKey = keyMaterial.copyOfRange(0, AES_KEY_BYTES)
            val macKey = keyMaterial.copyOfRange(AES_KEY_BYTES, IosSnapshotKeychain.KEY_BYTES)
            return try {
                val authenticated =
                    protectedBytes.copyOfRange(0, MAGIC.size + HEADER_BYTES) +
                        name.encodeToByteArray() +
                        iv +
                        ciphertext
                val expectedTag = hmacSha256(macKey, authenticated)
                try {
                    if (!constantTimeEquals(receivedTag, expectedTag)) {
                        throw SnapshotProtectionException()
                    }
                } finally {
                    expectedTag.fill(0)
                }
                enforceSnapshotPlaintextLimit(
                    iosAesCbc(kCCDecrypt, encryptionKey, iv, ciphertext),
                )
            } finally {
                encryptionKey.fill(0)
                macKey.fill(0)
                keyMaterial.fill(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SnapshotProtectionException) {
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            throw SnapshotProtectionException(cause = failure)
        }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        ByteArray(MAC_BYTES).also { mac ->
            key.usePinned { keyPinned ->
                data.usePinned { dataPinned ->
                    mac.usePinned { macPinned ->
                        CCHmac(
                            algorithm = kCCHmacAlgSHA256,
                            key = keyPinned.addressOf(0),
                            keyLength = key.size.toULong(),
                            data = dataPinned.addressOf(0),
                            dataLength = data.size.toULong(),
                            macOut = macPinned.addressOf(0),
                        )
                    }
                }
            }
        }

    private fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size).also { bytes ->
            val status = bytes.usePinned { pinned ->
                SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
            }
            if (status != errSecSuccess) {
                bytes.fill(0)
                throw IllegalStateException("Secure random generation failed")
            }
        }

    private fun writeProtected(name: String, protectedBytes: ByteArray) {
        val path = filePath(name)
        val data = protectedBytes.toNSData()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete
            if (!data.writeToFile(path, options = options, error = error.ptr)) {
                throw IllegalStateException("Couldn't atomically write protected snapshot")
            }
        }
        excludeFromBackup(NSURL.fileURLWithPath(path))
    }

    private fun excludeFromBackup(url: NSURL) {
        val key = NSURLIsExcludedFromBackupKey
            ?: throw IllegalStateException("iOS backup-exclusion key unavailable")
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            if (!url.setResourceValue(true, forKey = key, error = error.ptr)) {
                throw IllegalStateException("Couldn't exclude protected snapshot from backup")
            }
        }
    }

    private fun filePath(name: String): String = "$basePath/$name"

    private fun header(): ByteArray =
        MAGIC + byteArrayOf(FORMAT_VERSION, IV_BYTES.toByte())

    private fun ByteArray.hasMagic(): Boolean =
        size >= MAGIC.size + HEADER_BYTES && MAGIC.indices.all { this[it] == MAGIC[it] }

    private fun ByteArray.looksLikeLegacyJson(): Boolean =
        firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte()

    private companion object {
        const val UNSIGNED_BYTE_MASK = 0xff
        const val AES_KEY_BYTES = 32
        const val AES_BLOCK_BYTES = 16
        const val IV_BYTES = 16
        const val MAC_BYTES = 32
        const val HEADER_BYTES = 2
        const val MAX_PROTECTED_SNAPSHOT_BYTES = MAX_PLAINTEXT_SNAPSHOT_BYTES + 1024
        const val FORMAT_VERSION: Byte = 1

        val MAGIC: ByteArray = "PARSNAP".encodeToByteArray()
    }
}

/**
 * Reads at most one byte beyond the accepted limit, so an oversized or
 * attacker-modified file can never make Foundation allocate its full length
 * before Parlor rejects it.
 */
internal fun readBoundedSnapshotBytes(path: String, maximumBytes: Int): ByteArray {
    require(maximumBytes >= 0) { "Snapshot byte limit must be non-negative" }
    val handle = NSFileHandle.fileHandleForReadingAtPath(path)
        ?: throw IllegalStateException("Couldn't open protected snapshot")
    val data = try {
        handle.readDataOfLength(maximumBytes.toULong() + 1uL)
    } finally {
        handle.closeFile()
    }
    if (data.length > maximumBytes.toULong()) throw SnapshotProtectionException()
    return data.toByteArray()
}

/** CommonCrypto wrapper that passes a null input pointer for an empty payload. */
internal fun iosAesCbc(
    operation: UInt,
    key: ByteArray,
    iv: ByteArray,
    input: ByteArray,
): ByteArray = memScoped {
    val output = ByteArray(input.size + kCCBlockSizeAES128.toInt())
    val moved = alloc<ULongVar>()
    moved.value = 0u
    val status = key.usePinned { keyPinned ->
        iv.usePinned { ivPinned ->
            input.usePinned { inputPinned ->
                output.usePinned { outputPinned ->
                    CCCrypt(
                        op = operation,
                        alg = kCCAlgorithmAES,
                        options = kCCOptionPKCS7Padding,
                        key = keyPinned.addressOf(0),
                        keyLength = kCCKeySizeAES256.toULong(),
                        iv = ivPinned.addressOf(0),
                        dataIn = if (input.isEmpty()) null else inputPinned.addressOf(0),
                        dataInLength = input.size.toULong(),
                        dataOut = outputPinned.addressOf(0),
                        dataOutAvailable = output.size.toULong(),
                        dataOutMoved = moved.ptr,
                    )
                }
            }
        }
    }
    if (status != kCCSuccess) {
        output.fill(0)
        throw SnapshotProtectionException()
    }
    output.copyOf(moved.value.toInt()).also { output.fill(0) }
}

private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
    var difference = first.size xor second.size
    val size = minOf(first.size, second.size)
    for (index in 0 until size) {
        difference = difference or (first[index].toInt() xor second[index].toInt())
    }
    return difference == 0
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    return ByteArray(length).also { result ->
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0u)
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

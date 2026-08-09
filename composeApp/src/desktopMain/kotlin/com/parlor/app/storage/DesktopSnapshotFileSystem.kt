package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import com.parlor.storage.snapshot.SnapshotFileSystem
import com.parlor.storage.snapshot.isSafeSnapshotFileName
import com.parlor.storage.snapshot.requireSafeSnapshotFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop development snapshot storage.
 *
 * Snapshots use AES-256-GCM with a random per-install key kept outside the
 * snapshot directory. On POSIX filesystems the key is owner-readable/writable
 * only. Desktop is not a first-release target: without an OS credential-store
 * integration, another process running as the same user can still read the
 * key. This is materially safer than plaintext but not equivalent to Android
 * Keystore or iOS Keychain.
 */
class DesktopSnapshotFileSystem(
    private val baseDir: Path = Path.of(System.getProperty("user.home"), ".parlor", "snapshots"),
    private val keyPath: Path = baseDir.resolveSibling(KEY_FILE_NAME),
) : SnapshotFileSystem {

    init {
        Files.createDirectories(baseDir)
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        requireSafeSnapshotFileName(name)
        val path = baseDir.resolve(name)
        if (!Files.exists(path)) return@withContext null
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_PROTECTED_SNAPSHOT_BYTES) {
            throw SnapshotProtectionException()
        }
        val protectedBytes = Files.readAllBytes(path)
        when {
            protectedBytes.hasMagic() -> decrypt(name, protectedBytes)
            protectedBytes.looksLikeLegacyJson() -> {
                if (protectedBytes.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                    throw SnapshotProtectionException()
                }
                atomicWrite(path, encrypt(name, protectedBytes))
                protectedBytes
            }
            else -> throw SnapshotProtectionException()
        }
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            requireSafeSnapshotFileName(name)
            if (bytes.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                throw SnapshotProtectionException()
            }
            val target = baseDir.resolve(name)
            atomicWrite(target, encrypt(name, bytes))
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            requireSafeSnapshotFileName(name)
            Files.deleteIfExists(baseDir.resolve(name))
        }
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        if (!Files.isDirectory(baseDir)) return@withContext emptyList()
        Files.list(baseDir).use { stream ->
            stream
                .map { it.fileName.toString() }
                .filter(::isSafeSnapshotFileName)
                .filter { !it.endsWith(".tmp") }
                .toList()
        }
    }

    private fun encrypt(name: String, plaintext: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, snapshotKey())
            cipher.updateAAD(aad(name))
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            check(iv.size == GCM_IV_BYTES)
            return ByteArray(MAGIC.size + HEADER_BYTES + iv.size + ciphertext.size).also { result ->
                var offset = 0
                MAGIC.copyInto(result, offset)
                offset += MAGIC.size
                result[offset++] = FORMAT_VERSION
                result[offset++] = iv.size.toByte()
                iv.copyInto(result, offset)
                offset += iv.size
                ciphertext.copyInto(result, offset)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure is SnapshotProtectionException) throw failure
            throw SnapshotProtectionException(cause = failure)
        }
    }

    private fun decrypt(name: String, protectedBytes: ByteArray): ByteArray {
        try {
            var offset = MAGIC.size
            val version = protectedBytes[offset++]
            val ivSize = protectedBytes[offset++].toInt() and 0xff
            if (
                version != FORMAT_VERSION ||
                ivSize != GCM_IV_BYTES ||
                protectedBytes.size <= offset + ivSize + GCM_TAG_BYTES
            ) {
                throw SnapshotProtectionException()
            }
            val iv = protectedBytes.copyOfRange(offset, offset + ivSize)
            offset += ivSize
            val ciphertext = protectedBytes.copyOfRange(offset, protectedBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, snapshotKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad(name))
            return cipher.doFinal(ciphertext)
        } catch (failure: AEADBadTagException) {
            throw SnapshotProtectionException(cause = failure)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure is SnapshotProtectionException) throw failure
            throw SnapshotProtectionException(cause = failure)
        }
    }

    @Synchronized
    private fun snapshotKey(): SecretKey {
        if (Files.exists(keyPath)) return keyFromDisk()

        keyPath.parent?.let { Files.createDirectories(it) }
        val lockPath = keyPath.resolveSibling("$KEY_FILE_NAME.lock")
        FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
            channel.lock().use {
                if (Files.exists(keyPath)) return keyFromDisk()
                createSnapshotKey()
            }
        }
        return keyFromDisk()
    }

    private fun createSnapshotKey() {
        val keyBytes = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val temporaryKey = keyPath.resolveSibling("$KEY_FILE_NAME.tmp")
        try {
            createOwnerOnlyFile(temporaryKey)
            FileOutputStream(temporaryKey.toFile()).use { output ->
                output.write(keyBytes)
                output.fd.sync()
            }
            try {
                Files.move(temporaryKey, keyPath, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryKey, keyPath)
            }
        } finally {
            Files.deleteIfExists(temporaryKey)
            keyBytes.fill(0)
        }
    }

    private fun keyFromDisk(): SecretKey {
        val keyBytes = Files.readAllBytes(keyPath)
        if (keyBytes.size != KEY_BYTES) throw SnapshotProtectionException()
        return try {
            SecretKeySpec(keyBytes, "AES")
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun createOwnerOnlyFile(path: Path) {
        Files.deleteIfExists(path)
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            val permissions = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
            Files.createFile(path, PosixFilePermissions.asFileAttribute(permissions))
        } else {
            Files.createFile(path)
        }
    }

    private fun aad(name: String): ByteArray =
        MAGIC + name.toByteArray(StandardCharsets.UTF_8)

    private fun atomicWrite(target: Path, protectedBytes: ByteArray) {
        val tmp = baseDir.resolve("${target.fileName}.tmp")
        try {
            FileOutputStream(tmp.toFile()).use { output ->
                output.write(protectedBytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    tmp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun ByteArray.hasMagic(): Boolean =
        size >= MAGIC.size + HEADER_BYTES && MAGIC.indices.all { this[it] == MAGIC[it] }

    private fun ByteArray.looksLikeLegacyJson(): Boolean =
        firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte()

    private companion object {
        const val KEY_FILE_NAME = "snapshot-key-v1.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val GCM_TAG_BITS = GCM_TAG_BYTES * 8
        const val HEADER_BYTES = 2
        const val MAX_PLAINTEXT_SNAPSHOT_BYTES = 8 * 1024 * 1024
        const val MAX_PROTECTED_SNAPSHOT_BYTES = MAX_PLAINTEXT_SNAPSHOT_BYTES + 1024
        const val FORMAT_VERSION: Byte = 1

        val MAGIC: ByteArray = "PARSNAP".toByteArray(StandardCharsets.US_ASCII)
    }
}

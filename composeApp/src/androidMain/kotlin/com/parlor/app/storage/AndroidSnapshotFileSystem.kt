package com.parlor.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotProtectionException
import com.parlor.storage.snapshot.SnapshotFileSystem
import com.parlor.storage.snapshot.isSafeSnapshotFileName
import com.parlor.storage.snapshot.requireSafeSnapshotFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android snapshot storage with authenticated encryption.
 *
 * A non-exportable AES-256 key lives in Android Keystore and each write gets a
 * fresh GCM nonce. The file name is authenticated as associated data so an
 * encrypted snapshot cannot be swapped under another session id. Ciphertext
 * lives in `noBackupFilesDir`; this is defense in depth over the app's
 * `allowBackup=false` manifest policy.
 */
class AndroidSnapshotFileSystem(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SnapshotFileSystem {

    private val baseDir: File by lazy {
        File(context.noBackupFilesDir, DIRECTORY).also { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Couldn't create protected snapshot directory")
            }
            check(directory.isDirectory) { "Protected snapshot path is not a directory" }
        }
    }

    /** Location used by builds before authenticated storage shipped. */
    private val legacyDir: File
        get() = File(context.filesDir, DIRECTORY)

    override suspend fun read(name: String): ByteArray? = withContext(ioDispatcher) {
        requireSafeSnapshotFileName(name)
        readProtectedOrMigrate(name)
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(ioDispatcher) {
            requireSafeSnapshotFileName(name)
            if (bytes.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                throw SnapshotProtectionException()
            }
            val file = File(baseDir, name)
            atomicWrite(file, encrypt(name, bytes))
        }
    }

    override suspend fun delete(name: String) {
        withContext(ioDispatcher) {
            requireSafeSnapshotFileName(name)
            val protected = File(baseDir, name)
            if (protected.exists() && !protected.delete()) {
                throw IllegalStateException("Couldn't remove protected snapshot")
            }
            deleteLegacy(name)
        }
    }

    override suspend fun list(): List<String> = withContext(ioDispatcher) {
        // listUnfinished() is called on cold start, so upgrades remove old
        // plaintext even when the user never opens that resume tile. Migrate
        // records independently: a corrupt legacy save remains visible for
        // explicit recovery instead of hiding every healthy save.
        val legacyNames = legacyDir
            .takeIf(File::isDirectory)
            ?.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.map(File::getName)
            ?.filter(::isSafeSnapshotFileName)
            ?.filter { it.endsWith(FileBackedSnapshotStore.SUFFIX) }
            ?.toList()
            .orEmpty()
        migrateSnapshotRecordsIndependently(legacyNames, ::readProtectedOrMigrate)

        val protectedNames = baseDir.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.map(File::getName)
            ?.filter(::isSafeSnapshotFileName)
            ?.filterNot { it.endsWith(".tmp") }
            ?.toList()
            .orEmpty()
        (legacyNames + protectedNames).distinct()
    }

    private fun readProtectedOrMigrate(name: String): ByteArray? {
        val file = File(baseDir, name)
        if (!file.exists()) return migrateLegacy(name)
        if (!file.isFile || file.length() > MAX_PROTECTED_SNAPSHOT_BYTES) {
            throw SnapshotProtectionException()
        }
        val protectedBytes = file.readBytes()
        return when {
            protectedBytes.hasMagic() -> decrypt(name, protectedBytes).also {
                deleteLegacy(name)
            }
            protectedBytes.looksLikeLegacyJson() -> {
                // One-way migration for snapshots written by pre-protection
                // builds. The plaintext bytes exist only long enough to return
                // this read and are atomically replaced before it completes.
                if (protectedBytes.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                    throw SnapshotProtectionException()
                }
                atomicWrite(file, encrypt(name, protectedBytes))
                deleteLegacy(name)
                protectedBytes
            }
            else -> throw SnapshotProtectionException()
        }
    }

    /**
     * Bounded one-way upgrade from `filesDir/snapshots`.
     *
     * The old plaintext is deleted only after authenticated ciphertext has
     * been durably and atomically installed in no-backup storage.
     */
    private fun migrateLegacy(name: String): ByteArray? {
        val legacy = File(legacyDir, name)
        if (!legacy.exists()) return null
        if (!legacy.isFile || legacy.length() > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
            throw SnapshotProtectionException()
        }
        val plaintext = legacy.readBytes()
        if (!plaintext.looksLikeLegacyJson()) throw SnapshotProtectionException()
        atomicWrite(File(baseDir, name), encrypt(name, plaintext))
        if (!legacy.delete() && legacy.exists()) {
            // The protected winner is already durable; retaining the old
            // plaintext would violate the at-rest guarantee, so surface the
            // cleanup failure instead of silently continuing.
            throw IllegalStateException("Couldn't remove legacy snapshot")
        }
        return plaintext
    }

    private fun deleteLegacy(name: String) {
        val legacy = File(legacyDir, name)
        if (legacy.exists() && !legacy.delete()) {
            throw IllegalStateException("Couldn't remove legacy snapshot")
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
        } catch (failure: SnapshotProtectionException) {
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            throw SnapshotProtectionException(cause = failure)
        }
    }

    private fun decrypt(name: String, protectedBytes: ByteArray): ByteArray {
        try {
            var offset = MAGIC.size
            val version = protectedBytes[offset++]
            val ivSize = protectedBytes[offset++].toInt() and UNSIGNED_BYTE_MASK
            if (
                version != FORMAT_VERSION ||
                ivSize != GCM_IV_BYTES ||
                !hasCompleteGcmPayload(
                    recordSize = protectedBytes.size,
                    payloadOffset = offset,
                    nonceBytes = ivSize,
                    tagBytes = GCM_TAG_BYTES,
                )
            ) {
                throw SnapshotProtectionException()
            }
            val iv = protectedBytes.copyOfRange(offset, offset + ivSize)
            offset += ivSize
            val ciphertext = protectedBytes.copyOfRange(offset, protectedBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, snapshotKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad(name))
            return cipher.doFinal(ciphertext).also { plaintext ->
                if (plaintext.size > MAX_PLAINTEXT_SNAPSHOT_BYTES) {
                    plaintext.fill(0)
                    throw SnapshotProtectionException()
                }
            }
        } catch (failure: AEADBadTagException) {
            throw SnapshotProtectionException(cause = failure)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SnapshotProtectionException) {
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            throw SnapshotProtectionException(cause = failure)
        }
    }

    private fun snapshotKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun aad(name: String): ByteArray =
        MAGIC + name.toByteArray(StandardCharsets.UTF_8)

    private fun atomicWrite(target: File, protectedBytes: ByteArray) {
        val tmp = File(baseDir, "${target.name}.tmp")
        try {
            FileOutputStream(tmp).use { output ->
                output.write(protectedBytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun ByteArray.hasMagic(): Boolean =
        size >= MAGIC.size + HEADER_BYTES && MAGIC.indices.all { this[it] == MAGIC[it] }

    private fun ByteArray.looksLikeLegacyJson(): Boolean =
        firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte()

    private companion object {
        const val DIRECTORY = "snapshots"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.parlor.app.snapshot.aes-gcm.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val UNSIGNED_BYTE_MASK = 0xff
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

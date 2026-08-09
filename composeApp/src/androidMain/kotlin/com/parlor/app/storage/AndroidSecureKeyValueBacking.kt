package com.parlor.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.parlor.storage.secure.SecureKeyValueBacking
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
 * Small device-bound secret store for resumable-session credentials.
 *
 * Values are encrypted with a non-exportable Android Keystore AES-256 key and
 * written atomically beneath [Context.getNoBackupFilesDir]. The logical key is
 * authenticated as GCM associated data, so ciphertext cannot be moved between
 * records. This backing deliberately supports only small, filename-safe keys
 * and values; it is not a general file store.
 */
internal class AndroidSecureKeyValueBacking(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecureKeyValueBacking {
    private val applicationContext = context.applicationContext

    private val baseDir: File by lazy {
        File(applicationContext.noBackupFilesDir, DIRECTORY).also { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                error("Couldn't create secure credential directory")
            }
            check(directory.isDirectory) { "Secure credential path is not a directory" }
        }
    }

    override suspend fun put(key: String, value: ByteArray) = withContext(ioDispatcher) {
        requireValidKey(key)
        require(value.size <= MAX_VALUE_BYTES) { "Secure value exceeds $MAX_VALUE_BYTES bytes" }
        atomicWrite(fileFor(key), encrypt(key, value))
    }

    override suspend fun get(key: String): ByteArray? = withContext(ioDispatcher) {
        requireValidKey(key)
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        check(file.isFile && file.length() <= MAX_PROTECTED_BYTES) {
            "Invalid secure credential record"
        }
        decrypt(key, file.readBytes())
    }

    override suspend fun remove(key: String) = withContext(ioDispatcher) {
        requireValidKey(key)
        val file = fileFor(key)
        if (file.exists() && !file.delete()) {
            error("Couldn't remove secure credential record")
        }
        val temporary = temporaryFileFor(key)
        if (temporary.exists() && !temporary.delete()) {
            error("Couldn't remove temporary credential record")
        }
    }

    private fun fileFor(key: String): File = File(baseDir, "$key.bin")

    private fun temporaryFileFor(key: String): File = File(baseDir, "$key.tmp")

    private fun encrypt(key: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(associatedData(key))
        val ciphertext = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        check(nonce.size == GCM_NONCE_BYTES)
        return ByteArray(MAGIC.size + HEADER_BYTES + nonce.size + ciphertext.size).also { output ->
            var offset = 0
            MAGIC.copyInto(output, offset)
            offset += MAGIC.size
            output[offset++] = FORMAT_VERSION
            output[offset++] = nonce.size.toByte()
            nonce.copyInto(output, offset)
            offset += nonce.size
            ciphertext.copyInto(output, offset)
        }
    }

    private fun decrypt(key: String, protectedBytes: ByteArray): ByteArray {
        try {
            require(protectedBytes.size >= MAGIC.size + HEADER_BYTES + GCM_NONCE_BYTES + GCM_TAG_BYTES)
            require(MAGIC.indices.all { protectedBytes[it] == MAGIC[it] })
            var offset = MAGIC.size
            require(protectedBytes[offset++] == FORMAT_VERSION)
            val nonceSize = protectedBytes[offset++].toInt() and UNSIGNED_BYTE_MASK
            require(nonceSize == GCM_NONCE_BYTES)
            require(protectedBytes.size > offset + nonceSize + GCM_TAG_BYTES)
            val nonce = protectedBytes.copyOfRange(offset, offset + nonceSize)
            offset += nonceSize
            val ciphertext = protectedBytes.copyOfRange(offset, protectedBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(associatedData(key))
            return cipher.doFinal(ciphertext).also {
                require(it.size <= MAX_VALUE_BYTES)
            }
        } catch (failure: AEADBadTagException) {
            throw IllegalStateException("Secure credential authentication failed", failure)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalStateException) {
            throw failure
        } catch (failure: Exception) {
            throw IllegalStateException("Secure credential record is invalid", failure)
        }
    }

    private fun encryptionKey(): SecretKey {
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

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = temporaryFileFor(target.name.removeSuffix(".bin"))
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun associatedData(key: String): ByteArray =
        MAGIC + key.toByteArray(StandardCharsets.UTF_8)

    private fun requireValidKey(key: String) {
        require(key.length in 1..MAX_KEY_LENGTH && key.all(::isSafeKeyCharacter)) {
            "Secure-storage key is invalid"
        }
    }

    private fun isSafeKeyCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '.' || character == '_' || character == '-'

    private companion object {
        const val DIRECTORY = "secure-credentials"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.parlor.app.secure-credentials.aes-gcm.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val UNSIGNED_BYTE_MASK = 0xff
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val GCM_TAG_BITS = GCM_TAG_BYTES * 8
        const val HEADER_BYTES = 2
        const val MAX_KEY_LENGTH = 64
        const val MAX_VALUE_BYTES = 16 * 1024
        const val MAX_PROTECTED_BYTES = MAX_VALUE_BYTES + 1024
        const val FORMAT_VERSION: Byte = 1
        val MAGIC: ByteArray = "PARSEC1".toByteArray(StandardCharsets.US_ASCII)
    }
}

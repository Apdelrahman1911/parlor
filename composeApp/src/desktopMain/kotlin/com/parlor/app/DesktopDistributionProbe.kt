package com.parlor.app

import org.jetbrains.skia.Surface
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Opt-in packaged-runtime probe. No DI, player storage, LAN socket, role, or user profile is opened. */
internal fun verifyDesktopDistribution() {
    check(Runtime.version().feature() == DISTRIBUTION_JDK)
    val key = KeyGenerator.getInstance("AES").apply { init(AES_BITS) }.generateKey()
    val nonce = ByteArray(GCM_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val clear = "parlor-runtime-probe".encodeToByteArray()
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
    val sealed = cipher.doFinal(clear)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
    check(cipher.doFinal(sealed).contentEquals(clear))
    // The EC provider is loaded dynamically, so jdeps alone cannot prove it is bundled.
    check(KeyPairGenerator.getInstance("EC").generateKeyPair().public.encoded.isNotEmpty())
    Surface.makeRasterN32Premul(2, 2).use { surface -> check(surface.width == 2) }
    println("PARLOR_DISTRIBUTION_SMOKE_OK")
}

private const val DISTRIBUTION_JDK = 21
private const val AES_BITS = 256
private const val GCM_TAG_BITS = 128
private const val GCM_NONCE_BYTES = 12

package com.parlor.networking.security

import java.security.SecureRandom

private val secureRandom: SecureRandom = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "size must be positive" }
    return ByteArray(size).also(secureRandom::nextBytes)
}


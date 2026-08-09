package com.parlor.networking.security

/** Platform CSPRNG used for protocol capabilities and security-sensitive entropy. */
internal expect fun secureRandomBytes(size: Int): ByteArray

object SecureIds {
    /** 128-bit identifier suitable for sessions, commands, and messages. */
    fun id128(): String = secureRandomBytes(16).toHex()

    /**
     * Uniform 64-bit value from the platform CSPRNG.
     *
     * Multiplayer hosts use this for hidden-role gameplay seeds. The seed is
     * retained only in host state and must never be substituted with a public
     * room code, start nonce, or other peer-observable value.
     */
    fun randomLong(): Long = secureRandomBytes(Long.SIZE_BYTES).toLongBigEndian()

    /** 256-bit bearer capability used only for same-host rejoin. */
    fun rejoinToken256(): String = secureRandomBytes(32).toHex()

    /** Uniform CSPRNG-backed characters, without modulo bias. */
    fun randomCharacters(length: Int, alphabet: String): String {
        require(length > 0) { "length must be positive" }
        require(alphabet.length in 2..128) { "alphabet must contain 2..128 characters" }
        val acceptedRange = 256 - (256 % alphabet.length)
        return buildString(length) {
            while (this.length < length) {
                secureRandomBytes(length).forEach { byte ->
                    val value = byte.toInt() and 0xff
                    if (value < acceptedRange && this.length < length) {
                        append(alphabet[value % alphabet.length])
                    }
                }
            }
        }
    }
}

internal fun ByteArray.toLongBigEndian(): Long {
    require(size == Long.SIZE_BYTES) { "A Long requires exactly ${Long.SIZE_BYTES} bytes" }
    return fold(0L) { value, byte ->
        (value shl Byte.SIZE_BITS) or (byte.toLong() and 0xffL)
    }
}

private val HEX = "0123456789abcdef".toCharArray()

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (value in this@toHex) {
        val unsigned = value.toInt() and 0xff
        append(HEX[unsigned ushr 4])
        append(HEX[unsigned and 0x0f])
    }
}

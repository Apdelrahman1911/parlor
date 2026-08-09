package com.parlor.networking.security

/** Platform CSPRNG used for protocol capabilities and security-sensitive entropy. */
internal expect fun secureRandomBytes(size: Int): ByteArray

object SecureIds {
    /** 128-bit identifier suitable for sessions, commands, and messages. */
    fun id128(): String = secureRandomBytes(ID_128_BYTES).toHex()

    /**
     * Uniform 64-bit value from the platform CSPRNG.
     *
     * Multiplayer hosts use this for hidden-role gameplay seeds. The seed is
     * retained only in host state and must never be substituted with a public
     * room code, start nonce, or other peer-observable value.
     */
    fun randomLong(): Long = secureRandomBytes(Long.SIZE_BYTES).toLongBigEndian()

    /** 256-bit bearer capability used only for same-host rejoin. */
    fun rejoinToken256(): String = secureRandomBytes(TOKEN_256_BYTES).toHex()

    /** Uniform CSPRNG-backed characters, without modulo bias. */
    fun randomCharacters(length: Int, alphabet: String): String {
        require(length > 0) { "length must be positive" }
        require(alphabet.length in MIN_ALPHABET_SIZE..MAX_ALPHABET_SIZE) {
            "alphabet must contain $MIN_ALPHABET_SIZE..$MAX_ALPHABET_SIZE characters"
        }
        val acceptedRange = BYTE_VALUE_COUNT - (BYTE_VALUE_COUNT % alphabet.length)
        return buildString(length) {
            while (this.length < length) {
                secureRandomBytes(length).forEach { byte ->
                    val value = byte.toInt() and UNSIGNED_BYTE_MASK
                    if (value < acceptedRange && this.length < length) {
                        append(alphabet[value % alphabet.length])
                    }
                }
            }
        }
    }

    private const val ID_128_BYTES = 16
    private const val TOKEN_256_BYTES = 32
    private const val MIN_ALPHABET_SIZE = 2
    private const val MAX_ALPHABET_SIZE = 128
    private const val BYTE_VALUE_COUNT = 256
}

internal fun ByteArray.toLongBigEndian(): Long {
    require(size == Long.SIZE_BYTES) { "A Long requires exactly ${Long.SIZE_BYTES} bytes" }
    return fold(0L) { value, byte ->
        (value shl Byte.SIZE_BITS) or (byte.toLong() and UNSIGNED_BYTE_MASK_LONG)
    }
}

private val HEX = "0123456789abcdef".toCharArray()

private fun ByteArray.toHex(): String = buildString(size * HEX_CHARACTERS_PER_BYTE) {
    for (value in this@toHex) {
        val unsigned = value.toInt() and UNSIGNED_BYTE_MASK
        append(HEX[unsigned ushr HALF_BYTE_BITS])
        append(HEX[unsigned and HALF_BYTE_MASK])
    }
}

private const val UNSIGNED_BYTE_MASK = 0xff
private const val UNSIGNED_BYTE_MASK_LONG = 0xffL
private const val HEX_CHARACTERS_PER_BYTE = 2
private const val HALF_BYTE_BITS = 4
private const val HALF_BYTE_MASK = 0x0f

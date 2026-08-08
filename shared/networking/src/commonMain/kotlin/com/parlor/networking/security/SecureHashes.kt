package com.parlor.networking.security

/** Platform SHA-256 primitive. */
internal expect fun platformSha256(input: ByteArray): ByteArray

/** Cryptographic digest helpers used for bearer-capability verification. */
object SecureHashes {
    fun sha256(input: ByteArray): ByteArray = platformSha256(input)

    fun sha256Utf8(input: String): ByteArray {
        val bytes = input.encodeToByteArray()
        return try {
            sha256(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /** Constant-work comparison for equal-length secret digests. */
    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        var difference = left.size xor right.size
        val maximum = maxOf(left.size, right.size)
        for (index in 0 until maximum) {
            val leftValue = if (index < left.size) left[index].toInt() else 0
            val rightValue = if (index < right.size) right[index].toInt() else 0
            difference = difference or (leftValue xor rightValue)
        }
        return difference == 0
    }
}

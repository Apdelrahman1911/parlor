package com.parlor.networking.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SecureHashesTest {
    @Test
    fun sha256_matches_the_standard_abc_vector() {
        assertThat(SecureHashes.sha256Utf8("abc").toHex()).isEqualTo(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )
    }

    @Test
    fun constant_time_comparison_handles_equal_unequal_and_different_lengths() {
        assertThat(SecureHashes.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
            .isEqualTo(true)
        assertThat(SecureHashes.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)))
            .isEqualTo(false)
        assertThat(SecureHashes.constantTimeEquals(byteArrayOf(1), byteArrayOf(1, 0)))
            .isEqualTo(false)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

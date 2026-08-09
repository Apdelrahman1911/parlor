package com.parlor.networking.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SecureIdsTest {
    @Test
    fun random_long_byte_conversion_preserves_all_64_random_bits() {
        assertThat(
            byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7).toLongBigEndian(),
        ).isEqualTo(0x0001_0203_0405_0607L)
        assertThat(ByteArray(Long.SIZE_BYTES) { 0xff.toByte() }.toLongBigEndian())
            .isEqualTo(-1L)
        assertThat(
            byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0).toLongBigEndian(),
        ).isEqualTo(Long.MIN_VALUE)
    }

    @Test
    fun random_long_byte_conversion_rejects_partial_entropy() {
        assertFailsWith<IllegalArgumentException> {
            ByteArray(Long.SIZE_BYTES - 1).toLongBigEndian()
        }
    }
}

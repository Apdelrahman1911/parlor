package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SnapshotProtectionPolicyTest {
    @Test
    fun authenticationTagOnlyPayloadIsValidForEmptyPlaintext() {
        assertTrue(
            hasCompleteGcmPayload(
                recordSize = 30,
                payloadOffset = 2,
                nonceBytes = 12,
                tagBytes = 16,
            ),
        )
    }

    @Test
    fun recordOneByteShorterThanAuthenticationTagIsRejected() {
        assertFalse(
            hasCompleteGcmPayload(
                recordSize = 29,
                payloadOffset = 2,
                nonceBytes = 12,
                tagBytes = 16,
            ),
        )
    }

    @Test
    fun plaintextExactlyAtTheMaximumIsAcceptedWithoutCopying() {
        val plaintext = ByteArray(MAX_PLAINTEXT_SNAPSHOT_BYTES) { 1 }

        assertSame(plaintext, enforceSnapshotPlaintextLimit(plaintext))
        assertTrue(plaintext.all { it == 1.toByte() })
    }

    @Test
    fun oversizedPlaintextIsClearedBeforeItIsRejected() {
        val plaintext = ByteArray(9) { 7 }

        assertFailsWith<SnapshotProtectionException> {
            enforceSnapshotPlaintextLimit(plaintext, maximumBytes = 8)
        }
        assertContentEquals(ByteArray(9), plaintext)
    }
}

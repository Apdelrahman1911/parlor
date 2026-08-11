package com.parlor.app.storage

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidGcmRecordPolicyTest {
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
    fun recordShorterThanAuthenticationTagIsRejected() {
        assertFalse(
            hasCompleteGcmPayload(
                recordSize = 29,
                payloadOffset = 2,
                nonceBytes = 12,
                tagBytes = 16,
            ),
        )
    }
}

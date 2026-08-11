package com.parlor.app.storage

/**
 * A valid GCM record may contain only the authentication tag after its nonce:
 * that is the canonical encoding of an empty plaintext.
 */
internal fun hasCompleteGcmPayload(
    recordSize: Int,
    payloadOffset: Int,
    nonceBytes: Int,
    tagBytes: Int,
): Boolean = recordSize >= payloadOffset + nonceBytes + tagBytes

package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException

internal const val MAX_PLAINTEXT_SNAPSHOT_BYTES: Int = 8 * 1024 * 1024

/**
 * A valid GCM record may contain only the authentication tag after its nonce:
 * that is the canonical encoding of an empty plaintext.
 */
internal fun hasCompleteGcmPayload(
    recordSize: Int,
    payloadOffset: Int,
    nonceBytes: Int,
    tagBytes: Int,
): Boolean =
    recordSize >= 0 &&
        payloadOffset >= 0 &&
        nonceBytes >= 0 &&
        tagBytes >= 0 &&
        payloadOffset <= recordSize &&
        nonceBytes <= recordSize - payloadOffset &&
        tagBytes <= recordSize - payloadOffset - nonceBytes

/** Rejects oversized decrypted data and clears it before releasing its storage. */
internal fun enforceSnapshotPlaintextLimit(
    plaintext: ByteArray,
    maximumBytes: Int = MAX_PLAINTEXT_SNAPSHOT_BYTES,
): ByteArray {
    require(maximumBytes >= 0) { "Snapshot plaintext limit must be non-negative" }
    if (plaintext.size <= maximumBytes) return plaintext

    plaintext.fill(0)
    throw SnapshotProtectionException()
}

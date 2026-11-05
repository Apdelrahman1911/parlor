package com.parlor.storage.snapshot

/**
 * Required platform protection could not be verified before accessing bytes.
 * This is storage unavailability, not evidence that the unread record is corrupt.
 * The snapshot store reports only its opaque IO code and leaves Retry/Discard
 * decisions with the caller; it never relaxes protection or logs the cause.
 */
class SnapshotProtectionUnavailableException(cause: Throwable? = null) :
    SnapshotProtectionException("snapshot protection unavailable", cause)

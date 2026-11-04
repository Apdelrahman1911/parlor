package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException

/** Metadata is evidence of the requested class, not proof of device-lock enforcement. */
internal fun requireVerifiedCompleteSnapshotProtection(
    volumeSupportsProtection: Boolean?,
    attributesReadSucceeded: Boolean,
    observedProtection: String?,
    completeProtection: String?,
) {
    if (
        volumeSupportsProtection != true ||
        !attributesReadSucceeded ||
        completeProtection == null ||
        observedProtection != completeProtection
    ) {
        throw SnapshotProtectionException()
    }
}

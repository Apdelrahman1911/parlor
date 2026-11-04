package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CompleteSnapshotProtectionPolicyTest {
    @Test
    fun completeMetadataRequiresSupportedVolumeAndSuccessfulRead() {
        requireVerifiedCompleteSnapshotProtection(true, true, COMPLETE, COMPLETE)
    }

    @Test
    fun unsupportedOrUnknownVolumeCannotBeQualifiedByCompleteMetadata() {
        for (supported in listOf(false, null)) {
            assertFailsWith<SnapshotProtectionException> {
                requireVerifiedCompleteSnapshotProtection(supported, true, COMPLETE, COMPLETE)
            }
        }
    }

    @Test
    fun aFailedMetadataQueryCannotBeQualifiedByItsValue() {
        assertFailsWith<SnapshotProtectionException> {
            requireVerifiedCompleteSnapshotProtection(true, false, COMPLETE, COMPLETE)
        }
    }

    @Test
    fun missingAttributeAndMissingConstantAreBothRejected() {
        for ((observed, expected) in listOf(null to COMPLETE, COMPLETE to null, null to null)) {
            assertFailsWith<SnapshotProtectionException> {
                requireVerifiedCompleteSnapshotProtection(true, true, observed, expected)
            }
        }
    }

    @Test
    fun otherProtectionClassesAreNotNormalizedToComplete() {
        for (protection in listOf("none", "unless-open", "until-first-unlock", "when-user-inactive")) {
            assertFailsWith<SnapshotProtectionException> {
                requireVerifiedCompleteSnapshotProtection(true, true, protection, COMPLETE)
            }
        }
    }

    private companion object {
        const val COMPLETE = "synthetic-complete"
    }
}

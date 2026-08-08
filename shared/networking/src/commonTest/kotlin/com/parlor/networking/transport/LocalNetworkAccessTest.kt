package com.parlor.networking.transport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkAccessTest {
    @Test
    fun recovery_guidance_is_reserved_for_proven_or_unclassified_failures() {
        assertTrue(LocalNetworkAccess.PermissionDenied.needsRecoveryGuidance)
        assertTrue(LocalNetworkAccess.FailureUnclassified.needsRecoveryGuidance)

        assertFalse(LocalNetworkAccess.NotApplicable.needsRecoveryGuidance)
        assertFalse(LocalNetworkAccess.Unknown.needsRecoveryGuidance)
        assertFalse(LocalNetworkAccess.Attempting.needsRecoveryGuidance)
        assertFalse(LocalNetworkAccess.Operational.needsRecoveryGuidance)
    }
}

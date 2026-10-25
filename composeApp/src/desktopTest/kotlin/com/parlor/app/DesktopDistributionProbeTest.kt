package com.parlor.app

import kotlin.test.Test

class DesktopDistributionProbeTest {
    @Test fun packaged_probe_requires_crypto_providers_and_native_graphics_without_opening_a_profile() {
        verifyDesktopDistribution()
    }
}

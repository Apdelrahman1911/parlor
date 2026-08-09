package com.parlor.games.whodunit.ui

import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernUiPolicy
import com.parlor.games.whodunit.ui.screens.safety.privacyConcernUiPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivacyConcernPolicyTest {
    @Test
    fun host_can_offer_authoritative_reroll() {
        assertEquals(
            PrivacyConcernUiPolicy.HostMayReroll,
            privacyConcernUiPolicy(isHost = true),
        )
    }

    @Test
    fun peer_is_told_to_contact_host_instead_of_offering_reroll() {
        assertEquals(
            PrivacyConcernUiPolicy.PeerMustContactHost,
            privacyConcernUiPolicy(isHost = false),
        )
    }
}

package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.networking.room.NetError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhodunitPeerSessionFlowPolicyTest {
    @Test
    fun failedLeaveDoesNotDisableConnectionRetry() {
        assertTrue(
            WhodunitPeerConnectionErrorState(
                leaveError = NetError.TransportFailure("leave failed"),
            ).canRetryConnection,
        )
    }

    @Test
    fun checkpointInstallationFailureStillDisablesConnectionRetry() {
        assertFalse(
            WhodunitPeerConnectionErrorState(
                checkpointInstallationError = NetError.IncompatibleProtocol,
            ).canRetryConnection,
        )
    }
}

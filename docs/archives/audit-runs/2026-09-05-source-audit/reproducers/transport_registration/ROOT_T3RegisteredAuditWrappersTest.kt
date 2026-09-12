package com.parlor.transport.p2p.audit

import com.parlor.transport.p2p.P2pKitRoomTransportLifecycleTest
import kotlin.test.Test

/** Execute only the eight annotated, non-ignored fake-kit tests absent from normal discovery.
 * The application tests themselves and physical-loopback Ignore annotations remain untouched.
 * Each isolated fixture receives the exact source AfterTest cancellation in finally.
 */
class ROOT_T3RegisteredAuditWrappersTest {
    @Test fun disconnectAcceptance() { fixture { disconnect_during_acceptance_rolls_back_the_seat_without_a_ghost_member() } }
    @Test fun cancellationAcceptance() { fixture { cancellation_during_acceptance_propagates_after_rolling_back_the_seat() } }
    @Test fun lateCorrectRoom() { fixture { wrong_room_does_not_end_search_before_a_late_correct_candidate_appears() } }
    @Test fun separateApprovalWindow() { fixture { admission_pending_opens_a_separate_host_approval_window() } }
    @Test fun hostCancellation() { fixture { host_send_propagates_coroutine_cancellation() } }
    @Test fun peerCancellation() { fixture { peer_send_propagates_coroutine_cancellation() } }
    @Test fun kitFactoryCancellation() { fixture { kit_factory_cancellation_is_never_mapped_to_transport_failure() } }
    @Test fun fatalFailures() { fixture { fatal_send_and_cleanup_failures_are_never_converted_to_normal_results() } }

    private fun fixture(body: P2pKitRoomTransportLifecycleTest.() -> Unit) {
        val fixture = P2pKitRoomTransportLifecycleTest()
        try { fixture.body() } finally { fixture.cancelScope() }
    }
}

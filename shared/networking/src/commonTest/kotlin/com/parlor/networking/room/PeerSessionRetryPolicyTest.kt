package com.parlor.networking.room

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class PeerSessionRetryPolicyTest {
    @Test
    fun fresh_session_uses_join_until_a_post_admission_start_failure() {
        val initial = PeerSessionRetryPolicy.initial(resumeExistingSession = false)

        assertThat(initial.nextAttempt).isEqualTo(PeerSessionAttempt.Join)
        assertThat(initial.afterRoomAcquired().nextAttempt).isEqualTo(PeerSessionAttempt.Join)
        assertThat(initial.afterPostAdmissionStartFailure().nextAttempt)
            .isEqualTo(PeerSessionAttempt.Resume)
    }

    @Test
    fun resumed_retry_returns_to_resume_after_each_failed_start_transaction() {
        val retained = PeerSessionRetryPolicy
            .initial(resumeExistingSession = false)
            .afterPostAdmissionStartFailure()

        val acquired = retained.afterRoomAcquired()
        assertThat(acquired.nextAttempt).isEqualTo(PeerSessionAttempt.Join)
        assertThat(acquired.afterPostAdmissionStartFailure().nextAttempt)
            .isEqualTo(PeerSessionAttempt.Resume)
    }

    @Test
    fun explicitly_resumed_flow_never_falls_back_to_fresh_room_code_admission() {
        val initial = PeerSessionRetryPolicy.initial(resumeExistingSession = true)

        assertThat(initial.nextAttempt).isEqualTo(PeerSessionAttempt.Resume)
        assertThat(initial.afterRoomAcquired().nextAttempt).isEqualTo(PeerSessionAttempt.Resume)
        assertThat(initial.afterPostAdmissionStartFailure().nextAttempt)
            .isEqualTo(PeerSessionAttempt.Resume)
    }
}

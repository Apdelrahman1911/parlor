package com.parlor.engine.testing.fakes

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import kotlin.test.Test
import kotlin.test.assertFails

class RrSnapshotCodecTest {
    private val state = RrState(
        phase = RrPhase.Announcing(currentSeat = 1),
        players = listOf(
            Player(PlayerId("p1"), "Alice", seat = 0),
            Player(PlayerId("p2"), "Bob", seat = 1),
        ),
        announcedBy = listOf(PlayerId("p1")),
    )

    @Test
    fun round_trip_preserves_the_complete_fixture_state() {
        assertThat(RrSnapshotCodec.decode(RrSnapshotCodec.encode(state))).isEqualTo(state)
    }

    @Test
    fun malformed_payload_is_rejected_instead_of_fabricating_state() {
        assertFails {
            RrSnapshotCodec.decode("{\"phase\":{}}".encodeToByteArray())
        }
    }
}

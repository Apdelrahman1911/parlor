package com.parlor.games.whodunit.action

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.PlayerId
import com.parlor.games.whodunit.domain.action.StructuredActionPayload
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Closes the Phase 7 / P2P serialization gap: `PeerMessage.ActionSubmit`
 * carries a `ByteArray` payload, and Whodunit's actions can now flow over
 * it because every variant is `@Serializable` and round-trips cleanly
 * through [WhodunitActionCodec].
 *
 * The test covers each shape category — singleton (`data object`), simple
 * value (`AssignRoles`, `AdvanceBriefingCard`), id-carrying (`CastVote`),
 * and nested polymorphic (`SubmitStructuredAction(StructuredActionPayload)`)
 * — because kotlinx-serialization's polymorphic discriminator output
 * differs across these and a regression would show up only on one shape.
 */
class WhodunitActionCodecTest {

    @Test
    fun oversizedActionIsRejectedOnEncodeAndDecode() {
        val oversized = WhodunitAction.SubmitStructuredAction(
            StructuredActionPayload.Alibi(PlayerId("alice"), "x".repeat(33 * 1024)),
        )

        assertFailsWith<IllegalArgumentException> {
            WhodunitActionCodec.encode(oversized)
        }
        assertFailsWith<IllegalArgumentException> {
            WhodunitActionCodec.decode(ByteArray(32 * 1024 + 1))
        }
    }

    @Test
    fun singleton_data_object_round_trips() {
        val original: WhodunitAction = WhodunitAction.Pause
        val bytes = WhodunitActionCodec.encode(original)
        val decoded = WhodunitActionCodec.decode(bytes)
        assertThat(decoded).isEqualTo(WhodunitAction.Pause)
    }

    @Test
    fun primitive_carrying_action_round_trips() {
        val original = WhodunitAction.AssignRoles(seed = 4242L)
        val decoded = WhodunitActionCodec.decode(WhodunitActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun id_carrying_action_round_trips() {
        val original = WhodunitAction.CastVote(
            voter = PlayerId("p1"),
            target = PlayerId("p3"),
        )
        val decoded = WhodunitActionCodec.decode(WhodunitActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun every_private_reveal_action_round_trips_its_assignment_generation() {
        val playerId = PlayerId("p1")
        val generation = 42L
        val actions = listOf<WhodunitAction>(
            WhodunitAction.StartCharacterReveal(playerId, generation),
            WhodunitAction.CompleteCharacterReveal(playerId, generation),
            WhodunitAction.OpenPrivateReview(playerId, generation),
            WhodunitAction.CloseHide(playerId, generation),
            WhodunitAction.ConfirmRoleViewed(playerId, generation),
        )

        actions.forEach { action ->
            val bytes = WhodunitActionCodec.encode(action)
            assertThat(bytes.decodeToString()).contains(
                "\"roleAssignmentGeneration\":$generation",
            )
            assertThat(WhodunitActionCodec.decode(bytes)).isEqualTo(action)
        }
    }

    @Test
    fun reveal_generation_is_required_and_must_be_positive() {
        val valid = WhodunitActionCodec.encode(
            WhodunitAction.StartCharacterReveal(PlayerId("p1"), 42L),
        ).decodeToString()
        val missing = valid.replace(",\"roleAssignmentGeneration\":42", "")
        val negative = valid.replace(
            "\"roleAssignmentGeneration\":42",
            "\"roleAssignmentGeneration\":-1",
        )

        assertFailsWith<SerializationException> {
            WhodunitActionCodec.decode(missing.encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            WhodunitActionCodec.decode(negative.encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            WhodunitActionCodec.encode(
                WhodunitAction.CompleteCharacterReveal(PlayerId("p1"), 0L),
            )
        }
    }

    @Test
    fun refuse_to_vote_round_trips() {
        val original = WhodunitAction.RefuseToVote(voter = PlayerId("p4"))
        val decoded = WhodunitActionCodec.decode(WhodunitActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun nested_polymorphic_payload_round_trips() {
        val original = WhodunitAction.SubmitStructuredAction(
            payload = StructuredActionPayload.Question(
                from = PlayerId("p1"),
                to = PlayerId("p2"),
                text = "Where were you at 9:00?",
            ),
        )
        val decoded = WhodunitActionCodec.decode(WhodunitActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
        val payload = (decoded as WhodunitAction.SubmitStructuredAction).payload
        assertThat(payload).isInstanceOf(StructuredActionPayload.Question::class)
    }

    @Test
    fun structured_action_no_action_object_round_trips() {
        val original = WhodunitAction.SubmitStructuredAction(StructuredActionPayload.NoAction)
        val decoded = WhodunitActionCodec.decode(WhodunitActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun encoded_payload_carries_discriminator_for_forward_compat() {
        // A peer running an older app version can read the discriminator and
        // refuse cleanly instead of mis-decoding bytes. This pins that
        // kotlinx-serialization emits the discriminator field (default key:
        // `type`).
        val bytes = WhodunitActionCodec.encode(WhodunitAction.OpenVote)
        val text = bytes.decodeToString()
        assertThat(text).contains("\"type\":")
    }
}

package com.parlor.games.mafia.domain.action

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.TieBehavior
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

/**
 * Peer→host action submission rides `PeerMessage.ClientCommand(payload: ByteArray)`.
 * Every variant must round-trip cleanly through [MafiaActionCodec]; a regression
 * in any shape (singleton, primitive-carrying, id-carrying, nested data class)
 * would only surface on that shape, so each shape gets its own test.
 */
class MafiaActionCodecTest {

    private val p1 = PlayerId("p1")
    private val p2 = PlayerId("p2")

    @Test
    fun oversizedActionIsRejectedOnEncodeAndDecode() {
        val oversized = MafiaAction.CastVote(
            by = PlayerId("x".repeat(33 * 1024)),
            target = p2,
        )

        assertFailsWith<IllegalArgumentException> { MafiaActionCodec.encode(oversized) }
        assertFailsWith<IllegalArgumentException> {
            MafiaActionCodec.decode(ByteArray(32 * 1024 + 1))
        }
    }

    @Test
    fun singleton_data_object_round_trips() {
        val original: MafiaAction = MafiaAction.StartGame
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(MafiaAction.StartGame)
    }

    @Test
    fun apply_settings_round_trips_with_nested_role_counts() {
        val original = MafiaAction.ApplySettings(
            settings = MafiaSettings(
                roleCounts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
                voteTieBehavior = TieBehavior.REVOTE_TIED_ONLY,
                mafiaKillTieBehavior = MafiaKillTie.REVOTE,
                maxRevotes = 1,
                nightDurationSeconds = 30,
            ),
        )
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun atomic_configure_and_start_round_trips_with_nested_settings() {
        val original = MafiaAction.ConfigureAndStart(
            settings = MafiaSettings(
                roleCounts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
                voteTieBehavior = TieBehavior.SKIP_ELIMINATION,
                mafiaKillTieBehavior = MafiaKillTie.NO_KILL,
                maxRevotes = 3,
            ),
        )

        assertThat(MafiaActionCodec.decode(MafiaActionCodec.encode(original)))
            .isEqualTo(original)
    }

    @Test
    fun mafia_kill_vote_round_trips_with_nullable_target() {
        val withTarget = MafiaAction.SubmitMafiaKillVote(by = p1, target = p2)
        val withoutTarget = MafiaAction.SubmitMafiaKillVote(by = p1, target = null)
        assertThat(MafiaActionCodec.decode(MafiaActionCodec.encode(withTarget))).isEqualTo(withTarget)
        assertThat(MafiaActionCodec.decode(MafiaActionCodec.encode(withoutTarget))).isEqualTo(withoutTarget)
    }

    @Test
    fun doctor_protect_round_trips() {
        val original = MafiaAction.SubmitDoctorProtect(by = p1, target = p2)
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun detective_inspect_round_trips() {
        val original = MafiaAction.SubmitDetectiveInspect(by = p1, target = p2)
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun civilian_suspicion_round_trips() {
        val original = MafiaAction.SubmitCivilianSuspicion(by = p1, target = p2)
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun cast_vote_round_trips() {
        val original = MafiaAction.CastVote(by = p1, target = p2)
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun abstain_round_trips() {
        val original = MafiaAction.AbstainVote(by = p1)
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun acknowledge_actions_round_trip() {
        val acks = listOf<MafiaAction>(
            MafiaAction.AcknowledgeRoleViewed(p1),
            MafiaAction.AcknowledgeNightAnnouncement(p1),
            MafiaAction.AcknowledgeDetectiveResult(p1),
            MafiaAction.AcknowledgeVoteAnnouncement(p1),
        )
        for (a in acks) {
            assertThat(MafiaActionCodec.decode(MafiaActionCodec.encode(a))).isEqualTo(a)
        }
    }

    @Test
    fun connection_chrome_actions_round_trip() {
        val actions = listOf<MafiaAction>(
            MafiaAction.MarkPlayerDisconnected(p1),
            MafiaAction.MarkPlayerReconnected(p1),
            MafiaAction.ContinueWithoutPlayer(p1),
        )
        for (a in actions) {
            assertThat(MafiaActionCodec.decode(MafiaActionCodec.encode(a))).isEqualTo(a)
        }
    }

    @Test
    fun host_lifecycle_singletons_round_trip() {
        val actions = listOf<MafiaAction>(
            MafiaAction.StartGame,
            MafiaAction.AdvanceFromRoleAssignment,
            MafiaAction.ResolveNight,
            MafiaAction.OpenDiscussion,
            MafiaAction.OpenVote,
            MafiaAction.CloseVote,
            MafiaAction.AdvanceFromVoteAnnouncement,
            MafiaAction.EndGame,
        )
        for (a in actions) {
            assertThat(MafiaActionCodec.decode(MafiaActionCodec.encode(a))).isEqualTo(a)
        }
    }

    @Test
    fun encoded_payload_carries_discriminator_for_forward_compat() {
        // Older peer can read the discriminator and refuse cleanly instead of
        // mis-decoding bytes. Pins that kotlinx-serialization emits the
        // discriminator key (default: `type`).
        val bytes = MafiaActionCodec.encode(MafiaAction.OpenVote)
        val text = bytes.decodeToString()
        assertThat(text).contains("\"type\":")
    }

    @Test
    fun retired_actions_are_rejected_explicitly() {
        val retired = listOf(
            "com.parlor.games.mafia.domain.action.MafiaAction.AcknowledgePostGame",
            "com.parlor.games.mafia.domain.action.MafiaAction.ReadmitPlayer",
        )

        retired.forEach { type ->
            assertFailsWith<UnsupportedLegacyMafiaActionException> {
                MafiaActionCodec.decode(
                    """{"type":"$type","by":"p1","playerId":"p1"}"""
                        .encodeToByteArray(),
                )
            }
        }
    }

    @Test
    fun malformedUtf8IsRejectedInsteadOfBeingReplacedInsideJsonStrings() {
        val valid = MafiaActionCodec.encode(MafiaAction.CastVote(p1, p2))
        val quoteIndex = valid.indexOfFirst { byte -> byte == 'p'.code.toByte() }
        val malformed = valid.copyOf().also { it[quoteIndex] = 0x80.toByte() }

        assertFails { MafiaActionCodec.decode(malformed) }
    }
}

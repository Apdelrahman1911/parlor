package com.parlor.games.mafia.domain.projection

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPublic
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.NightResolutionRecord
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.VoteRoundRecord
import kotlin.test.Test
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Sentinel for the hand-maintained `redactedHostOnly` constant inside
 * [MafiaProjectionPolicy].
 *
 * The policy clears the `hostOnly` bucket by replacing the field with a
 * fully redacted [MafiaHostOnly] instance — not by mutating the original.
 * The field-level assertions below cover every currently serialized field.
 * The serialized-field count sentinel below forces a deliberate review when
 * a future [MafiaHostOnly] field is added; it does not infer that a new
 * declared default is safe or that the projection redacts it.
 *
 * To extend [MafiaHostOnly] safely: add the new field with an explicit
 * empty/zero default, update `MafiaProjectionPolicy.redactedHostOnly`, add a
 * populated fixture value and an explicit assertion, and update the pinned
 * serialized-field count.
 */
class MafiaHostOnlyRedactionSentinelTest {

    private val players = (0 until 7).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    private fun stateWithPopulatedHostOnly(): MafiaState = MafiaState(
        public = MafiaPublic(
            settings = MafiaSettingsPresets.forPlayerCount(7),
            roster = players.map { PublicPlayerSlot(it.id, it.displayName, it.seat) },
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = MafiaHostOnly(
            fullRoleMap = mapOf(players[0].id to Role.Mafia, players[1].id to Role.Detective),
            randomSeed = 12345L,
            nightLog = listOf(
                NightResolutionRecord(
                    day = 1,
                    mafiaTarget = players[0].id,
                    mafiaTargetTied = false,
                    doctorProtect = null,
                    detectiveInspect = null,
                    detectiveResult = null,
                    killedPlayerId = players[0].id,
                ),
            ),
            voteLog = listOf(
                VoteRoundRecord(
                    day = 1,
                    revoteRound = 0,
                    tally = mapOf(players[0].id to 3),
                    eliminatedPlayerId = players[0].id,
                ),
            ),
        ),
        phase = MafiaPhase.Setup,
        players = players,
    )

    @Test
    fun to_public_redacts_every_known_host_only_field() {
        val redacted = MafiaProjectionPolicy.toPublic(stateWithPopulatedHostOnly()).state.hostOnly
        // Every currently serialized field must be the "empty" sentinel value.
        assertThat(redacted.fullRoleMap).isEmpty()
        assertThat(redacted.randomSeed).isEqualTo(0L)
        assertThat(redacted.nightLog).isEmpty()
        assertThat(redacted.voteLog).isEmpty()
    }

    @Test
    fun to_player_redacts_every_known_host_only_field() {
        val state = stateWithPopulatedHostOnly()
        val viewer = players[3].id
        val redacted = MafiaProjectionPolicy.toPlayer(state, viewer).state.hostOnly
        assertThat(redacted.fullRoleMap).isEmpty()
        assertThat(redacted.randomSeed).isEqualTo(0L)
        assertThat(redacted.nightLog).isEmpty()
        assertThat(redacted.voteLog).isEmpty()
    }

    @Test
    fun redacted_host_only_equals_empty_constructor_baseline() {
        // The redaction constant must equal a freshly constructed
        // [MafiaHostOnly] with `fullRoleMap = emptyMap()`, `randomSeed = 0L`,
        // and all other fields at their declared defaults. This documents the
        // constructor baseline; the schema-count test below forces review of
        // whether any newly declared default is safe to project.
        val baseline = MafiaHostOnly(fullRoleMap = emptyMap(), randomSeed = 0L)
        val projected = MafiaProjectionPolicy.toPublic(stateWithPopulatedHostOnly()).state.hostOnly
        assertThat(projected).isEqualTo(baseline)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun redaction_sentinel_requires_review_when_host_only_schema_changes() {
        assertThat(MafiaHostOnly.serializer().descriptor.elementsCount).isEqualTo(4)
    }
}

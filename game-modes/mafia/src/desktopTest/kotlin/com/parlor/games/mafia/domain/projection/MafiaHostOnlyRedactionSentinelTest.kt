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

/**
 * Sentinel for the hand-maintained `redactedHostOnly` constant inside
 * [MafiaProjectionPolicy].
 *
 * The policy clears the `hostOnly` bucket by replacing the field with a
 * fully redacted [MafiaHostOnly] instance — not by mutating the original.
 * That means **every field** of `MafiaHostOnly` must be reset to its
 * empty/zero default by the projection. If a future `MafiaHostOnly` field
 * is added but not added to the redaction constant, this test fails —
 * preventing a silent privacy leak through `toPublic` / `toPlayer`.
 *
 * To extend [MafiaHostOnly] safely: add the new field with an explicit
 * empty/zero default here AND inside `MafiaProjectionPolicy.redactedHostOnly`.
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
        // Every known field must be the "empty" sentinel value. If a new
        // field is added to MafiaHostOnly without being redacted, the
        // resulting projection will retain it and one of these assertions
        // (or a new one you write alongside the new field) will fail.
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
        // and all other fields at their declared defaults. Anyone adding a
        // new MafiaHostOnly field must either give it a safe default OR
        // update the redaction constant to match.
        val baseline = MafiaHostOnly(fullRoleMap = emptyMap(), randomSeed = 0L)
        val projected = MafiaProjectionPolicy.toPublic(stateWithPopulatedHostOnly()).state.hostOnly
        assertThat(projected).isEqualTo(baseline)
    }
}

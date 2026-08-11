package com.parlor.games.mafia.domain.rules

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.networking.room.RoomInputPolicy
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MafiaSessionRulesTest {
    private val definition = MafiaDefinition(Json { encodeDefaults = true })

    @Test
    fun every_supported_classic_roster_is_accepted_at_the_definition_boundary() {
        for (count in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS) {
            val config = config(players(count))

            assertTrue(MafiaSessionRules.isValidConfig(config), "count=$count")
            assertTrue(definition.createInitialState(config).players == config.players, "count=$count")
        }
    }

    @Test
    fun unsupported_mode_is_rejected_before_authoritative_state_is_created() {
        val config = config(players(5)).copy(modeId = ModeId("modified-classic"))

        assertFalse(MafiaSessionRules.isValidConfig(config))
        assertFailsWith<IllegalArgumentException> { definition.createInitialState(config) }
    }

    @Test
    fun player_count_identity_and_seat_mutations_are_rejected() {
        val valid = players(5)
        val mutations = listOf(
            "below minimum" to players(MafiaSettings.MIN_PLAYERS - 1),
            "above maximum" to players(MafiaSettings.MAX_PLAYERS + 1),
            "duplicate id" to valid.toMutableList().also {
                it[1] = it[1].copy(id = it[0].id)
            },
            "duplicate seat" to valid.toMutableList().also {
                it[1] = it[1].copy(seat = it[0].seat)
            },
            "seat gap" to valid.toMutableList().also {
                it[4] = it[4].copy(seat = 5)
            },
            "seat order" to valid.toMutableList().also {
                val first = it[0]
                it[0] = it[1]
                it[1] = first
            },
        )

        mutations.forEach { (label, roster) ->
            val config = config(roster)
            assertFalse(MafiaSessionRules.isValidConfig(config), label)
            assertFailsWith<IllegalArgumentException>(label) {
                definition.createInitialState(config)
            }
        }
    }

    @Test
    fun names_must_be_bounded_trimmed_and_free_of_control_or_format_characters() {
        val valid = players(5)
        val invalidNames = listOf(
            "",
            "   ",
            " Alice",
            "Alice ",
            "A".repeat(RoomInputPolicy.MAX_DISPLAY_NAME_LENGTH + 1),
            "Ali\u0000ce",
            "Ali\u202Ece",
        )

        invalidNames.forEachIndexed { index, name ->
            val roster = valid.toMutableList().also {
                it[index % it.size] = it[index % it.size].copy(displayName = name)
            }
            assertFalse(MafiaSessionRules.isValidRoster(roster), "invalid name index=$index")
            assertFailsWith<IllegalArgumentException>("invalid name index=$index") {
                definition.createInitialState(config(roster))
            }
        }

        val international = valid.toMutableList().also {
            it[0] = it[0].copy(displayName = "عبد الرحمن")
            it[1] = it[1].copy(displayName = "A".repeat(RoomInputPolicy.MAX_DISPLAY_NAME_LENGTH))
        }
        assertTrue(MafiaSessionRules.isValidRoster(international))
    }

    @Test
    fun exact_duplicate_display_names_are_rejected_at_the_authoritative_boundary() {
        val duplicateNames = players(5).toMutableList().also {
            it[1] = it[1].copy(displayName = it[0].displayName)
        }

        assertFalse(MafiaSessionRules.isValidRoster(duplicateNames))
        assertFailsWith<IllegalArgumentException> {
            definition.createInitialState(config(duplicateNames))
        }

        val caseVariants = players(5).toMutableList().also {
            it[0] = it[0].copy(displayName = "Alice")
            it[1] = it[1].copy(displayName = "alice")
        }
        assertTrue(MafiaSessionRules.isValidRoster(caseVariants))
    }

    private fun config(players: List<Player>) = SessionConfig(
        sessionId = SessionId("mafia-session-rules"),
        caseId = CaseId("default"),
        modeId = MafiaIds.ClassicModeId,
        players = players,
        randomSeed = 42L,
    )

    private fun players(count: Int): List<Player> = (0 until count).map { seat ->
        Player(
            id = PlayerId("player-${seat + 1}"),
            displayName = "Player ${seat + 1}",
            seat = seat,
        )
    }
}

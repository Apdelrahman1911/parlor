package com.parlor.games.whodunit.ui.screens.setup

import com.parlor.games.whodunit.WhodunitIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhodunitModeChoiceTest {
    @Test
    fun selected_case_controls_visible_modes_and_effective_player_bounds() {
        assertEquals(
            listOf(WhodunitModeChoice(WhodunitIds.ClassicVoteModeId, 6..6)),
            whodunitModeChoices(
                caseSupportedModes = listOf(WhodunitIds.ClassicVoteModeId.raw),
                caseSupportedPlayerCounts = 6..6,
            ),
        )
        assertEquals(
            listOf(WhodunitModeChoice(WhodunitIds.EliminationModeId, 5..6)),
            whodunitModeChoices(
                caseSupportedModes = listOf(WhodunitIds.EliminationModeId.raw),
                caseSupportedPlayerCounts = 4..6,
            ),
        )
    }

    @Test
    fun unsupported_unknown_and_disjoint_modes_are_not_offered() {
        assertTrue(
            whodunitModeChoices(
                caseSupportedModes = listOf("future-mode"),
                caseSupportedPlayerCounts = 4..8,
            ).isEmpty(),
        )
        assertTrue(
            whodunitModeChoices(
                caseSupportedModes = listOf(WhodunitIds.EliminationModeId.raw),
                caseSupportedPlayerCounts = 4..4,
            ).isEmpty(),
        )
    }
}

package com.parlor.games.whodunit

import com.parlor.core.ids.GameId
import com.parlor.core.ids.ModeId

/** Stable identifiers for the Whodunit module. */
object WhodunitIds {
    val GameId = GameId("whodunit")
    val ClassicVoteModeId = ModeId("classic-vote")
    val EliminationModeId = ModeId("elimination")
}

package com.parlor.games.lastlight

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.ids.ModeId

/** Stable protocol and persistence identities for the fixed version-one rules. */
object LastLightIds {
    val GameId = GameId("last-light")
    val StandardModeId = ModeId("standard")
    val CaseId = CaseId("last-light-standard")
}

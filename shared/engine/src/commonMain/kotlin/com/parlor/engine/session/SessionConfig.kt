package com.parlor.engine.session

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player

/**
 * The static configuration of a game session at start. Together with validated
 * case content (carried by the module's content payload), this fully determines
 * the initial state.
 */
data class SessionConfig(
    val sessionId: SessionId,
    val caseId: CaseId,
    val modeId: ModeId,
    val players: List<Player>,
    val randomSeed: Long,
)

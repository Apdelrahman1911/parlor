package com.parlor.games.whodunit.ui.flow

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.networking.security.SecureIds

/** Builds a local config without exposing the hidden gameplay seed through its persistent id. */
internal fun createLocalWhodunitSessionConfig(
    caseId: CaseId,
    modeId: ModeId,
    players: List<Player>,
    randomSeed: Long,
    restoredSessionId: SessionId?,
    sessionIdGenerator: () -> String = SecureIds::id128,
): SessionConfig = SessionConfig(
    sessionId = restoredSessionId ?: SessionId(sessionIdGenerator()),
    caseId = caseId,
    modeId = modeId,
    players = players,
    randomSeed = randomSeed,
)

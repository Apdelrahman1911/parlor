package com.parlor.games.mafia.ui.flow.passandplay

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaIds
import com.parlor.networking.security.SecureIds

/** Builds a local config without exposing the hidden gameplay seed through its persistent id. */
internal fun createLocalMafiaSessionConfig(
    players: List<Player>,
    randomSeed: Long,
    restoredSessionId: SessionId?,
    sessionIdGenerator: () -> String = SecureIds::id128,
): SessionConfig = SessionConfig(
    sessionId = restoredSessionId ?: SessionId(sessionIdGenerator()),
    caseId = CaseId("default"),
    modeId = MafiaIds.ClassicModeId,
    players = players,
    randomSeed = randomSeed,
)

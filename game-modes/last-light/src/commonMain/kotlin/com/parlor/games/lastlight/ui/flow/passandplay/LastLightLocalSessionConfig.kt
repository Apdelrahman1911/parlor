package com.parlor.games.lastlight.ui.flow.passandplay

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightIds
import com.parlor.networking.room.RoomInputPolicy
import com.parlor.networking.security.SecureIds

/** Local labels follow the same canonical namespace as admitted LAN seats. */
internal fun lastLightLocalPlayers(names: List<String>): List<Player> {
    val canonicalNames = names.map(RoomInputPolicy::normalizeDisplayName)
    require(canonicalNames.size in 2..6)
    require(RoomInputPolicy.areValidDistinctDisplayNames(canonicalNames))
    return canonicalNames.mapIndexed { index, name ->
        Player(id = PlayerId("p${index + 1}"), displayName = name, seat = index)
    }
}

/** The public session identity never contains the secret seed. */
internal fun createLocalLastLightSessionConfig(
    players: List<Player>,
    randomSeed: Long,
    restoredSessionId: SessionId? = null,
    sessionIdGenerator: () -> String = SecureIds::id128,
): SessionConfig = SessionConfig(
    sessionId = restoredSessionId ?: SessionId(sessionIdGenerator()),
    caseId = LastLightIds.CaseId,
    modeId = LastLightIds.StandardModeId,
    players = players.toList(),
    randomSeed = randomSeed,
)

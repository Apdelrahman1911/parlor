package com.parlor.games.whodunit

import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.session.passandplay.PassAndPlaySessionController

/**
 * Shared test helpers for the Wave 9H readiness gates. Existing tests
 * predate the gates and the gates would silently no-op `AdvanceFromIntro`
 * etc. Using these helpers keeps the test bodies focused on the behaviour
 * under test instead of the ack ceremony.
 */

internal suspend fun PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>.ackIntroForAll(
    players: List<Player>,
) {
    for (player in players) submit(WhodunitAction.AcknowledgeIntro(player.id))
}

internal suspend fun PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>.ackBriefingForAll(
    players: List<Player>,
) {
    for (player in players) submit(WhodunitAction.AcknowledgeBriefing(player.id))
}

package com.parlor.games.whodunit.ui.flow

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase

/**
 * Pure helper that decides whether the local device should render the
 * dossier-reveal ceremony for the *current* player during
 * [WhodunitPhase.CharacterReveal], or render the waiting screen instead.
 *
 * Pass-and-play semantics — `selfPlayerId == null` — always renders the
 * dossier locally (the phone is being passed around).
 *
 * Multi-device semantics — `selfPlayerId` non-null — only the device whose
 * id matches `players[phase.playerIndex]` renders the dossier; every other
 * device waits.
 *
 * Extracted from `CharacterRevealSegment` so the logic is unit-testable
 * without spinning up Compose; the regression test for the JVM peer crash
 * pins this contract.
 *
 * Returns the player whose dossier the local device should reveal (i.e.
 * the local player) when the device is the active one, or `null` when the
 * device should show the waiting screen.
 */
internal fun resolveLocalRevealActor(
    phase: WhodunitPhase.CharacterReveal,
    players: List<Player>,
    selfPlayerId: PlayerId?,
): Player? {
    val current = players.getOrNull(phase.playerIndex) ?: return null
    // Pass-and-play: render the current player's reveal — the phone is on
    // their side of the table.
    if (selfPlayerId == null) return current
    // Multi-device: only the matching device shows the dossier.
    return if (current.id == selfPlayerId) current else null
}

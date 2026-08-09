package com.parlor.games.whodunit

import com.parlor.engine.state.Player
import com.parlor.core.ids.PlayerId
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

/**
 * Wave 9H-3: Drive the simultaneous-reveal phase. Each player completes
 * their reveal (which now adds them to `rolesViewed` without
 * auto-advancing), then host explicitly advances to Round(1).
 */
internal suspend fun PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>.revealRolesAndAdvance(
    players: List<Player>,
) {
    val generation = requireNotNull(hostState).value.state.public.roleAssignmentGeneration
    for (player in players) {
        submit(WhodunitAction.StartCharacterReveal(player.id, generation))
        submit(WhodunitAction.CompleteCharacterReveal(player.id, generation))
    }
    submit(WhodunitAction.AdvanceFromCharacterReveal)
}

/**
 * Cast a unanimous valid accusation. The accused cannot vote for themselves,
 * so their own ballot is recorded as an abstention.
 */
internal suspend fun PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>.accuseWithAllOtherVoters(
    ballot: List<PlayerId>,
    target: PlayerId,
) {
    for (voter in ballot) {
        if (voter == target) submit(WhodunitAction.AbstainVote(voter))
        else submit(WhodunitAction.CastVote(voter, target))
    }
}

/**
 * Produce a valid two-way split without ever allowing a self-vote. Any
 * remaining voters abstain (or explicitly refuse when [refuseRemainder] is
 * true). The common 4-player 2–2 and 5-player 2–2–abstain fixtures are both
 * covered.
 */
internal suspend fun PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>.castSplitVote(
    ballot: List<PlayerId>,
    targetA: PlayerId,
    votesForA: Int,
    targetB: PlayerId,
    votesForB: Int,
    refuseRemainder: Boolean = false,
) {
    var remainingA = votesForA
    var remainingB = votesForB
    for (voter in ballot) {
        when {
            remainingA > 0 && voter != targetA -> {
                submit(WhodunitAction.CastVote(voter, targetA))
                remainingA -= 1
            }
            remainingB > 0 && voter != targetB -> {
                submit(WhodunitAction.CastVote(voter, targetB))
                remainingB -= 1
            }
            remainingA > 0 -> {
                submit(WhodunitAction.CastVote(voter, targetA))
                remainingA -= 1
            }
            remainingB > 0 -> {
                submit(WhodunitAction.CastVote(voter, targetB))
                remainingB -= 1
            }
            refuseRemainder -> submit(WhodunitAction.RefuseToVote(voter))
            else -> submit(WhodunitAction.AbstainVote(voter))
        }
    }
    check(remainingA == 0 && remainingB == 0) {
        "Unable to construct requested split for the supplied ballot"
    }
}

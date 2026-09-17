package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.room.RoomLifecycleState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Drives automatic host-only transitions from the retained multiplayer runtime.
 *
 * This must not be owned by a Composable: the host remains authoritative while
 * the UI root is recreated, and a transition rejected during app suspension
 * must be offered again when the same room generation becomes active. The
 * outer [collectLatest] cancels an in-flight offer as soon as the room leaves
 * [RoomLifecycleState.Active] or foreground readiness. A validated short return
 * must also re-evaluate the gate even when the physical room stayed Active.
 */
internal suspend fun driveMafiaHostProgression(
    states: StateFlow<MafiaState>,
    lifecycle: StateFlow<RoomLifecycleState>,
    foregroundReady: StateFlow<Boolean>? = null,
    submit: suspend (MafiaAction) -> Unit,
) {
    combine(lifecycle, foregroundReady ?: flowOf(true)) { lifecycleState, ready ->
        lifecycleState == RoomLifecycleState.Active && ready
    }.distinctUntilChanged().collectLatest { ready ->
        if (ready) {
            states.collect { state ->
                nextHostAdvance(state)?.let { submit(it) }
            }
        }
    }
}

/**
 * The gated host advance ready for [state], or `null` while the reducer's
 * prerequisites are incomplete. The reducer remains the canonical validator.
 */
internal fun nextHostAdvance(state: MafiaState): MafiaAction? {
    if (state.public.disconnectedPlayers.isNotEmpty()) return null
    val active = state.players.map { it.id }.filterNot { it in state.public.droppedPlayers }
    val aliveActive = active.filter { id ->
        state.public.roster.firstOrNull { it.playerId == id }?.alive == true
    }
    return when (state.phase) {
        MafiaPhase.RoleAssignment ->
            MafiaAction.AdvanceFromRoleAssignment.takeIf {
                active.isNotEmpty() &&
                    active.all { state.privatePerPlayer[it]?.roleAcknowledged == true }
            }
        is MafiaPhase.Night ->
            MafiaAction.ResolveNight.takeIf {
                aliveActive.isNotEmpty() &&
                    aliveActive.all { id ->
                        val private = state.privatePerPlayer[id]
                        private?.nightChoiceSubmitted == true &&
                            (
                                private.pendingDetectiveResult == null ||
                                    private.detectiveResultAcknowledged
                            )
                    }
            }
        is MafiaPhase.NightAnnouncement ->
            MafiaAction.OpenDiscussion.takeIf {
                aliveActive.isNotEmpty() &&
                    aliveActive.all { state.privatePerPlayer[it]?.nightAcknowledged == true }
            }
        is MafiaPhase.Voting -> {
            val vote = state.public.activeVote
            MafiaAction.CloseVote.takeIf {
                vote != null && vote.ballot.isNotEmpty() &&
                    vote.ballot.all { it in vote.castSoFar.keys || it in vote.abstained }
            }
        }
        is MafiaPhase.VoteAnnouncement ->
            MafiaAction.AdvanceFromVoteAnnouncement.takeIf {
                aliveActive.isNotEmpty() &&
                    aliveActive.all { state.privatePerPlayer[it]?.voteAcknowledged == true }
            }
        else -> null
    }
}

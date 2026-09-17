package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordRole
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_cancel
import com.parlor.games.wordimpostor.resources.wi_champions
import com.parlor.games.wordimpostor.resources.wi_guess_confirm
import com.parlor.games.wordimpostor.resources.wi_guess_count
import com.parlor.games.wordimpostor.resources.wi_guess_hint
import com.parlor.games.wordimpostor.resources.wi_guess_recorded
import com.parlor.games.wordimpostor.resources.wi_guess_result
import com.parlor.games.wordimpostor.resources.wi_guess_wait
import com.parlor.games.wordimpostor.resources.wi_guessing
import com.parlor.games.wordimpostor.resources.wi_hide
import com.parlor.games.wordimpostor.resources.wi_impostors_were
import com.parlor.games.wordimpostor.resources.wi_lock_guess
import com.parlor.games.wordimpostor.resources.wi_lock_vote
import com.parlor.games.wordimpostor.resources.wi_next
import com.parlor.games.wordimpostor.resources.wi_ordinary_no_point
import com.parlor.games.wordimpostor.resources.wi_ordinary_point
import com.parlor.games.wordimpostor.resources.wi_rematch
import com.parlor.games.wordimpostor.resources.wi_result
import com.parlor.games.wordimpostor.resources.wi_reveal
import com.parlor.games.wordimpostor.resources.wi_reveal_hint
import com.parlor.games.wordimpostor.resources.wi_score
import com.parlor.games.wordimpostor.resources.wi_scoreboard
import com.parlor.games.wordimpostor.resources.wi_vote_confirm
import com.parlor.games.wordimpostor.resources.wi_vote_count
import com.parlor.games.wordimpostor.resources.wi_vote_counts
import com.parlor.games.wordimpostor.resources.wi_vote_recorded
import com.parlor.games.wordimpostor.resources.wi_voted_count
import com.parlor.games.wordimpostor.resources.wi_voting
import com.parlor.games.wordimpostor.resources.wi_voting_hint
import com.parlor.games.wordimpostor.resources.wi_wait_host
import com.parlor.games.wordimpostor.resources.wi_word_was
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WordVoting(
    state: WordImpostorState, self: PlayerId, enabled: Boolean, privacyEpoch: Long, onAction: (WordImpostorAction) -> Unit,
) {
    var selected by remember(state.public.token, privacyEpoch) { mutableStateOf<PlayerId?>(null) }
    InPlaceBackHandler(selected != null) { selected = null }
    WordTitle(stringResource(Res.string.wi_voting))
    WordBody(stringResource(Res.string.wi_voted_count, state.public.voted.size, state.players.size),
        Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    WordPanel {
        if (self in state.public.voted) {
            WordEmblem(false, Modifier.align(Alignment.CenterHorizontally))
            WordBody(stringResource(Res.string.wi_vote_recorded))
        } else {
            val target = selected
            if (target == null) {
                WordBody(stringResource(Res.string.wi_voting_hint))
                state.players.filter { it.id != self }.forEach { player ->
                    WordButton(player.displayName, enabled, true) { selected = player.id }
                }
            } else {
                WordTitle(stringResource(Res.string.wi_vote_confirm, state.name(target)))
                WordButton(stringResource(Res.string.wi_lock_vote), enabled) {
                    onAction(WordImpostorAction.Vote(self, state.public.token, target))
                }
                WordButton(stringResource(Res.string.wi_cancel), enabled, true) { selected = null }
            }
        }
    }
}

@Composable
internal fun WordGuessing(
    state: WordImpostorState, self: PlayerId, enabled: Boolean, privacyEpoch: Long, onAction: (WordImpostorAction) -> Unit,
) {
    val own = state.privatePerPlayer.getValue(self)
    // Guesses/role screens do not survive recovery or an interrupted app surface.
    var revealed by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var selected by remember(state.public.token, privacyEpoch) { mutableStateOf<String?>(null) }
    InPlaceBackHandler(revealed || selected != null) {
        if (selected != null) selected = null else revealed = false
    }
    WordTitle(stringResource(Res.string.wi_guessing))
    WordVoteCounts(state)
    WordBody(stringResource(Res.string.wi_guess_count, state.public.guessCount, state.public.settings.impostors))
    WordPanel {
        when {
            !revealed -> {
                WordBody(stringResource(Res.string.wi_reveal_hint))
                WordButton(stringResource(Res.string.wi_reveal), enabled) { revealed = true }
            }
            own.role != WordRole.Impostor -> WordBody(stringResource(Res.string.wi_guess_wait))
            own.guess != null -> WordBody(stringResource(Res.string.wi_guess_recorded))
            else -> {
                val choice = selected
                WordBody(stringResource(Res.string.wi_guess_hint))
                if (choice == null) {
                    own.choices.forEach { id ->
                        WordButton(stringResource(WordContentResources.word(id)), enabled, true) { selected = id }
                    }
                } else {
                    WordTitle(stringResource(Res.string.wi_guess_confirm, stringResource(WordContentResources.word(choice))))
                    WordButton(stringResource(Res.string.wi_lock_guess), enabled) {
                        onAction(WordImpostorAction.Guess(self, state.public.token, choice))
                        revealed = false
                        selected = null
                    }
                    WordButton(stringResource(Res.string.wi_cancel), enabled, true) { selected = null }
                }
            }
        }
        if (revealed) WordButton(stringResource(Res.string.wi_hide), enabled, true) { revealed = false; selected = null }
    }
}

@Composable
private fun WordVoteCounts(state: WordImpostorState) {
    val counts = checkNotNull(state.public.voteCounts)
    WordBody(stringResource(Res.string.wi_vote_counts))
    state.players.sortedByDescending { counts.getValue(it.id) }.forEach { player ->
        WordScoreBar(stringResource(Res.string.wi_vote_count, player.displayName.asBidiArgument(), counts.getValue(player.id)),
            counts.getValue(player.id), state.players.size)
    }
}

@Composable
internal fun WordResults(state: WordImpostorState, host: Boolean, enabled: Boolean, onAction: (WordImpostorAction) -> Unit) {
    val result = checkNotNull(state.public.result)
    WordTitle(stringResource(Res.string.wi_result))
    WordPanel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WordEmblem(true)
            WordBody(stringResource(Res.string.wi_word_was))
            WordTitle(stringResource(WordContentResources.word(result.wordId)))
        }
        WordBody(stringResource(Res.string.wi_impostors_were, state.names(result.impostors)))
        WordBody(stringResource(if (result.ordinaryTeamScored) Res.string.wi_ordinary_point else Res.string.wi_ordinary_no_point))
        state.players.filter { it.id in result.impostors }.forEach { player ->
            WordBody(stringResource(Res.string.wi_guess_result, player.displayName.asBidiArgument(),
                stringResource(WordContentResources.word(result.guesses.getValue(player.id))), result.awarded.getValue(player.id)))
        }
    }
    WordVoteCounts(state)
    if (state.phase == WordImpostorPhase.MatchResult) {
        val high = state.public.scores.values.max()
        WordTitle(stringResource(Res.string.wi_champions,
            state.names(state.players.filter { state.public.scores.getValue(it.id) == high }.map { it.id }.toSet())))
    }
    WordBody(stringResource(Res.string.wi_scoreboard))
    state.players.sortedByDescending { state.public.scores.getValue(it.id) }.forEach { player ->
        WordScoreBar(stringResource(Res.string.wi_score, player.displayName.asBidiArgument(),
            state.public.scores.getValue(player.id), result.awarded.getValue(player.id)),
            state.public.scores.getValue(player.id), state.public.settings.rounds)
    }
    if (host) WordButton(stringResource(if (state.phase == WordImpostorPhase.MatchResult) Res.string.wi_rematch else Res.string.wi_next),
        enabled) {
        onAction(if (state.phase == WordImpostorPhase.MatchResult) WordImpostorAction.Rematch(state.public.token)
            else WordImpostorAction.NextRound(state.public.token))
    } else WordBody(stringResource(Res.string.wi_wait_host))
}

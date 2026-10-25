package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.parlor.games.wordimpostor.resources.wi_answer_hint
import com.parlor.games.wordimpostor.resources.wi_answered
import com.parlor.games.wordimpostor.resources.wi_close_help
import com.parlor.games.wordimpostor.resources.wi_discussion
import com.parlor.games.wordimpostor.resources.wi_discussion_hint
import com.parlor.games.wordimpostor.resources.wi_help
import com.parlor.games.wordimpostor.resources.wi_hide
import com.parlor.games.wordimpostor.resources.wi_host_controls
import com.parlor.games.wordimpostor.resources.wi_impostor
import com.parlor.games.wordimpostor.resources.wi_impostor_hint
import com.parlor.games.wordimpostor.resources.wi_leave
import com.parlor.games.wordimpostor.resources.wi_open_vote
import com.parlor.games.wordimpostor.resources.wi_ordinary
import com.parlor.games.wordimpostor.resources.wi_pair
import com.parlor.games.wordimpostor.resources.wi_question_progress
import com.parlor.games.wordimpostor.resources.wi_question_prompt
import com.parlor.games.wordimpostor.resources.wi_question_wait
import com.parlor.games.wordimpostor.resources.wi_questions
import com.parlor.games.wordimpostor.resources.wi_ready
import com.parlor.games.wordimpostor.resources.wi_ready_count
import com.parlor.games.wordimpostor.resources.wi_reveal
import com.parlor.games.wordimpostor.resources.wi_reveal_hint
import com.parlor.games.wordimpostor.resources.wi_reveal_title
import com.parlor.games.wordimpostor.resources.wi_round
import com.parlor.games.wordimpostor.resources.wi_rules
import com.parlor.games.wordimpostor.resources.wi_solo_impostor
import com.parlor.games.wordimpostor.resources.wi_teammates
import com.parlor.games.wordimpostor.resources.wi_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun WordImpostorTable(
    state: WordImpostorState, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
    onAction: (WordImpostorAction) -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier,
) {
    var help by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    InPlaceBackHandler(help) { help = false }
    WordFrame(modifier) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLeave, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.wi_leave), color = WordPeach)
            }
            TextButton(onClick = { help = !help }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (help) Res.string.wi_close_help else Res.string.wi_help), color = WordPeach)
            }
        }
        WordBody(stringResource(Res.string.wi_round, state.public.round, state.public.settings.rounds))
        WordPhaseTrack(state.phase.ordinal.coerceAtMost(5))
        if (help) {
            WordTitle(stringResource(Res.string.wi_title))
            WordBody(stringResource(Res.string.wi_rules))
        } else {
            when (state.phase) {
                WordImpostorPhase.Reveal -> WordSecretCard(state, self, enabled, privacyEpoch, true, onAction)
                WordImpostorPhase.Questions -> WordQuestions(state, self, enabled, onAction)
                WordImpostorPhase.Discussion -> WordDiscussion(state, isHost, enabled, onAction)
                WordImpostorPhase.Voting -> WordVoting(state, self, enabled, privacyEpoch, onAction)
                WordImpostorPhase.Guessing -> WordGuessing(state, self, enabled, privacyEpoch, onAction)
                WordImpostorPhase.RoundResult, WordImpostorPhase.MatchResult -> WordResults(state, isHost, enabled, onAction)
                WordImpostorPhase.Aborted -> Unit // Public-only termination is owned by the shared session shell.
            }
            if (state.phase in listOf(WordImpostorPhase.Questions, WordImpostorPhase.Discussion, WordImpostorPhase.Voting)) {
                WordSecretCard(state, self, enabled, privacyEpoch, false, onAction)
            }
        }
    }
}

@Composable
private fun WordSecretCard(
    state: WordImpostorState, self: PlayerId, enabled: Boolean, privacyEpoch: Long, readiness: Boolean,
    onAction: (WordImpostorAction) -> Unit,
) {
    // Deliberately not saveable: recovery, backgrounding and phase changes re-cover the secret.
    var revealed by remember(state.public.token, privacyEpoch, state.phase) { mutableStateOf(false) }
    InPlaceBackHandler(revealed) { revealed = false }
    val own = state.privatePerPlayer.getValue(self)
    WordPanel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WordEmblem(revealed)
            if (!revealed) {
                WordTitle(stringResource(Res.string.wi_reveal_title))
                WordBody(stringResource(Res.string.wi_reveal_hint))
                WordButton(stringResource(Res.string.wi_reveal), enabled) { revealed = true }
            } else {
                if (own.role == WordRole.Impostor) {
                    WordTitle(stringResource(Res.string.wi_impostor))
                    WordBody(stringResource(Res.string.wi_impostor_hint))
                    WordBody(if (own.teammates.isEmpty()) stringResource(Res.string.wi_solo_impostor) else
                        stringResource(Res.string.wi_teammates, state.names(own.teammates)))
                } else {
                    WordBody(stringResource(Res.string.wi_ordinary))
                    WordTitle(stringResource(WordContentResources.word(checkNotNull(own.wordId))))
                }
                if (readiness && self !in state.public.ready) {
                    WordButton(stringResource(Res.string.wi_ready), enabled) {
                        revealed = false
                        onAction(WordImpostorAction.Ready(self, state.public.token))
                    }
                } else WordButton(stringResource(Res.string.wi_hide), enabled) { revealed = false }
            }
            if (readiness) WordBody(stringResource(Res.string.wi_ready_count, state.public.ready.size, state.players.size))
        }
    }
}

@Composable
private fun WordQuestions(state: WordImpostorState, self: PlayerId, enabled: Boolean, onAction: (WordImpostorAction) -> Unit) {
    val pair = state.public.interactions[state.public.questionIndex]
    WordBody(stringResource(Res.string.wi_questions))
    WordTitle(stringResource(Res.string.wi_pair, state.name(pair.asker), state.name(pair.answerer)))
    WordBody(stringResource(Res.string.wi_question_progress, state.public.questionIndex + 1, state.players.size),
        Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    WordPanel {
        if (self == pair.asker) {
            WordBody(stringResource(Res.string.wi_question_prompt))
            WordTitle(stringResource(WordContentResources.question(checkNotNull(state.privatePerPlayer[self]?.questionId))))
            WordButton(stringResource(Res.string.wi_answered), enabled) {
                onAction(WordImpostorAction.Answered(self, state.public.token, state.public.questionIndex))
            }
        } else {
            WordEmblem(false, Modifier.align(Alignment.CenterHorizontally))
            WordBody(stringResource(if (self == pair.answerer) Res.string.wi_answer_hint else Res.string.wi_question_wait))
        }
    }
}

@Composable
private fun WordDiscussion(state: WordImpostorState, host: Boolean, enabled: Boolean, onAction: (WordImpostorAction) -> Unit) {
    WordTitle(stringResource(Res.string.wi_discussion))
    WordPanel {
        WordBody(stringResource(Res.string.wi_discussion_hint))
        if (host) WordButton(stringResource(Res.string.wi_open_vote), enabled) {
            onAction(WordImpostorAction.OpenVoting(state.public.token))
        } else WordBody(stringResource(Res.string.wi_host_controls))
    }
}

internal fun WordImpostorState.name(id: PlayerId): String = players.first { it.id == id }.displayName.asBidiArgument()
internal fun WordImpostorState.names(ids: Set<PlayerId>): String =
    players.filter { it.id in ids }.joinToString(" · ") { it.displayName.asBidiArgument() }

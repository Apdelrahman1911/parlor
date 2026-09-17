package com.parlor.games.ghamza.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaRole
import com.parlor.games.ghamza.domain.GhamzaRules
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_active
import com.parlor.games.ghamza.resources.gh_attempts_left
import com.parlor.games.ghamza.resources.gh_cancel
import com.parlor.games.ghamza.resources.gh_circle
import com.parlor.games.ghamza.resources.gh_close_help
import com.parlor.games.ghamza.resources.gh_confirm
import com.parlor.games.ghamza.resources.gh_confirm_body
import com.parlor.games.ghamza.resources.gh_confirm_title
import com.parlor.games.ghamza.resources.gh_eliminated
import com.parlor.games.ghamza.resources.gh_final
import com.parlor.games.ghamza.resources.gh_final_wait
import com.parlor.games.ghamza.resources.gh_final_you
import com.parlor.games.ghamza.resources.gh_guess_confirm
import com.parlor.games.ghamza.resources.gh_guess_result
import com.parlor.games.ghamza.resources.gh_guess_submit
import com.parlor.games.ghamza.resources.gh_guest
import com.parlor.games.ghamza.resources.gh_guest_hint
import com.parlor.games.ghamza.resources.gh_help
import com.parlor.games.ghamza.resources.gh_hide
import com.parlor.games.ghamza.resources.gh_leave
import com.parlor.games.ghamza.resources.gh_loser
import com.parlor.games.ghamza.resources.gh_match_complete
import com.parlor.games.ghamza.resources.gh_next_lives
import com.parlor.games.ghamza.resources.gh_next
import com.parlor.games.ghamza.resources.gh_out
import com.parlor.games.ghamza.resources.gh_ready
import com.parlor.games.ghamza.resources.gh_ready_count
import com.parlor.games.ghamza.resources.gh_rematch
import com.parlor.games.ghamza.resources.gh_result
import com.parlor.games.ghamza.resources.gh_reveal
import com.parlor.games.ghamza.resources.gh_reveal_hint
import com.parlor.games.ghamza.resources.gh_reveal_title
import com.parlor.games.ghamza.resources.gh_revealed
import com.parlor.games.ghamza.resources.gh_round
import com.parlor.games.ghamza.resources.gh_rules
import com.parlor.games.ghamza.resources.gh_social
import com.parlor.games.ghamza.resources.gh_social_hint
import com.parlor.games.ghamza.resources.gh_status
import com.parlor.games.ghamza.resources.gh_title
import com.parlor.games.ghamza.resources.gh_wait_host
import com.parlor.games.ghamza.resources.gh_winked
import com.parlor.games.ghamza.resources.gh_winker
import com.parlor.games.ghamza.resources.gh_winker_hint
import com.parlor.games.ghamza.resources.gh_winner
import org.jetbrains.compose.resources.stringResource

@Composable
fun GhamzaTable(
    state: GhamzaState, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
    onAction: (GhamzaAction) -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier,
) {
    var help by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    InPlaceBackHandler(help) { help = false }
    GhamzaFrame(modifier) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLeave, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.gh_leave), color = GhamzaMint)
            }
            TextButton(onClick = { help = !help }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (help) Res.string.gh_close_help else Res.string.gh_help), color = GhamzaMint)
            }
        }
        GhamzaBody(stringResource(Res.string.gh_round, state.public.round, state.public.settings.rounds))
        if (help) {
            GhamzaTitle(stringResource(Res.string.gh_title))
            GhamzaBody(stringResource(Res.string.gh_rules))
        } else when (state.phase) {
            GhamzaPhase.Reveal -> GhamzaRoleCard(state, self, enabled, privacyEpoch, true, onAction)
            GhamzaPhase.Social -> GhamzaSocial(state, self, enabled, privacyEpoch, onAction)
            GhamzaPhase.FinalGuess -> GhamzaFinalGuess(state, self, enabled, privacyEpoch, onAction)
            GhamzaPhase.RoundResult, GhamzaPhase.MatchResult -> GhamzaResults(state, isHost, enabled, onAction)
            GhamzaPhase.Aborted -> Unit // The session shell replaces aborted games with a public-only terminal surface.
        }
    }
}

@Composable
private fun GhamzaRoleCard(
    state: GhamzaState, self: PlayerId, enabled: Boolean, privacyEpoch: Long, readiness: Boolean,
    onAction: (GhamzaAction) -> Unit,
) {
    var revealed by remember(state.public.token, privacyEpoch, state.phase) { mutableStateOf(false) }
    InPlaceBackHandler(revealed) { revealed = false }
    Column(Modifier.fillMaxWidth().background(GhamzaInk.copy(alpha = .7f), RoundedCornerShape(28.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GhamzaEye(revealed)
        if (!revealed) {
            GhamzaTitle(stringResource(Res.string.gh_reveal_title))
            GhamzaBody(stringResource(Res.string.gh_reveal_hint))
            GhamzaButton(stringResource(Res.string.gh_reveal), enabled) { revealed = true }
        } else {
            val winker = state.privatePerPlayer[self]?.role == GhamzaRole.Winker
            GhamzaTitle(stringResource(if (winker) Res.string.gh_winker else Res.string.gh_guest))
            GhamzaBody(stringResource(if (winker) Res.string.gh_winker_hint else Res.string.gh_guest_hint))
            if (readiness && self !in state.public.ready) {
                GhamzaButton(stringResource(Res.string.gh_ready), enabled) {
                    revealed = false
                    onAction(GhamzaAction.Ready(self, state.public.token))
                }
            } else GhamzaButton(stringResource(Res.string.gh_hide), enabled) { revealed = false }
        }
        if (readiness) GhamzaBody(stringResource(Res.string.gh_ready_count, state.public.ready.size, state.players.size))
    }
}

@Composable
private fun GhamzaSocial(state: GhamzaState, self: PlayerId, enabled: Boolean, privacyEpoch: Long, onAction: (GhamzaAction) -> Unit) {
    var confirming by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    InPlaceBackHandler(confirming) { confirming = false }
    val used = state.public.reports.getValue(self)
    if (confirming) {
        GhamzaTitle(stringResource(Res.string.gh_confirm_title))
        GhamzaBody(stringResource(Res.string.gh_confirm_body))
        GhamzaButton(stringResource(Res.string.gh_confirm), enabled) {
            confirming = false
            onAction(GhamzaAction.Winked(self, state.public.token, used + 1))
        }
        GhamzaButton(stringResource(Res.string.gh_cancel), enabled, true) { confirming = false }
        return
    }
    GhamzaTitle(stringResource(Res.string.gh_social))
    GhamzaBody(stringResource(Res.string.gh_social_hint))
    if (state.privatePerPlayer[self]?.role == GhamzaRole.Guest) {
        if (used < state.public.settings.attempts) {
            GhamzaBody(stringResource(Res.string.gh_attempts_left, state.public.settings.attempts - used))
            GhamzaButton(stringResource(Res.string.gh_winked), enabled) { confirming = true }
        } else GhamzaBody(stringResource(Res.string.gh_out))
    }
    GhamzaReportFeed(state)
    GhamzaTitle(stringResource(Res.string.gh_circle))
    state.players.forEach { player ->
        val remaining = GhamzaRules.livesRemaining(state, player.id)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                GhamzaBody(stringResource(Res.string.gh_status, player.displayName.asBidiArgument(), remaining))
                Text(stringResource(if (remaining > 0) Res.string.gh_active else Res.string.gh_eliminated),
                    color = GhamzaMint.copy(alpha = .8f), style = ParlorTheme.typography.bodyMedium)
            }
            GhamzaAttemptMarks(remaining, state.public.settings.attempts)
        }
    }
    GhamzaRoleCard(state, self, enabled, privacyEpoch, false, onAction)
}

@Composable
private fun GhamzaFinalGuess(state: GhamzaState, self: PlayerId, enabled: Boolean, privacyEpoch: Long, onAction: (GhamzaAction) -> Unit) {
    var selected by remember(state.public.token, privacyEpoch) { mutableStateOf<PlayerId?>(null) }
    InPlaceBackHandler(selected != null) { selected = null }
    GhamzaTitle(stringResource(Res.string.gh_final))
    // Keep the report that triggered the final stage visible to every seat.
    GhamzaReportFeed(state)
    if (state.public.finalGuesser != self) {
        GhamzaBody(stringResource(Res.string.gh_final_wait, state.name(checkNotNull(state.public.finalGuesser))))
    } else {
        val target = selected
        if (target == null) {
            GhamzaBody(stringResource(Res.string.gh_final_you))
            state.players.filter { it.id != self }.forEach { player ->
                GhamzaButton(player.displayName, enabled, true) { selected = player.id }
            }
        } else {
            GhamzaBody(stringResource(Res.string.gh_guess_confirm, state.name(target)))
            GhamzaButton(stringResource(Res.string.gh_guess_submit), enabled) {
                onAction(GhamzaAction.Guess(self, state.public.token, target))
            }
            GhamzaButton(stringResource(Res.string.gh_cancel), enabled, true) { selected = null }
        }
    }
}

@Composable
private fun GhamzaResults(state: GhamzaState, host: Boolean, enabled: Boolean, onAction: (GhamzaAction) -> Unit) {
    val result = checkNotNull(state.public.result)
    GhamzaTitle(stringResource(Res.string.gh_result))
    GhamzaEye(true, Modifier.fillMaxWidth())
    GhamzaTitle(stringResource(Res.string.gh_winner, state.name(result.winner)))
    GhamzaBody(stringResource(Res.string.gh_loser, state.name(result.loser)))
    GhamzaBody(stringResource(Res.string.gh_revealed, state.name(result.winker)))
    GhamzaBody(stringResource(Res.string.gh_guess_result, state.name(result.guesser), state.name(result.guessed)))
    val matchFinished = state.phase == GhamzaPhase.MatchResult
    if (matchFinished) GhamzaTitle(stringResource(Res.string.gh_match_complete))
    GhamzaBody(stringResource(Res.string.gh_next_lives))
    if (host) GhamzaButton(stringResource(if (matchFinished) Res.string.gh_rematch else Res.string.gh_next), enabled) {
        onAction(if (matchFinished) GhamzaAction.Rematch(state.public.token) else GhamzaAction.NextRound(state.public.token))
    } else GhamzaBody(stringResource(Res.string.gh_wait_host))
}

private fun GhamzaState.name(id: PlayerId): String = players.first { it.id == id }.displayName.asBidiArgument()

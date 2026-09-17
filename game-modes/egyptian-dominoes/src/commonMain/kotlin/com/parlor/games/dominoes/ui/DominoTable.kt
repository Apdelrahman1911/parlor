package com.parlor.games.dominoes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_close_help
import com.parlor.games.dominoes.resources.dom_help
import com.parlor.games.dominoes.resources.dom_leave
import com.parlor.games.dominoes.resources.dom_round
import com.parlor.games.dominoes.resources.dom_rules
import com.parlor.games.dominoes.resources.dom_title
import com.parlor.games.dominoes.resources.dom_turn_other
import com.parlor.games.dominoes.resources.dom_turn_you
import org.jetbrains.compose.resources.stringResource

@Composable
fun DominoTable(
    state: DominoState, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
    onAction: (DominoAction) -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier,
) {
    var help by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var visible by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    InPlaceBackHandler(help || visible) { if (help) help = false else visible = false }
    val motion = rememberDominoMotion(state, self, privacyEpoch, visible && !help)
    val canAct = enabled && motion.flight == null
    Box(modifier.fillMaxSize().onGloballyPositioned { motion.rootOrigin = it.positionInRoot() }) {
        DominoFrame(Modifier.fillMaxSize()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLeave, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.dom_leave), color = DominoGold)
                }
                TextButton(onClick = { help = !help }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (help) Res.string.dom_close_help else Res.string.dom_help), color = DominoGold)
                }
            }
            if (help) {
                DominoTitle(stringResource(Res.string.dom_title))
                DominoBody(stringResource(Res.string.dom_rules))
            } else {
                DominoBody(stringResource(Res.string.dom_round, state.public.round))
                DominoOpponents(state, self, motion)
                if (state.phase == DominoPhase.Playing) {
                    DominoBody(if (state.public.turn == self) stringResource(Res.string.dom_turn_you) else
                        stringResource(Res.string.dom_turn_other, state.name(checkNotNull(state.public.turn))),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                DominoBoard(state, motion)
                if (state.phase == DominoPhase.Playing) {
                    DominoStock(state.public.stockCount, motion)
                    DominoHand(state, self, canAct, visible, motion, { visible = it }, onAction)
                } else if (state.phase == DominoPhase.RoundResult || state.phase == DominoPhase.MatchResult) {
                    DominoResults(state, isHost, enabled, onAction)
                }
            }
        }
        if (!help) DominoFlightOverlay(motion, Modifier.align(AbsoluteAlignment.TopLeft))
    }
}

internal fun DominoState.name(id: PlayerId): String = players.first { it.id == id }.displayName.asBidiArgument()

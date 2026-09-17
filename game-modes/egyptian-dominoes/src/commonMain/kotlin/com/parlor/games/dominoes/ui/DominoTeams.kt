package com.parlor.games.dominoes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoScoring
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_team_name
import com.parlor.games.dominoes.resources.dom_team_summary
import com.parlor.games.dominoes.resources.dom_your_team
import org.jetbrains.compose.resources.stringResource

internal fun dominoSideColor(team: Int?): Color = if (team == 2) Color(0xFFA6D4F1) else DominoGold

@Composable
internal fun dominoSideName(state: DominoState, player: PlayerId): String =
    DominoScoring.teamNumber(state, player)?.let { stringResource(Res.string.dom_team_name, it) } ?: state.name(player)

@Composable
internal fun DominoTeams(state: DominoState, self: PlayerId) {
    if (state.public.settings.competition != DominoCompetition.Teams) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DominoScoring.sides(state.players, state.public.settings).forEach { (side, members) ->
            val color = dominoSideColor(DominoScoring.teamNumber(state, side))
            Column(Modifier.fillMaxWidth().background(DominoInk.copy(alpha = .6f), RoundedCornerShape(16.dp))
                .border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val title = stringResource(Res.string.dom_team_summary, dominoSideName(state, side),
                    state.public.scores.getValue(side))
                Text(if (self in members) stringResource(Res.string.dom_your_team, title) else title,
                    color = color, style = ParlorTheme.typography.labelLarge)
                DominoBody(members.joinToString(" · ") { state.name(it) })
            }
        }
    }
}

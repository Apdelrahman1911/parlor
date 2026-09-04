package com.parlor.games.mafia.ui.screens.postgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.postgame_eyebrow
import com.parlor.games.mafia.resources.postgame_ended_without_winner
import com.parlor.games.mafia.resources.postgame_exit
import com.parlor.games.mafia.resources.postgame_exit_description
import com.parlor.games.mafia.resources.postgame_mafia_wins
import com.parlor.games.mafia.resources.postgame_roles_card
import com.parlor.games.mafia.resources.postgame_town_wins
import com.parlor.games.mafia.ui.screens.reveal.roleDisplayName
import org.jetbrains.compose.resources.stringResource

@Composable
fun PostGameScreen(
    winner: Team?,
    finalRoles: List<Pair<String, Role>>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.postgame_eyebrow), textAlign = TextAlign.Center)
            Text(
                text = winLine(winner),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                ) {
                    EyebrowLabel(text = stringResource(Res.string.postgame_roles_card), accent = false)
                    finalRoles.forEach { (name, role) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = name,
                                style = ParlorTheme.typography.bodyLarge,
                                color = ParlorTheme.colors.textPrimary,
                            )
                            Text(
                                text = roleDisplayName(role),
                                style = ParlorTheme.typography.bodyLarge,
                                color = ParlorTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }

            ParlorButton(
                label = stringResource(Res.string.postgame_exit),
                contentDescription = stringResource(Res.string.postgame_exit_description),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun winLine(team: Team?): String = when (team) {
    Team.Mafia -> stringResource(Res.string.postgame_mafia_wins)
    Team.Town -> stringResource(Res.string.postgame_town_wins)
    null -> stringResource(Res.string.postgame_ended_without_winner)
}

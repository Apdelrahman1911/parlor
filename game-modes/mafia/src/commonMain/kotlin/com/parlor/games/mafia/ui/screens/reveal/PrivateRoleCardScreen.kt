package com.parlor.games.mafia.ui.screens.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.reveal_ack
import com.parlor.games.mafia.resources.reveal_ack_description
import com.parlor.games.mafia.resources.reveal_your_job
import com.parlor.games.mafia.resources.reveal_your_teammates
import com.parlor.games.mafia.resources.role_civilian
import com.parlor.games.mafia.resources.role_civilian_brief
import com.parlor.games.mafia.resources.role_detective
import com.parlor.games.mafia.resources.role_detective_brief
import com.parlor.games.mafia.resources.role_doctor
import com.parlor.games.mafia.resources.role_doctor_brief
import com.parlor.games.mafia.resources.role_mafia
import com.parlor.games.mafia.resources.role_mafia_brief
import com.parlor.games.mafia.resources.team_mafia
import com.parlor.games.mafia.resources.team_town
import com.parlor.games.mafia.resources.handoff_player_only_format
import com.parlor.games.mafia.resources.private_screen_label
import org.jetbrains.compose.resources.stringResource

/**
 * Shows the active player their role, team, and (for Mafia) their teammates.
 * Confirmation triggers [onAcknowledged] which the flow translates to
 * `AcknowledgeRoleViewed(playerId)` — the device then moves to [MafiaHideAndPassScreen].
 */
@Composable
fun PrivateRoleCardScreen(
    playerName: String,
    role: Role,
    team: Team,
    knownTeammateNames: List<String>,
    onAcknowledged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            ContextRibbon(
                label = stringResource(Res.string.private_screen_label),
                detail = stringResource(Res.string.handoff_player_only_format, playerName),
                tone = ParlorContextTone.Private,
            )
            EyebrowLabel(text = playerName, textAlign = TextAlign.Center)
            Text(
                text = roleDisplayName(role),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = teamDisplayName(team),
                style = ParlorTheme.typography.headingMedium,
                color = ParlorTheme.colors.accentEmber,
                textAlign = TextAlign.Center,
            )

            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                ) {
                    EyebrowLabel(text = stringResource(Res.string.reveal_your_job), accent = false)
                    Text(
                        text = roleBriefDescription(role),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textPrimary,
                    )
                }
            }

            if (role == Role.Mafia && knownTeammateNames.isNotEmpty()) {
                ParlorCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                    ) {
                        EyebrowLabel(text = stringResource(Res.string.reveal_your_teammates), accent = false)
                        knownTeammateNames.forEach { name ->
                            Text(
                                text = name,
                                style = ParlorTheme.typography.bodyLarge,
                                color = ParlorTheme.colors.textPrimary,
                            )
                        }
                    }
                }
            }

            ParlorButton(
                label = stringResource(Res.string.reveal_ack),
                contentDescription = stringResource(Res.string.reveal_ack_description),
                onClick = onAcknowledged,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun roleDisplayName(role: Role): String = when (role) {
    Role.Mafia -> stringResource(Res.string.role_mafia)
    Role.Detective -> stringResource(Res.string.role_detective)
    Role.Doctor -> stringResource(Res.string.role_doctor)
    Role.Civilian -> stringResource(Res.string.role_civilian)
}

@Composable
internal fun teamDisplayName(team: Team): String = when (team) {
    Team.Mafia -> stringResource(Res.string.team_mafia)
    Team.Town -> stringResource(Res.string.team_town)
}

@Composable
internal fun roleBriefDescription(role: Role): String = when (role) {
    Role.Mafia -> stringResource(Res.string.role_mafia_brief)
    Role.Detective -> stringResource(Res.string.role_detective_brief)
    Role.Doctor -> stringResource(Res.string.role_doctor_brief)
    Role.Civilian -> stringResource(Res.string.role_civilian_brief)
}

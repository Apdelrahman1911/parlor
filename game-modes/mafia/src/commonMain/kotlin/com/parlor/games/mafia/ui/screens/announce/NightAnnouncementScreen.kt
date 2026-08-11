package com.parlor.games.mafia.ui.screens.announce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_announcement_continue
import com.parlor.games.mafia.resources.night_announcement_continue_description
import com.parlor.games.mafia.resources.night_announcement_eyebrow_format
import com.parlor.games.mafia.resources.night_announcement_killed_format
import com.parlor.games.mafia.resources.night_announcement_no_kill
import com.parlor.games.mafia.resources.night_announcement_revealed_role_format
import com.parlor.games.mafia.resources.night_announcement_saved
import com.parlor.games.mafia.ui.screens.reveal.roleDisplayName
import org.jetbrains.compose.resources.stringResource

/**
 * Public announcement of last night's outcome. Shown together to everyone:
 * the victim's name (or "no one died") and, only if `revealRoleOnDeath`,
 * their role.
 *
 * The Detective's private inspection result is delivered SEPARATELY on a
 * private handoff segment — it must NEVER appear here.
 */
@Composable
fun NightAnnouncementScreen(
    day: Int,
    killedPlayerName: String?,
    revealedRole: Role?,
    wasSaved: Boolean,
    onAcknowledged: (() -> Unit)?,
    waitingLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.night_announcement_eyebrow_format, day),
                textAlign = TextAlign.Center,
            )
            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val headline = when {
                        killedPlayerName != null -> stringResource(
                            Res.string.night_announcement_killed_format,
                            killedPlayerName,
                        )
                        wasSaved -> stringResource(Res.string.night_announcement_saved)
                        else -> stringResource(Res.string.night_announcement_no_kill)
                    }
                    Text(
                        text = headline,
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    if (killedPlayerName != null && revealedRole != null) {
                        Text(
                            text = stringResource(
                                Res.string.night_announcement_revealed_role_format,
                                roleDisplayName(revealedRole),
                            ),
                            style = ParlorTheme.typography.bodyLarge,
                            color = ParlorTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (onAcknowledged != null) {
                ParlorButton(
                    label = stringResource(Res.string.night_announcement_continue),
                    contentDescription = stringResource(
                        Res.string.night_announcement_continue_description,
                    ),
                    onClick = onAcknowledged,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (waitingLabel != null) {
                Text(
                    text = waitingLabel,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

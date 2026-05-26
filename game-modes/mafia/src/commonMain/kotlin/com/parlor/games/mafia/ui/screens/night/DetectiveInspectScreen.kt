package com.parlor.games.mafia.ui.screens.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_detective_eyebrow_format
import com.parlor.games.mafia.resources.night_detective_headline
import com.parlor.games.mafia.resources.night_detective_instructions
import com.parlor.games.mafia.resources.night_detective_result_ack
import com.parlor.games.mafia.resources.night_detective_result_ack_description
import com.parlor.games.mafia.resources.night_detective_result_eyebrow_format
import com.parlor.games.mafia.resources.night_detective_result_keep_private
import com.parlor.games.mafia.resources.night_detective_result_mafia_format
import com.parlor.games.mafia.resources.night_detective_result_town_format
import com.parlor.games.mafia.resources.night_detective_skip
import com.parlor.games.mafia.resources.night_detective_submit
import org.jetbrains.compose.resources.stringResource

@Composable
fun DetectiveInspectScreen(
    detectiveName: String,
    targets: List<PickableTarget>,
    onSubmit: (PlayerId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    TargetPickerScreen(
        eyebrow = stringResource(Res.string.night_detective_eyebrow_format, detectiveName),
        headline = stringResource(Res.string.night_detective_headline),
        instructions = stringResource(Res.string.night_detective_instructions),
        targets = targets,
        submitLabel = stringResource(Res.string.night_detective_submit),
        onSubmit = onSubmit,
        allowSkip = true,
        skipLabel = stringResource(Res.string.night_detective_skip),
        modifier = modifier,
    )
}

/**
 * Private result delivered to the Detective after the night resolves. The
 * flow gates this behind a [com.parlor.games.mafia.ui.screens.handoff.MafiaRoleRevealHandoffScreen]
 * + [com.parlor.games.mafia.ui.screens.handoff.MafiaRoleRevealGateScreen] so
 * non-detectives never glimpse the result.
 */
@Composable
fun DetectiveResultScreen(
    detectiveName: String,
    inspectedName: String,
    seesAs: DetectiveSeesAs,
    onAcknowledged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val verdict = when (seesAs) {
        DetectiveSeesAs.Mafia -> stringResource(Res.string.night_detective_result_mafia_format, inspectedName)
        DetectiveSeesAs.Town -> stringResource(Res.string.night_detective_result_town_format, inspectedName)
    }
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.night_detective_result_eyebrow_format, detectiveName),
                textAlign = TextAlign.Center,
            )
            Text(
                text = verdict,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.night_detective_result_keep_private),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.night_detective_result_ack),
                contentDescription = stringResource(Res.string.night_detective_result_ack_description),
                onClick = onAcknowledged,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

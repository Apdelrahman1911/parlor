package com.parlor.games.whodunit.ui.screens.postgame

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
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.postgame_back_to_library
import com.parlor.games.whodunit.resources.postgame_back_to_library_description
import com.parlor.games.whodunit.resources.postgame_eyebrow
import com.parlor.games.whodunit.resources.postgame_question
import com.parlor.games.whodunit.resources.postgame_replay
import com.parlor.games.whodunit.resources.postgame_replay_description
import com.parlor.games.whodunit.resources.postgame_try_other_mode
import com.parlor.games.whodunit.resources.postgame_try_other_mode_description
import org.jetbrains.compose.resources.stringResource

@Composable
fun PostGameScreen(
    onReplaySameCase: () -> Unit,
    onTryOtherMode: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.postgame_eyebrow),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.postgame_question),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.postgame_replay),
                contentDescription = stringResource(Res.string.postgame_replay_description),
                onClick = onReplaySameCase,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.postgame_try_other_mode),
                contentDescription = stringResource(Res.string.postgame_try_other_mode_description),
                onClick = onTryOtherMode,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.postgame_back_to_library),
                contentDescription = stringResource(Res.string.postgame_back_to_library_description),
                onClick = onBackToLibrary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

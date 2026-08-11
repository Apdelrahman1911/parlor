package com.parlor.app.shell.home

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.local_resume_failure_back
import com.parlor.app.resources.local_resume_failure_back_description
import com.parlor.app.resources.local_resume_failure_body
import com.parlor.app.resources.local_resume_failure_discard
import com.parlor.app.resources.local_resume_failure_discard_description
import com.parlor.app.resources.local_resume_failure_eyebrow
import com.parlor.app.resources.local_resume_failure_retry
import com.parlor.app.resources.local_resume_failure_retry_description
import com.parlor.app.resources.local_resume_failure_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/** Global recovery surface for failures outside any game-specific decoder. */
@Composable
internal fun LocalResumeFailureScreen(
    actionsEnabled: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.local_resume_failure_eyebrow))
            Text(
                text = stringResource(Res.string.local_resume_failure_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.local_resume_failure_body),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.local_resume_failure_retry),
                contentDescription = stringResource(
                    Res.string.local_resume_failure_retry_description,
                ),
                enabled = actionsEnabled,
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.local_resume_failure_discard),
                contentDescription = stringResource(
                    Res.string.local_resume_failure_discard_description,
                ),
                enabled = actionsEnabled,
                onClick = onDiscard,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.local_resume_failure_back),
                contentDescription = stringResource(
                    Res.string.local_resume_failure_back_description,
                ),
                enabled = actionsEnabled,
                onClick = onBack,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

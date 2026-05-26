package com.parlor.games.mafia.ui.screens.night

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme

data class PickableTarget(
    val id: PlayerId,
    val name: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
)

/**
 * Generic target picker used by Mafia kill, Doctor protect, Detective inspect,
 * and Civilian suspicion screens. Caller passes a list of [PickableTarget]s
 * and is invoked with the chosen id (or `null` if [allowSkip]).
 */
@Composable
fun TargetPickerScreen(
    eyebrow: String,
    headline: String,
    instructions: String,
    targets: List<PickableTarget>,
    submitLabel: String,
    onSubmit: (PlayerId?) -> Unit,
    modifier: Modifier = Modifier,
    allowSkip: Boolean = false,
    skipLabel: String,
    footer: @Composable (() -> Unit)? = null,
) {
    var selected: PlayerId? by remember { mutableStateOf(null) }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = eyebrow, textAlign = TextAlign.Center)
            Text(
                text = headline,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = instructions,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            footer?.invoke()

            targets.forEach { target ->
                TargetRow(
                    target = target,
                    selected = target.id == selected,
                    onClick = { if (target.enabled) selected = target.id },
                )
            }

            ParlorButton(
                label = submitLabel,
                contentDescription = submitLabel,
                onClick = { onSubmit(selected) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (allowSkip) {
                ParlorButton(
                    label = skipLabel,
                    contentDescription = skipLabel,
                    onClick = { onSubmit(null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TargetRow(
    target: PickableTarget,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    val borderColor = if (selected) ParlorTheme.colors.accentEmber else ParlorTheme.colors.borderSubtle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ParlorTheme.colors.surfaceElevated)
            .border(ParlorTheme.borders.hairline, borderColor, shape)
            .clickable(enabled = target.enabled, onClick = onClick)
            .padding(ParlorTheme.spacing.l),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
            Text(
                text = target.name,
                style = ParlorTheme.typography.bodyLarge,
                color = if (target.enabled) ParlorTheme.colors.textPrimary else ParlorTheme.colors.textSecondary,
            )
            target.subtitle?.let {
                Text(
                    text = it,
                    style = ParlorTheme.typography.bodySmall,
                    color = ParlorTheme.colors.textSecondary,
                )
            }
        }
    }
}

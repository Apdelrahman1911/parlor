package com.parlor.games.mafia.ui.screens.night

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.private_do_not_pass
import com.parlor.games.mafia.resources.private_screen_label
import org.jetbrains.compose.resources.stringResource

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
    val selectableTargetIds = targets.filter(PickableTarget::enabled).map(PickableTarget::id)
    var selected: PlayerId? by remember(selectableTargetIds) { mutableStateOf(null) }
    val validSelection = validTargetSelection(selected, targets)

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContextRibbon(
                label = stringResource(Res.string.private_screen_label),
                detail = stringResource(Res.string.private_do_not_pass),
                tone = ParlorContextTone.Private,
            )
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                targets.forEach { target ->
                    TargetRow(
                        target = target,
                        selected = target.id == validSelection,
                        onClick = { selected = target.id },
                    )
                }
            }

            ParlorButton(
                label = submitLabel,
                contentDescription = submitLabel,
                onClick = { onSubmit(validSelection) },
                enabled = validSelection != null,
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

/** A removed or newly-disabled target can never reach the submit callback. */
internal fun validTargetSelection(
    selected: PlayerId?,
    targets: List<PickableTarget>,
): PlayerId? = selected?.takeIf { selectedId ->
    targets.any { target -> target.id == selectedId && target.enabled }
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
            .selectable(
                selected = selected,
                enabled = target.enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
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

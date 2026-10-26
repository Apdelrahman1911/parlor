package com.parlor.app.shell.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.app.resources.settings_language_help
import com.parlor.app.resources.settings_appearance_help
import com.parlor.app.resources.settings_accessibility_help
import com.parlor.app.resources.Res
import com.parlor.app.resources.settings_appearance_dark
import com.parlor.app.resources.settings_appearance_label
import com.parlor.app.resources.settings_appearance_light
import com.parlor.app.resources.settings_appearance_system
import com.parlor.app.resources.settings_experience_label
import com.parlor.app.resources.settings_eyebrow
import com.parlor.app.resources.settings_language_arabic
import com.parlor.app.resources.settings_language_english
import com.parlor.app.resources.settings_language_label
import com.parlor.app.resources.settings_language_system
import com.parlor.app.resources.settings_reduced_motion_description
import com.parlor.app.resources.settings_reduced_motion_title
import com.parlor.app.resources.settings_save_failed
import com.parlor.app.resources.settings_subtitle
import com.parlor.app.resources.settings_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Settings — language, appearance, and accessibility controls backed by
 * shipping behavior. Writes run in the app-owned [mutationScope], so leaving
 * this screen cannot cancel an accepted choice. The running UI updates in
 * place because `App.kt` collects the same [SettingsStore] flows and wires
 * them to [ParlorTheme] + `ProvideAppLanguage`.
 *
 * The screen inherits shared theme tokens and introduces no bespoke colors,
 * while accepted mutations outlive this screen's composition.
 */
@Composable
fun SettingsScreen(
    mutationScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val settings: SettingsStore = koinInject()
    val toastState = LocalParlorToastState.current
    val saveFailedText = stringResource(Res.string.settings_save_failed)
    val mutations = remember(mutationScope, toastState, saveFailedText) {
        SettingsMutationDispatcher(mutationScope) {
            toastState.show(saveFailedText, ParlorToastSeverity.Danger)
        }
    }

    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)
    val reducedMotion by settings.reducedMotion.collectAsState(initial = false)

    val currentLanguage = languageTag?.let(AppLanguage::fromTag)
    val currentThemeMode = ThemeMode.fromTag(themeModeTag)

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.settings_title),
                eyebrow = stringResource(Res.string.settings_eyebrow),
                subtitle = stringResource(Res.string.settings_subtitle),
            )

            SettingsSection(
                label = stringResource(Res.string.settings_language_label),
                description = stringResource(Res.string.settings_language_help),
                selectableGroup = true,
            ) {
                LanguageOption(
                    label = stringResource(Res.string.settings_language_system),
                    selected = currentLanguage == null,
                    onSelected = {
                        mutations.submit { settings.setLanguageOverride(null) }
                    },
                )
                LanguageOption(
                    label = stringResource(Res.string.settings_language_english),
                    selected = currentLanguage == AppLanguage.English,
                    onSelected = {
                        mutations.submit { settings.setLanguageOverride(AppLanguage.English.tag) }
                    },
                )
                LanguageOption(
                    label = stringResource(Res.string.settings_language_arabic),
                    selected = currentLanguage == AppLanguage.Arabic,
                    onSelected = {
                        mutations.submit { settings.setLanguageOverride(AppLanguage.Arabic.tag) }
                    },
                )
            }

            SettingsSection(
                label = stringResource(Res.string.settings_appearance_label),
                description = stringResource(Res.string.settings_appearance_help),
                selectableGroup = true,
            ) {
                AppearanceOption(
                    label = stringResource(Res.string.settings_appearance_system),
                    selected = currentThemeMode == ThemeMode.System,
                    onSelected = {
                        mutations.submit { settings.setThemeMode(ThemeMode.System.tag) }
                    },
                )
                AppearanceOption(
                    label = stringResource(Res.string.settings_appearance_light),
                    selected = currentThemeMode == ThemeMode.Light,
                    onSelected = {
                        mutations.submit { settings.setThemeMode(ThemeMode.Light.tag) }
                    },
                )
                AppearanceOption(
                    label = stringResource(Res.string.settings_appearance_dark),
                    selected = currentThemeMode == ThemeMode.Dark,
                    onSelected = {
                        mutations.submit { settings.setThemeMode(ThemeMode.Dark.tag) }
                    },
                )
            }

            SettingsSection(
                label = stringResource(Res.string.settings_experience_label),
                description = stringResource(Res.string.settings_accessibility_help),
            ) {
                ToggleOption(
                    title = stringResource(Res.string.settings_reduced_motion_title),
                    description = stringResource(Res.string.settings_reduced_motion_description),
                    checked = reducedMotion,
                    onCheckedChange = { enabled ->
                        mutations.submit { settings.setReducedMotion(enabled) }
                    },
                )
            }

        }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    description: String,
    selectableGroup: Boolean = false,
    content: @Composable () -> Unit,
) {
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Column(
            modifier = if (selectableGroup) Modifier.selectableGroup() else Modifier,
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            EyebrowLabel(
                text = label,
                accent = false,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = description,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )
            content()
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) = OptionRow(label = label, selected = selected, onSelected = onSelected)

@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) = OptionRow(label = label, selected = selected, onSelected = onSelected)

@Composable
private fun ToggleOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ParlorTheme.colors
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(colors.surfaceHigher)
            .border(
                width = ParlorTheme.borders.hairline,
                color = colors.borderElevated,
                shape = RoundedCornerShape(ParlorTheme.radii.card),
            )
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.m),
    ) {
        // A fixed switch must not squeeze enlarged text into a narrow column.
        // Keep one whole-card toggle action; the visual switch is not a second target.
        if (maxWidth / fontScale < ToggleRowMinimumWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                ToggleText(title, description, Modifier.fillMaxWidth())
                Switch(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.End))
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleText(title, description, Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun ToggleText(title: String, description: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
        Text(text = title, style = ParlorTheme.typography.bodyLarge, color = ParlorTheme.colors.textPrimary)
        Text(text = description, style = ParlorTheme.typography.bodySmall, color = ParlorTheme.colors.textSecondary)
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(colors.surfaceHigher)
            .background(
                if (selected) colors.accentEmber.copy(alpha = SELECTED_SURFACE_ALPHA)
                else colors.transparent,
            )
            .border(
                ParlorTheme.borders.hairline,
                if (selected) colors.accentEmber else colors.borderElevated,
                RoundedCornerShape(ParlorTheme.radii.card),
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ParlorTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = null)
    }
}

private const val SELECTED_SURFACE_ALPHA = 0.12f

private val ToggleRowMinimumWidth = 240.dp

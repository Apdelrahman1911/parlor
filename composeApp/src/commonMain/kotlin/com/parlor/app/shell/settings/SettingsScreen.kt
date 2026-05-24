package com.parlor.app.shell.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.settings_appearance_dark
import com.parlor.app.resources.settings_appearance_label
import com.parlor.app.resources.settings_appearance_light
import com.parlor.app.resources.settings_appearance_system
import com.parlor.app.resources.settings_back
import com.parlor.app.resources.settings_back_description
import com.parlor.app.resources.settings_language_arabic
import com.parlor.app.resources.settings_language_english
import com.parlor.app.resources.settings_language_label
import com.parlor.app.resources.settings_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Settings — language and appearance pickers. Persists immediately via
 * [SettingsStore]; the running UI updates in place because `App.kt` collects
 * the same flows and wires them to [ParlorTheme] + `ProvideAppLanguage`.
 *
 * Per Phase 8 polish bar: the screen is small, polished, and inherits the
 * cozy-noir tokens. No bespoke colors.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()

    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)

    val currentLanguage = AppLanguage.fromTag(languageTag)
    val currentThemeMode = ThemeMode.fromTag(themeModeTag)

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
            )

            SettingsSection(label = stringResource(Res.string.settings_language_label)) {
                LanguageOption(
                    label = stringResource(Res.string.settings_language_english),
                    selected = currentLanguage == AppLanguage.English,
                    onSelected = {
                        scope.launch { settings.setLanguageOverride(AppLanguage.English.tag) }
                    },
                )
                LanguageOption(
                    label = stringResource(Res.string.settings_language_arabic),
                    selected = currentLanguage == AppLanguage.Arabic,
                    onSelected = {
                        scope.launch { settings.setLanguageOverride(AppLanguage.Arabic.tag) }
                    },
                )
            }

            SettingsSection(label = stringResource(Res.string.settings_appearance_label)) {
                AppearanceOption(
                    label = stringResource(Res.string.settings_appearance_system),
                    selected = currentThemeMode == ThemeMode.System,
                    onSelected = {
                        scope.launch { settings.setThemeMode(ThemeMode.System.tag) }
                    },
                )
                AppearanceOption(
                    label = stringResource(Res.string.settings_appearance_light),
                    selected = currentThemeMode == ThemeMode.Light,
                    onSelected = {
                        scope.launch { settings.setThemeMode(ThemeMode.Light.tag) }
                    },
                )
                AppearanceOption(
                    label = stringResource(Res.string.settings_appearance_dark),
                    selected = currentThemeMode == ThemeMode.Dark,
                    onSelected = {
                        scope.launch { settings.setThemeMode(ThemeMode.Dark.tag) }
                    },
                )
            }

            ParlorButton(
                label = stringResource(Res.string.settings_back),
                contentDescription = stringResource(Res.string.settings_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable () -> Unit,
) {
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = ParlorTheme.elevation.medium,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = label.uppercase(),
                style = ParlorTheme.typography.labelSmall,
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
            .background(if (selected) colors.accentEmberDeep else colors.surfaceHigher)
            .border(
                1.dp,
                if (selected) colors.accentEmber else colors.borderElevated,
                RoundedCornerShape(ParlorTheme.radii.card),
            )
            .clickable(onClick = onSelected)
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ParlorTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(ParlorTheme.iconSize.xxs)
                    .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                    .background(colors.accentEmber),
            )
        }
    }
}

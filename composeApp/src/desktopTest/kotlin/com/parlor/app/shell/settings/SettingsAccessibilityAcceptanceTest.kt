package com.parlor.app.shell.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.app.shell.ShellAcceptanceFrame
import com.parlor.app.shell.assertPhoneTouchTarget
import com.parlor.app.shell.captureShellAcceptance
import com.parlor.app.shell.fullTextLayout
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.settings.SettingsStore
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsAccessibilityAcceptanceTest {
    @Test
    fun english_light_settings_at_200_percent() = verifySettings(AppLanguage.English, ThemeMode.Light)

    @Test
    fun english_dark_settings_at_200_percent() = verifySettings(AppLanguage.English, ThemeMode.Dark)

    @Test
    fun arabic_light_settings_at_200_percent() = verifySettings(AppLanguage.Arabic, ThemeMode.Light)

    @Test
    fun arabic_dark_settings_at_200_percent() = verifySettings(AppLanguage.Arabic, ThemeMode.Dark)

    private fun verifySettings(language: AppLanguage, theme: ThemeMode) {
        val settings = InMemorySettingsStore(initialLanguageOverride = language.tag, initialThemeMode = theme.tag)
        startKoin { modules(module { single<SettingsStore> { settings } }) }
        try {
            runComposeUiTest {
                setContent {
                    // Match App's live bindings: choices must update both resources and theme in place.
                    val languageTag by settings.languageOverride.collectAsState()
                    val themeTag by settings.themeMode.collectAsState()
                    CompositionLocalProvider(LocalParlorToastState provides ParlorToastState()) {
                        ShellAcceptanceFrame(AppLanguage.fromTag(checkNotNull(languageTag)), ThemeMode.fromTag(themeTag)) {
                            SettingsScreen(mutationScope = rememberCoroutineScope())
                        }
                    }
                }
                verifyLanguageChoices(settings, language)
                captureShellAcceptance("settings-language-${language.tag}-${theme.tag}-2.0")
                verifyAppearanceChoices(settings, language, theme)
                captureShellAcceptance("settings-appearance-${language.tag}-${theme.tag}-2.0")
                val reduceMotion = if (language == AppLanguage.Arabic) "تقليل الحركة" else "Reduce motion"
                onNodeWithText(reduceMotion).performScrollTo().assertIsOff()
                onNodeWithText(reduceMotion).assertPhoneTouchTarget()
                assertReadableToggle(reduceMotion)
                onNodeWithText(reduceMotion).performClick()
                waitUntil { settings.reducedMotion.value }
                onNodeWithText(reduceMotion).assertIsOn()
                captureShellAcceptance("settings-accessibility-${language.tag}-${theme.tag}-2.0")
                onNodeWithText(reduceMotion).performClick()
                waitUntil { !settings.reducedMotion.value }
                onNodeWithText(reduceMotion).assertIsOff()
                runOnIdle { assertEquals(language.tag, settings.languageOverride.value) }
            }
        } finally {
            stopKoin()
        }
    }

    private fun ComposeUiTest.assertReadableToggle(title: String) {
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = true)
            .assertCountEquals(1)
        val layout = onNodeWithText(title, useUnmergedTree = true).fullTextLayout()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = true)
            assertTrue(title[end - 1].isWhitespace() || title[end].isWhitespace(),
                "A settings title must not split words to make room for its switch")
        }
        val control = onNodeWithText(title).getUnclippedBoundsInRoot()
        val viewport = onNodeWithTag("acceptance-viewport").getUnclippedBoundsInRoot()
        assertTrue(control.top >= viewport.top && control.bottom <= viewport.bottom,
            "The whole switch explanation must be visible after scrolling: $control in $viewport")
    }

    private fun ComposeUiTest.verifyLanguageChoices(settings: InMemorySettingsStore, initial: AppLanguage) {
        val other = if (initial == AppLanguage.English) AppLanguage.Arabic else AppLanguage.English
        for (language in listOf(other, initial)) {
            val label = if (language == AppLanguage.Arabic) "العربية" else "English"
            onNodeWithText(label).performScrollTo().assertIsNotSelected()
            onNodeWithText(label).assertPhoneTouchTarget()
            onNodeWithText(label, useUnmergedTree = true).fullTextLayout()
            onNodeWithText(label).performClick()
            waitUntil { settings.languageOverride.value == language.tag }
            onNodeWithText(label).assertIsSelected()
            val localizedTitle = if (language == AppLanguage.Arabic) "الإعدادات" else "Settings"
            onNodeWithText(localizedTitle).assertExists()
        }
    }

    private fun ComposeUiTest.verifyAppearanceChoices(
        settings: InMemorySettingsStore, language: AppLanguage, initial: ThemeMode,
    ) {
        val other = if (initial == ThemeMode.Light) ThemeMode.Dark else ThemeMode.Light
        for (theme in listOf(other, initial)) {
            val label = when {
                language == AppLanguage.Arabic -> if (theme == ThemeMode.Light) "فاتح" else "داكن"
                theme == ThemeMode.Light -> "Light"
                else -> "Dark"
            }
            onNodeWithText(label).performScrollTo().assertIsNotSelected()
            onNodeWithText(label).assertPhoneTouchTarget()
            onNodeWithText(label, useUnmergedTree = true).fullTextLayout()
            onNodeWithText(label).performClick()
            waitUntil { settings.themeMode.value == theme.tag }
            onNodeWithText(label).assertIsSelected()
        }
    }
}

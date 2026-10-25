package com.parlor.app.shell.playmode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.session.PlayMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlayModePickerRenderingTest {
    @Test
    fun multiplayer_only_bindings_hide_local_topology_and_offer_reachable_host_join_in_both_languages() {
        val bindings = listOf(
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        for (binding in bindings) {
            for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
                verifyMultiplayerOnly(binding.capabilities, binding.definition.supportedPlayerCounts, language)
            }
        }
    }

    private fun verifyMultiplayerOnly(
        capabilities: GameShellCapabilities,
        playerCounts: IntRange,
        language: AppLanguage,
    ) = runComposeUiTest {
        var localSelections = 0
        var hostSelections = 0
        var joinSelections = 0
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(320.dp, 740.dp)) {
                            PlayModePickerScreen(
                                onModeSelected = { localSelections++ },
                                onHost = { hostSelections++ },
                                onJoin = { joinSelections++ },
                                onBack = {},
                                capabilities = capabilities,
                                supportedPlayerCounts = playerCounts,
                            )
                        }
                    }
                }
            }
        }
        val arabic = language == AppLanguage.Arabic
        onNodeWithText(if (arabic) "جهاز واحد" else "ONE DEVICE").assertDoesNotExist()
        onNodeWithText(if (arabic) "اللعب المنفرد غير متاح." else "Solo is not available.").assertDoesNotExist()
        onNodeWithContentDescription(
            if (arabic) "ابدأ جلسة تمرير ولعب على هذا الجهاز." else "Start a pass-and-play session on this device.",
        ).assertDoesNotExist()
        onNodeWithContentDescription(
            if (arabic) "ابدأ جلسة منفردة على هذا الجهاز بدون تمرير." else "Start a solo run on this device with no hand-off.",
        ).assertDoesNotExist()
        onNodeWithContentDescription(
            if (arabic) "افتح غرفة وأظهر الرمز لأصدقائك للانضمام." else "Open a room and show a code your friends can join.",
        ).performScrollTo().assertIsEnabled().performClick()
        onNodeWithContentDescription(
            if (arabic) "أدخل رمز المضيف للدخول إلى طاولته." else "Enter the host's room code to join their table.",
        ).performScrollTo().assertIsEnabled().performClick()
        runOnIdle {
            assertEquals(0, localSelections)
            assertEquals(1, hostSelections)
            assertEquals(1, joinSelections)
        }
    }

    @Test
    fun existing_pass_and_play_keeps_its_action_and_explains_unsupported_solo() = verifyLocalModes(solo = false)

    @Test
    fun existing_solo_and_pass_and_play_remain_selectable() = verifyLocalModes(solo = true)

    private fun verifyLocalModes(solo: Boolean) = runComposeUiTest {
        val selections = mutableListOf<PlayMode>()
        val modes = setOf(GameEntryMode.PassAndPlay, GameEntryMode.Host, GameEntryMode.Join) +
            if (solo) setOf(GameEntryMode.Solo) else emptySet()
        setContent {
            ProvideAppLanguage(AppLanguage.English) {
                ParlorTheme(reducedMotion = true) {
                    PlayModePickerScreen(
                        onModeSelected = selections::add,
                        onHost = {},
                        onJoin = {},
                        onBack = {},
                        capabilities = GameShellCapabilities(modes),
                        supportedPlayerCounts = 3..6,
                    )
                }
            }
        }
        onNodeWithText("ONE DEVICE").assertExists()
        onNodeWithContentDescription("Start a pass-and-play session on this device.")
            .performScrollTo().assertIsEnabled().performClick()
        if (solo) {
            onNodeWithContentDescription("Start a solo run on this device with no hand-off.")
                .performScrollTo().assertIsEnabled().performClick()
        } else {
            onNodeWithText("Solo is not available.").performScrollTo().assertIsNotEnabled()
        }
        runOnIdle {
            assertEquals(if (solo) listOf(PlayMode.PassAndPlay, PlayMode.Solo) else listOf(PlayMode.PassAndPlay), selections)
        }
    }
}

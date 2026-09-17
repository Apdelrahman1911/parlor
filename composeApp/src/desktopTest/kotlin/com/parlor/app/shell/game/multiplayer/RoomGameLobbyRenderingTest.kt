package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.RoomMember
import com.parlor.networking.testing.InMemoryHostRoom
import com.parlor.networking.testing.InMemoryRoomBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RoomGameLobbyRenderingTest {
    @Test
    fun public_lobbies_isolate_mixed_direction_names_and_validate_seat_limits_in_both_languages() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            verifyLobby(DominoGameShellBinding(DominoDefinition()), language)
            verifyLobby(GhamzaGameShellBinding(GhamzaDefinition()), language)
            verifyLobby(WordImpostorGameShellBinding(WordImpostorDefinition()), language)
        }
    }

    private fun <S : GameState, A : GameAction, E : GameEvent> verifyLobby(
        spec: MultiplayerGameSpec<S, A, E>, language: AppLanguage,
    ) = runComposeUiTest {
        val hostName = "أحمد (Ahmed 12)"
        val members = MutableStateFlow(emptyList<RoomMember>())
        val room = object : LocalRoom by InMemoryHostRoom(InMemoryRoomBus(), PlayerId("host"), hostName) {
            override val members = members
        }
        val setup = GameHostSetupCheckpoint(spec.kind("setup"), spec.defaultCaseId)
        val toast = ParlorToastState()
        var starts = 0
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalParlorToastState provides toast) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(320.dp, 740.dp)) {
                            HostRoomControls(spec, room, setup, { starts++ }, {}, false, Modifier)
                        }
                    }
                }
            }
        }
        val arabic = language == AppLanguage.Arabic
        onNodeWithText(if (arabic) "المضيف ${hostName.asBidiArgument()}" else "Hosted by ${hostName.asBidiArgument()}")
            .performScrollTo().assertExists()
        onNodeWithText(startLabel(arabic, 1)).performScrollTo().assertIsNotEnabled()
        val minimum = spec.definition.supportedPlayerCounts.first
        runOnIdle {
            members.value = (1 until minimum).map { RoomMember(PlayerId("guest-$it"), "Player $it", true) }
        }
        onNodeWithText(startLabel(arabic, minimum)).performScrollTo().assertIsEnabled().performClick()
        runOnIdle { assertEquals(1, starts) }
        val maximum = spec.definition.supportedPlayerCounts.last
        runOnIdle {
            members.value = (1 until maximum).map { RoomMember(PlayerId("guest-$it"), "Player $it", true) }
        }
        onNodeWithText(startLabel(arabic, maximum)).performScrollTo().assertIsEnabled()
        runOnIdle {
            members.value = members.value + RoomMember(PlayerId("extra"), "Extra Player", true)
        }
        onNodeWithText(startLabel(arabic, maximum + 1)).performScrollTo().assertIsNotEnabled()
    }

    private fun startLabel(arabic: Boolean, count: Int): String = when {
        !arabic -> if (count == 1) "Start with 1 player" else "Start with $count players"
        count == 1 -> "ابدأ بلاعب واحد (1)"
        count == 2 -> "ابدأ بلاعبين (2)"
        count <= 10 -> "ابدأ بـ $count لاعبين"
        else -> "ابدأ بـ $count لاعبًا"
    }
}

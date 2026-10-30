package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import kotlinx.coroutines.flow.Flow
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Real shared clickable/pointer-to-shell execution, not SemanticsActions.OnClick.
 * The 50ms interval is Compose event time, not UIKit or physical-device evidence.
 * No observer, shipping seam, artificial callback or networking is introduced.
 */
@OptIn(ExperimentalTestApi::class)
class MultiplayerSetupPointerTest {
    @Test
    fun single_fifty_millisecond_press_opens_host_name_without_starting_networking() =
        verifyChoices(SetupChoice.Host, SetupGesture.Press)

    @Test
    fun single_fifty_millisecond_press_opens_peer_name_without_starting_networking() =
        verifyChoices(SetupChoice.Join, SetupGesture.Press)

    @Test
    fun cancelled_press_keeps_setup_and_does_not_start_networking() =
        SetupChoice.entries.forEach { verifyChoices(it, SetupGesture.Cancel) }

    @Test
    fun scrolling_from_a_pressed_card_does_not_activate_its_choice() =
        SetupChoice.entries.forEach { verifyChoices(it, SetupGesture.Scroll) }

    @Test
    fun repeated_short_input_does_not_skip_name_confirmation_or_start_networking() =
        SetupChoice.entries.forEach { verifyChoices(it, SetupGesture.RepeatedPress) }

    @Test
    fun equal_recreated_launch_preserves_the_selected_destination_and_typed_name() {
        for (binding in bindings()) {
            for (language in AppLanguage.entries) {
                for (choice in SetupChoice.entries) verifyRecreatedLaunch(binding, language, choice)
            }
        }
    }

    @Test
    fun returning_from_an_untouched_host_name_then_joining_commits_the_single_full_input() {
        for (binding in bindings()) {
            for (language in AppLanguage.entries) {
                verifyHostReturnThenPeerInput(binding, language, hostInput = null, peerInput = "Table Test")
            }
        }
    }

    @Test
    fun a_new_peer_entry_does_not_reuse_the_previous_host_name() {
        for (binding in bindings()) {
            for (language in AppLanguage.entries) {
                verifyHostReturnThenPeerInput(binding, language, hostInput = "Host Fixture", peerInput = "Table Test")
            }
        }
    }

    @Test
    fun arabic_unicode_input_survives_return_and_equal_peer_launch_recreation() {
        for (binding in bindings()) {
            verifyHostReturnThenPeerInput(binding, AppLanguage.Arabic, hostInput = "مضيف تجريبي", peerInput = "أحمد")
        }
    }

    private fun verifyChoices(choice: SetupChoice, gesture: SetupGesture) {
        for (binding in bindings()) {
            for (language in AppLanguage.entries) verifyChoice(binding, language, choice, gesture)
        }
    }

    private fun verifyChoice(
        binding: GameShellBinding,
        language: AppLanguage,
        choice: SetupChoice,
        gesture: SetupGesture,
    ) = runComposeUiTest {
        val transport = SetupNetworkTripwire()
        var exits = 0
        setContent {
            KoinApplication(application = { modules(module { single<RoomTransport> { transport } }) }) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    ProvideAppLanguage(language) {
                        ParlorTheme(reducedMotion = true) {
                            Box(Modifier.size(320.dp, 740.dp)) {
                                binding.Content(GameShellLaunch.New(binding.definition.id), { exits++ }, GameShellBackRequest.Initial)
                            }
                        }
                    }
                }
            }
        }
        val copy = setupCopy(language)
        val source = onNodeWithContentDescription(copy.choice(choice)).performScrollTo().assertIsEnabled()
        source.send(gesture)
        if (gesture == SetupGesture.Cancel || gesture == SetupGesture.Scroll) {
            source.assertExists()
            onNodeWithContentDescription(copy.confirm(choice)).assertDoesNotExist()
        } else {
            assertNameDestination(copy, choice)
        }
        runOnIdle {
            assertEquals(0, exits)
            assertEquals(0, transport.operations)
        }
    }

    private fun verifyRecreatedLaunch(binding: GameShellBinding, language: AppLanguage, choice: SetupChoice) =
        runComposeUiTest {
            val transport = SetupNetworkTripwire()
            var generation by mutableStateOf(0)
            val launches = mutableListOf<GameShellLaunch>()
            setContent {
                KoinApplication(application = { modules(module { single<RoomTransport> { transport } }) }) {
                    val launch = remember(generation) {
                        GameShellLaunch.New(binding.definition.id).also { launches += it }
                    }
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        ProvideAppLanguage(language) {
                            ParlorTheme(reducedMotion = true) {
                                Box(Modifier.size(320.dp, 740.dp)) {
                                    binding.Content(launch, {}, GameShellBackRequest.Initial)
                                }
                            }
                        }
                    }
                }
            }
            val copy = setupCopy(language)
            onNodeWithContentDescription(copy.choice(choice)).performScrollTo().send(SetupGesture.Press)
            assertNameDestination(copy, choice)
            onNodeWithText(copy.nameField).performTextInput("Table Test")
            runOnIdle { generation++ }
            onNodeWithText(copy.nameField).assertTextContains("Table Test")
            onNodeWithContentDescription(copy.confirm(choice)).assertIsEnabled()
            onNodeWithContentDescription(copy.choice(choice)).assertDoesNotExist()
            runOnIdle {
                assertEquals(2, launches.size)
                assertEquals(launches[0], launches[1])
                assertNotSame(launches[0], launches[1])
                assertEquals(0, transport.operations)
            }
        }

    /** Shared callback/state boundary only: Desktop text injection is not native IME delivery. */
    private fun verifyHostReturnThenPeerInput(
        binding: GameShellBinding,
        language: AppLanguage,
        hostInput: String?,
        peerInput: String,
    ) = runComposeUiTest {
        val transport = SetupNetworkTripwire()
        var visible by mutableStateOf(true)
        var generation by mutableStateOf(0)
        var exits = 0
        setContent {
            KoinApplication(application = { modules(module { single<RoomTransport> { transport } }) }) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    ProvideAppLanguage(language) {
                        ParlorTheme(reducedMotion = true) {
                            Box(Modifier.size(320.dp, 740.dp)) {
                                if (visible) {
                                    val launch = remember(generation) { GameShellLaunch.New(binding.definition.id) }
                                    binding.Content(
                                        launch,
                                        {
                                            exits++
                                            visible = false
                                        },
                                        GameShellBackRequest.Initial,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val copy = setupCopy(language)
        onNodeWithContentDescription(copy.host).performScrollTo().send(SetupGesture.Press)
        assertNameDestination(copy, SetupChoice.Host)
        if (hostInput != null) {
            onNodeWithText(copy.nameField).performTextInput(hostInput)
            onNodeWithText(copy.nameField).assertTextContains(hostInput, substring = false)
            onNodeWithContentDescription(copy.hostConfirm).assertIsEnabled()
        }
        val back = if (language == AppLanguage.Arabic) "العودة إلى الشاشة الرئيسية." else "Return to the home screen."
        onNodeWithContentDescription(back).performScrollTo().send(SetupGesture.Press)
        onNodeWithText(copy.nameField).assertDoesNotExist()
        runOnIdle {
            assertEquals(1, exits)
            visible = true
            generation++
        }
        onNodeWithContentDescription(copy.join).performScrollTo().send(SetupGesture.Press)
        assertNameDestination(copy, SetupChoice.Join)
        if (hostInput != null) onNodeWithText(hostInput).assertDoesNotExist()
        onNodeWithText(copy.nameField).performTextInput(peerInput)
        onNodeWithText(copy.nameField).assertTextContains(peerInput, substring = false)
        onNodeWithContentDescription(copy.peerConfirm).assertIsEnabled()
        runOnIdle { generation++ }
        onNodeWithText(copy.nameField).assertTextContains(peerInput, substring = false)
        onNodeWithContentDescription(copy.peerConfirm).assertIsEnabled()
        runOnIdle {
            assertEquals(1, exits)
            assertEquals(0, transport.operations)
        }
    }

    private fun ComposeUiTest.assertNameDestination(copy: SetupCopy, choice: SetupChoice) {
        onNodeWithText(copy.nameField).assertExists()
        onNodeWithContentDescription(copy.confirm(choice)).assertIsNotEnabled()
        onNodeWithContentDescription(copy.choice(SetupChoice.Host)).assertDoesNotExist()
        onNodeWithContentDescription(copy.choice(SetupChoice.Join)).assertDoesNotExist()
        onNodeWithContentDescription(copy.confirm(if (choice == SetupChoice.Host) SetupChoice.Join else SetupChoice.Host))
            .assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.send(gesture: SetupGesture) = performTouchInput {
        down(center)
        advanceEventTime(50)
        when (gesture) {
            SetupGesture.Press -> up()
            SetupGesture.Cancel -> cancel()
            SetupGesture.Scroll -> {
                moveTo(center - Offset(0f, 120f))
                advanceEventTime(50)
                up()
            }
            SetupGesture.RepeatedPress -> {
                up()
                advanceEventTime(50)
                down(center)
                advanceEventTime(50)
                up()
            }
        }
    }
}

private enum class SetupChoice { Host, Join }
private enum class SetupGesture { Press, Cancel, Scroll, RepeatedPress }

private fun bindings(): List<GameShellBinding> = listOf(
    DominoGameShellBinding(DominoDefinition()),
    GhamzaGameShellBinding(GhamzaDefinition()),
    WordImpostorGameShellBinding(WordImpostorDefinition()),
)

private data class SetupCopy(
    val host: String,
    val join: String,
    val hostConfirm: String,
    val peerConfirm: String,
    val nameField: String,
) {
    fun choice(choice: SetupChoice) = if (choice == SetupChoice.Host) host else join
    fun confirm(choice: SetupChoice) = if (choice == SetupChoice.Host) hostConfirm else peerConfirm
}

private fun setupCopy(language: AppLanguage): SetupCopy = if (language == AppLanguage.Arabic) {
    SetupCopy(
        host = "افتح غرفة وأظهر الرمز لأصدقائك للانضمام.",
        join = "أدخل رمز المضيف للدخول إلى طاولته.",
        hostConfirm = "احفظ اسمك وتابع لإعداد طاولتك.",
        peerConfirm = "احفظ اسمك وتابع لإدخال رمز الغرفة.",
        nameField = "اسمك",
    )
} else {
    SetupCopy(
        host = "Open a room and show a code your friends can join.",
        join = "Enter the host's room code to join their table.",
        hostConfirm = "Save your name and continue to set up your table.",
        peerConfirm = "Save your name and continue to enter a room code.",
        nameField = "Your name",
    )
}

/** Any premature network call fails the journey even if a UI catch handles it. */
private class SetupNetworkTripwire : RoomTransport {
    var operations = 0
        private set
    override val capability = TransportCapability(false, 0, 65_536)

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = unexpectedOperation()
    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = unexpectedOperation()
    override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> = unexpectedOperation()
    override fun discoverRooms(): Flow<List<DiscoveredRoom>> = unexpectedOperation()

    private fun unexpectedOperation(): Nothing {
        operations++
        error("Public setup must not start a network operation before explicit confirmation")
    }
}

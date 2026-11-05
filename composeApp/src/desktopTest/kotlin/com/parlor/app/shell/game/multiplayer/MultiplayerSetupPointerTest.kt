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
import androidx.compose.ui.semantics.SemanticsActions
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
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.CaseUpdate
import com.parlor.content.schema.CaseSummary
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
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
    fun queued_opposite_setup_choice_cannot_replace_the_first_host_selection() =
        verifyRetainedChoice(SetupChoice.Host, afterNameInput = false)

    @Test
    fun queued_opposite_setup_choice_cannot_replace_the_first_join_selection() =
        verifyRetainedChoice(SetupChoice.Join, afterNameInput = false)

    @Test
    fun retained_setup_choices_cannot_change_the_route_or_erase_a_typed_name() =
        SetupChoice.entries.forEach { verifyRetainedChoice(it, afterNameInput = true) }

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

    /** Retained public semantics callbacks, not a reproduced UIKit event sequence. */
    private fun verifyRetainedChoice(choice: SetupChoice, afterNameInput: Boolean) {
        val allBindings = bindings() + listOf(
            MafiaGameShellBinding(MafiaDefinition(Json)),
            LastLightGameShellBinding(LastLightDefinition()),
            WhodunitGameShellBinding(WhodunitDefinition(Json)),
        )
        for (binding in allBindings) {
            for (language in AppLanguage.entries) runComposeUiTest {
                val transport = SetupNetworkTripwire()
                var exits = 0
                setContent {
                    KoinApplication(application = { modules(module {
                        single<RoomTransport> { transport }
                        single<CaseRepository> { SetupContentTripwire() }
                        single { AppLifecycleCoordinator(transport).apply { notifyActive() } }
                    }) }) {
                        ProvideAppLanguage(language) {
                            ParlorTheme(reducedMotion = true) {
                                binding.Content(
                                    GameShellLaunch.New(binding.definition.id), { exits++ }, GameShellBackRequest.Initial,
                                )
                            }
                        }
                    }
                }
                val copy = setupCopy(language)
                val opposite = if (choice == SetupChoice.Host) SetupChoice.Join else SetupChoice.Host
                val activate = onNodeWithContentDescription(copy.choice(choice)).performScrollTo()
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action.let(::checkNotNull)
                val activateOpposite = onNodeWithContentDescription(copy.choice(opposite)).performScrollTo()
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action.let(::checkNotNull)
                val activateLocal = if (binding.capabilities.supports(GameEntryMode.PassAndPlay)) {
                    val description = if (language == AppLanguage.Arabic) {
                        "ابدأ جلسة تمرير ولعب على هذا الجهاز."
                    } else {
                        "Start a pass-and-play session on this device."
                    }
                    onNodeWithContentDescription(description).performScrollTo()
                        .fetchSemanticsNode().config[SemanticsActions.OnClick].action.let(::checkNotNull)
                } else null
                runOnUiThread {
                    activate()
                    if (!afterNameInput) activateOpposite()
                }
                assertNameDestination(copy, choice)
                if (afterNameInput) {
                    val input = if (language == AppLanguage.Arabic) "أحمد" else "Table Test"
                    onNodeWithText(copy.nameField).performTextInput(input)
                    runOnUiThread {
                        activateOpposite()
                        activateLocal?.invoke()
                        activate()
                    }
                    onNodeWithText(copy.nameField).assertTextContains(input, substring = false)
                    onNodeWithContentDescription(copy.confirm(choice)).assertIsEnabled()
                    onNodeWithContentDescription(copy.confirm(opposite)).assertDoesNotExist()
                }
                runOnIdle {
                    assertEquals(0, transport.operations)
                    assertEquals(0, exits)
                }
            }
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

/** Public mode/name selection must not fetch a case or enter gameplay. */
private class SetupContentTripwire : CaseRepository {
    override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, DataError> = unexpectedOperation()
    override suspend fun <TPayload> loadCase(
        id: CaseId, payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, DataError> = unexpectedOperation()
    override fun observeCacheUpdates(): Flow<CaseUpdate> = unexpectedOperation()
    override suspend fun <TPayload> refresh(
        gameId: GameId, payloadValidator: PayloadValidator<TPayload>,
    ): Result<Unit, DataError> = unexpectedOperation()
    private fun unexpectedOperation(): Nothing = error("Public mode/name selection must not access case content")
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

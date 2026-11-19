package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
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
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shared raw-pointer route boundary only, not UIKit or original Host-failure causality. */
@OptIn(ExperimentalTestApi::class)
class MultiplayerNameConfirmationPointerTest {
    @Test
    fun single_name_confirmation_preserves_host_intent() = verifyConfirmation(repeat = false)

    @Test
    fun repeated_name_confirmation_preserves_host_intent() = verifyConfirmation(repeat = true)

    @Test
    fun duplicate_accessibility_confirmation_preserves_host_intent() =
        verifyConfirmation(repeat = true, accessibility = true)

    @Test
    fun duplicate_accessibility_confirmation_preserves_peer_intent_without_joining() =
        verifyConfirmation(repeat = true, accessibility = true, isHost = false)

    private fun verifyConfirmation(repeat: Boolean, accessibility: Boolean = false, isHost: Boolean = true) {
        val bindings = listOf(
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        for (binding in bindings) {
            for (language in AppLanguage.entries) verifyConfirmation(binding, language, repeat, accessibility, isHost)
        }
    }

    private fun verifyConfirmation(
        binding: GameShellBinding,
        language: AppLanguage,
        repeat: Boolean,
        accessibility: Boolean,
        isHost: Boolean,
    ) {
        val job = SupervisorJob()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val owner = ProcessMultiplayerSessionOwner(CoroutineScope(job + dispatcher))
        val transport = FailedConfirmationHost()
        val arabic = language == AppLanguage.Arabic
        val host = if (arabic) "افتح غرفة وأظهر الرمز لأصدقائك للانضمام." else "Open a room and show a code your friends can join."
        val confirm = if (arabic) "احفظ اسمك وتابع لإعداد طاولتك." else "Save your name and continue to set up your table."
        val join = if (arabic) "أدخل رمز المضيف للدخول إلى طاولته." else "Enter the host's room code to join their table."
        val peerConfirm = if (arabic) "احفظ اسمك وتابع لإدخال رمز الغرفة." else "Save your name and continue to enter a room code."
        val field = if (arabic) "اسمك" else "Your name"
        val peer = if (arabic) "الاتصال بالمضيف باستخدام الرمز المُدخل." else "Connect to the host using the entered room code."
        try {
            runComposeUiTest {
                setContent {
                    KoinApplication(application = { modules(module {
                        single<RoomTransport> { transport }
                        single { owner }
                    }) }) {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                            ProvideAppLanguage(language) {
                                ParlorTheme(reducedMotion = true) {
                                    Box(Modifier.size(320.dp, 740.dp)) {
                                        binding.Content(GameShellLaunch.New(binding.definition.id), {}, GameShellBackRequest.Initial)
                                    }
                                }
                            }
                        }
                    }
                }
                onNodeWithContentDescription(if (isHost) host else join).performScrollTo().performTouchInput { click() }
                onNodeWithText(field).performTextInput("Host Fixture")
                val button = onNodeWithContentDescription(if (isHost) confirm else peerConfirm).assertIsEnabled()
                if (accessibility) {
                    val activate = checkNotNull(button.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
                    runOnUiThread {
                        activate()
                        activate()
                    }
                } else {
                    button.performTouchInput {
                        down(center)
                        advanceEventTime(50)
                        up()
                        if (repeat) {
                            advanceEventTime(50)
                            down(center)
                            advanceEventTime(50)
                            up()
                        }
                    }
                }
                if (isHost) {
                    onNodeWithContentDescription(peer).assertDoesNotExist()
                    // A fixture's instant failure must not replace Name with
                    // Retry/Leave between the two injected pointer presses.
                    // Opening is published before the owner's background job
                    // calls the transport. Observe that callback's explicit
                    // checkpoint, not an unrelated UI-idle/state publication.
                    waitUntil(timeoutMillis = 5_000) { transport.hostStarted.isCompleted }
                    runOnIdle { assertTrue(owner.state.value is ProcessMultiplayerState.Opening) }
                    onNodeWithText(if (arabic) "جرّب تاني" else "Try again").assertDoesNotExist()
                    runOnIdle { assertEquals(1, transport.hostCalls.get(), "${binding.definition.id}/$language/$repeat/$accessibility") }
                    transport.finishOpening.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) { owner.state.value is ProcessMultiplayerState.Failed }
                    runOnIdle { assertEquals(1, transport.hostCalls.get()) }
                } else {
                    onNodeWithContentDescription(peer).assertExists()
                    runOnIdle {
                        assertEquals(ProcessMultiplayerState.Idle, owner.state.value)
                        assertEquals(0, transport.hostCalls.get())
                    }
                }
            }
        } finally {
            try {
                runBlocking { withTimeout(5_000) { job.cancelAndJoin() } }
            } finally {
                dispatcher.close()
            }
        }
    }
}

private class FailedConfirmationHost : RoomTransport {
    override val capability = TransportCapability(false, 1, 1024)
    val hostCalls = AtomicInteger()
    val hostStarted = CompletableDeferred<Unit>()
    val finishOpening = CompletableDeferred<Unit>()
    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        hostCalls.incrementAndGet()
        hostStarted.complete(Unit)
        finishOpening.await()
        return Result.Failure(NetError.NotConnected)
    }
    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> =
        error("Host name confirmation must not join")
}

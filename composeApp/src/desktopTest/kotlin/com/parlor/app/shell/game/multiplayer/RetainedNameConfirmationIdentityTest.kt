package com.parlor.app.shell.game.multiplayer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.parlor.app.di.contentModule
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.result.Result
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.dominoes.di.dominoModule
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.ghamza.di.ghamzaModule
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.di.lastLightModule
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.games.wordimpostor.di.wordImpostorModule
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/** Actual shipping callbacks/transport handoff; not a reproduced UIKit or VoiceOver delivery. */
@OptIn(ExperimentalTestApi::class)
class RetainedNameConfirmationIdentityTest {
    @Test
    fun whodunit_retains_the_first_confirmed_peer_identity() = verify(WhodunitGameShellBinding(WhodunitDefinition(Json)))

    @Test
    fun mafia_retains_the_first_confirmed_peer_identity() = verify(MafiaGameShellBinding(MafiaDefinition(Json)))

    @Test
    fun last_light_retains_the_first_confirmed_peer_identity() = verify(LastLightGameShellBinding(LastLightDefinition()))

    @Test
    fun dominoes_retains_the_first_confirmed_peer_identity() = verify(DominoGameShellBinding(DominoDefinition()))

    @Test
    fun ghamza_retains_the_first_confirmed_peer_identity() = verify(GhamzaGameShellBinding(GhamzaDefinition()))

    @Test
    fun word_impostor_retains_the_first_confirmed_peer_identity() =
        verify(WordImpostorGameShellBinding(WordImpostorDefinition()))

    @Test
    fun whodunit_retains_the_first_confirmed_room_code() =
        verify(WhodunitGameShellBinding(WhodunitDefinition(Json)), mutateName = false)

    @Test
    fun mafia_retains_the_first_confirmed_room_code() = verify(MafiaGameShellBinding(MafiaDefinition(Json)), mutateName = false)

    @Test
    fun last_light_retains_the_first_confirmed_room_code() =
        verify(LastLightGameShellBinding(LastLightDefinition()), mutateName = false)

    @Test
    fun dominoes_retains_the_first_confirmed_room_code() = verify(DominoGameShellBinding(DominoDefinition()), mutateName = false)

    @Test
    fun ghamza_retains_the_first_confirmed_room_code() = verify(GhamzaGameShellBinding(GhamzaDefinition()), mutateName = false)

    @Test
    fun word_impostor_retains_the_first_confirmed_room_code() =
        verify(WordImpostorGameShellBinding(WordImpostorDefinition()), mutateName = false)

    @Test
    fun whodunit_retains_the_first_confirmed_host_identity() =
        verify(WhodunitGameShellBinding(WhodunitDefinition(Json)), isHost = true)

    @Test
    fun mafia_retains_the_first_confirmed_host_identity() = verify(MafiaGameShellBinding(MafiaDefinition(Json)), isHost = true)

    @Test
    fun last_light_retains_the_first_confirmed_host_identity() =
        verify(LastLightGameShellBinding(LastLightDefinition()), isHost = true)

    private fun verify(binding: GameShellBinding, mutateName: Boolean = true, isHost: Boolean = false) {
        for (language in AppLanguage.entries) verify(binding, language, mutateName, isHost)
    }

    private fun verify(binding: GameShellBinding, language: AppLanguage, mutateName: Boolean, isHost: Boolean) {
        val job = SupervisorJob()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val owner = ProcessMultiplayerSessionOwner(CoroutineScope(job + dispatcher))
        val transport = IdentityJoinTripwire(isHost)
        val toast = ParlorToastState()
        val arabic = language == AppLanguage.Arabic
        val original = if (arabic) "أحمد" else "First Peer"
        val replacement = if (arabic) "نور" else "Replacement Peer"
        val join = if (arabic) "أدخل رمز المضيف للدخول إلى طاولته." else "Enter the host's room code to join their table."
        val confirm = if (arabic) "احفظ اسمك وتابع لإدخال رمز الغرفة." else "Save your name and continue to enter a room code."
        val host = if (arabic) "افتح غرفة وأظهر الرمز لأصدقائك للانضمام." else "Open a room and show a code your friends can join."
        val hostConfirm = if (arabic) "احفظ اسمك وتابع لإعداد طاولتك." else "Save your name and continue to set up your table."
        val connect = if (arabic) "الاتصال بالمضيف باستخدام الرمز المُدخل." else "Connect to the host using the entered room code."
        try {
            runComposeUiTest {
                setContent {
                    KoinApplication(application = { modules(
                        whodunitModule, mafiaModule, lastLightModule, dominoModule,
                        ghamzaModule, wordImpostorModule, contentModule, module {
                        single<Json> { Json }
                        single<RoomTransport> { transport }
                        single { owner }
                        single { AppLifecycleCoordinator(transport).apply { notifyActive() } }
                    }) }) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(1f),
                            LocalParlorToastState provides toast,
                        ) {
                            ProvideAppLanguage(language) {
                                ParlorTheme(reducedMotion = true) {
                                    binding.Content(GameShellLaunch.New(binding.definition.id), {}, GameShellBackRequest.Initial)
                                }
                            }
                        }
                    }
                }
                onNodeWithContentDescription(if (isHost) host else join).performScrollTo().performClick()
                onNode(hasSetTextAction()).performTextInput(original)
                val edit = checkNotNull(onNode(hasSetTextAction()).fetchSemanticsNode()
                    .config[SemanticsActions.SetText].action)
                val activate = checkNotNull(onNodeWithContentDescription(if (isHost) hostConfirm else confirm).fetchSemanticsNode()
                    .config[SemanticsActions.OnClick].action)
                runOnUiThread {
                    activate()
                    // A late editor callback changes the outgoing draft. A retained
                    // confirmation must not rewrite the already accepted identity.
                    if (mutateName) {
                        check(edit(AnnotatedString(replacement)))
                        activate()
                    }
                }
                runOnIdle { assertEquals(0, transport.joinCalls.get(), "Name confirmation must not join") }
                if (isHost) {
                    if (binding is WhodunitGameShellBinding) {
                        // Complete the real public case/mode setup, not a substituted route.
                        val story = hasContentDescription(if (arabic) "المتابعة بقصة " else "Continue with ", substring = true)
                        waitUntil(timeoutMillis = 5_000) { onAllNodes(story).fetchSemanticsNodes().size == 1 }
                        onNode(story).performClick()
                        val mode = if (arabic) "اختر نمط التصويت الكلاسيكي وتابع إلى إعداد اللاعبين."
                        else "Choose Classic Vote mode and proceed to player setup."
                        onNodeWithContentDescription(mode).performScrollTo().performClick()
                    }
                    waitUntil(timeoutMillis = 5_000) { transport.hostStarted.isCompleted }
                    runOnIdle {
                        assertEquals(original, transport.displayName.get(), "${binding.definition.id}/$language/host")
                        assertEquals(1, transport.hostCalls.get())
                        assertEquals(0, transport.joinCalls.get())
                    }
                    return@runComposeUiTest
                }
                onNode(hasSetTextAction()).performTextInput("ABCDEF")
                if (mutateName) {
                    onNodeWithContentDescription(connect).performClick()
                } else {
                    val editCode = checkNotNull(onNode(hasSetTextAction()).fetchSemanticsNode()
                        .config[SemanticsActions.SetText].action)
                    val connectRoom = checkNotNull(onNodeWithContentDescription(connect).fetchSemanticsNode()
                        .config[SemanticsActions.OnClick].action)
                    runOnUiThread {
                        connectRoom()
                        check(editCode(AnnotatedString("UVWXYZ")))
                        connectRoom()
                    }
                }
                waitUntil(timeoutMillis = 5_000) { transport.joinStarted.isCompleted }
                runOnIdle {
                    assertEquals(original, transport.displayName.get(), "${binding.definition.id}/$language")
                    assertEquals("ABCDEF", transport.roomCode.get(), "A stale confirmation cannot select another room")
                    assertEquals(1, transport.joinCalls.get(), "Only explicit room-code confirmation may join")
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

private class IdentityJoinTripwire(private val isHost: Boolean) : RoomTransport {
    override val capability = TransportCapability(false, 1, 1024)
    val joinCalls = AtomicInteger()
    val hostCalls = AtomicInteger()
    val displayName = AtomicReference<String>()
    val roomCode = AtomicReference<String>()
    val joinStarted = CompletableDeferred<Unit>()
    val hostStarted = CompletableDeferred<Unit>()
    private val finishJoining = CompletableDeferred<Unit>()

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        check(isHost) { "Peer confirmation must never host" }
        displayName.set(config.hostDisplayName)
        hostCalls.incrementAndGet()
        hostStarted.complete(Unit)
        finishJoining.await()
        return Result.Failure(NetError.NotConnected)
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        check(!isHost) { "Host confirmation must never join" }
        roomCode.set(code)
        this.displayName.set(displayName)
        joinCalls.incrementAndGet()
        joinStarted.complete(Unit)
        finishJoining.await()
        return Result.Failure(NetError.NotConnected)
    }
}

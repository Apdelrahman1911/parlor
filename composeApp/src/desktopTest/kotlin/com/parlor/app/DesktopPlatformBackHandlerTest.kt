package com.parlor.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.parlor.core.ids.GameId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopPlatformBackHandlerTest {
    @Test
    fun escape_key_down_invokes_the_shared_handler_once_and_key_up_is_ignored() =
        runComposeUiTest {
            val owner = TestNavigationEventDispatcherOwner()
            val input = DesktopEscapeInput()
            owner.navigationEventDispatcher.addInput(input)
            var calls = 0

            setContent {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    PlatformBackHandler(enabled = true) { calls++ }
                }
            }

            runOnIdle {
                assertTrue(input.send(KeyEventType.KeyDown))
                assertFalse(input.send(KeyEventType.KeyUp))
            }

            assertEquals(1, calls)
        }

    @Test
    fun disabled_desktop_back_event_is_left_to_the_dispatcher_fallback() = runComposeUiTest {
        var fallbackCalls = 0
        val owner = TestNavigationEventDispatcherOwner { fallbackCalls++ }
        val input = DesktopEscapeInput()
        owner.navigationEventDispatcher.addInput(input)
        var handlerCalls = 0

        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                PlatformBackHandler(enabled = false) { handlerCalls++ }
            }
        }

        runOnIdle { assertTrue(input.send(KeyEventType.KeyDown)) }

        assertEquals(0, handlerCalls)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun disabling_after_back_prevents_a_repeated_event_from_double_handling() = runComposeUiTest {
        val enabled = mutableStateOf(true)
        val owner = TestNavigationEventDispatcherOwner()
        val input = DesktopEscapeInput()
        owner.navigationEventDispatcher.addInput(input)
        var calls = 0

        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                PlatformBackHandler(enabled = enabled.value) {
                    calls++
                    enabled.value = false
                }
            }
        }

        runOnIdle { assertTrue(input.send(KeyEventType.KeyDown)) }
        waitForIdle()
        runOnIdle { assertTrue(input.send(KeyEventType.KeyDown)) }

        assertEquals(1, calls)
    }

    @Test
    fun settings_and_game_actions_both_enable_the_same_desktop_handler() = runComposeUiTest {
        val route = mutableStateOf<AppRoute>(AppRoute.Settings)
        val owner = TestNavigationEventDispatcherOwner()
        val input = DirectNavigationEventInput()
        owner.navigationEventDispatcher.addInput(input)
        val handledActions = mutableListOf<AppBackAction>()

        setContent {
            val action = appBackAction(route.value)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                PlatformBackHandler(
                    enabled = action == AppBackAction.NavigateGames ||
                        action == AppBackAction.DelegateToGame,
                ) {
                    handledActions += action
                }
            }
        }

        runOnIdle { input.backCompleted() }
        runOnIdle {
            route.value = AppRoute.Game(
                gameId = GameId("desktop-back-fixture"),
                entryId = 1L,
            )
        }
        waitForIdle()
        runOnIdle { input.backCompleted() }

        assertEquals(
            listOf(AppBackAction.NavigateGames, AppBackAction.DelegateToGame),
            handledActions,
        )
    }
}

private class TestNavigationEventDispatcherOwner(
    onBackFallback: () -> Unit = {},
) : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher(onBackFallback)
}

/**
 * Exercises the Escape adapter installed by the pinned Compose Desktop runtime.
 * The class is intentionally kept internal by Compose, so reflection is confined
 * to this dependency-contract test and never used by production navigation.
 */
private class DesktopEscapeInput {
    private val delegate: NavigationEventInput = Class
        .forName("androidx.compose.ui.navigationevent.BackNavigationEventInput")
        .getDeclaredConstructor()
        .apply { isAccessible = true }
        .newInstance() as NavigationEventInput
    private val sendKeyEvent = delegate::class.java.declaredMethods.single { method ->
        method.name.startsWith("onKeyEvent-")
    }.apply { isAccessible = true }

    fun send(type: KeyEventType): Boolean = sendKeyEvent.invoke(
        delegate,
        desktopKeyEvent(type).nativeKeyEvent,
    ) as Boolean

    fun asNavigationEventInput(): NavigationEventInput = delegate
}

@OptIn(InternalComposeUiApi::class)
private fun desktopKeyEvent(type: KeyEventType): KeyEvent = KeyEvent(
    key = Key.Escape,
    type = type,
)

private fun NavigationEventDispatcher.addInput(input: DesktopEscapeInput) {
    addInput(input.asNavigationEventInput())
}

package com.parlor.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionExitBackPolicyTest {
    @Test
    fun active_local_sessions_always_confirm_before_the_save_transaction() {
        assertEquals(
            SessionExitBackAction.Confirm,
            sessionExitBackAction(SessionExitKind.Local, gameHasStarted = true),
        )
    }

    @Test
    fun multiplayer_setup_exits_immediately_but_started_games_confirm() {
        for (kind in listOf(SessionExitKind.Host, SessionExitKind.Peer)) {
            assertEquals(
                SessionExitBackAction.ExitImmediately,
                sessionExitBackAction(kind, gameHasStarted = false),
            )
            assertEquals(
                SessionExitBackAction.Confirm,
                sessionExitBackAction(kind, gameHasStarted = true),
            )
        }
    }
}

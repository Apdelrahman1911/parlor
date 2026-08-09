package com.parlor.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AppBackPolicyTest {

    @Test
    fun home_allows_the_platform_to_finish_the_application() {
        assertEquals(AppBackAction.AllowPlatformExit, appBackAction(AppScreen.Home))
    }

    @Test
    fun static_routes_have_an_explicit_destination() {
        val expected = mapOf(
            AppScreen.GameSetup to AppBackAction.NavigateHome,
            AppScreen.LocalCasePicker to AppBackAction.NavigateGameSetup,
            AppScreen.MafiaSetup to AppBackAction.NavigateHome,
            AppScreen.MafiaHostPermission to AppBackAction.NavigateHome,
            AppScreen.MafiaHostName to AppBackAction.NavigateHome,
            AppScreen.MafiaJoinPermission to AppBackAction.NavigateHome,
            AppScreen.MafiaJoinName to AppBackAction.NavigateHome,
            AppScreen.MafiaJoinPrompt to AppBackAction.NavigateHome,
            AppScreen.HostPermission to AppBackAction.NavigateHome,
            AppScreen.HostName to AppBackAction.NavigateHome,
            AppScreen.HostCasePicker to AppBackAction.NavigateHome,
            AppScreen.HostMode to AppBackAction.NavigateHostCasePicker,
            AppScreen.JoinPermission to AppBackAction.NavigateHome,
            AppScreen.JoinName to AppBackAction.NavigateHome,
            AppScreen.JoinPrompt to AppBackAction.NavigateHome,
            AppScreen.MultiplayerResumePermission to AppBackAction.NavigateHome,
            AppScreen.Settings to AppBackAction.NavigateHome,
        )

        expected.forEach { (screen, action) ->
            assertEquals(action, appBackAction(screen), "Unexpected policy for $screen")
        }
    }

    @Test
    fun session_owned_routes_consume_system_back() {
        val transactionSensitiveRoutes = setOf(
            AppScreen.Whodunit,
            AppScreen.Mafia,
            AppScreen.HostLobby,
            AppScreen.PeerLobby,
            AppScreen.MafiaHostLobby,
            AppScreen.MafiaPeerLobby,
            AppScreen.ResumeWhodunitPeer,
            AppScreen.ResumeMafiaPeer,
        )

        transactionSensitiveRoutes.forEach { screen ->
            assertEquals(AppBackAction.Consume, appBackAction(screen), "Unexpected policy for $screen")
        }
    }

    @Test
    fun every_route_is_covered_by_a_known_policy_group() {
        val counts = AppScreen.entries.groupingBy(::appBackAction).eachCount()

        assertEquals(1, counts[AppBackAction.AllowPlatformExit])
        assertEquals(15, counts[AppBackAction.NavigateHome])
        assertEquals(1, counts[AppBackAction.NavigateGameSetup])
        assertEquals(1, counts[AppBackAction.NavigateHostCasePicker])
        assertEquals(8, counts[AppBackAction.Consume])
        assertEquals(AppScreen.entries.size, counts.values.sum())
    }
}

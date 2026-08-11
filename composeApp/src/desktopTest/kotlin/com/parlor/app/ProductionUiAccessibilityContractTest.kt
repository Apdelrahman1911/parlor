package com.parlor.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structural guard for custom Compose interactions that cannot be exercised by
 * the repository's headless domain tests. Physical TalkBack/VoiceOver remains
 * a release gate; these assertions prevent known inaccessible implementations
 * from silently returning before that matrix runs.
 */
class ProductionUiAccessibilityContractTest {
    private val root: File by lazy(::findProjectRoot)

    @Test
    fun wax_seal_exposes_a_one_shot_button_action_beside_pointer_hold() {
        val source = read(
            "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/" +
                "ui/components/WaxSealReveal.kt",
        )

        assertContains(source, "role = Role.Button")
        assertContains(source, "onClick(label = a11y)")
        assertContains(source, ".pointerInput(reduced, completed, holdMs)")
    }

    @Test
    fun mafia_target_rows_expose_radio_selection_and_revalidate_stale_selection() {
        val source = read(
            "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/" +
                "ui/screens/night/TargetPickerScreen.kt",
        )

        assertContains(source, ".selectable(")
        assertContains(source, "role = Role.RadioButton")
        assertContains(source, "val validSelection")
        assertFalse(source.contains(".clickable(enabled = target.enabled"))
    }

    @Test
    fun recovery_surfaces_are_announced_and_continuous_indicators_are_motion_aware() {
        listOf(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "ReconnectingOverlay.kt",
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "HostDisconnectedOverlay.kt",
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "OfflineBanner.kt",
        ).forEach { path ->
            assertContains(read(path), "liveRegion")
        }

        val activitySources = listOf(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "CandleFlame.kt",
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "ReconnectingOverlay.kt",
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "OfflineBanner.kt",
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "ParlorButton.kt",
        )
        activitySources.forEach { path ->
            assertContains(read(path), "ParlorActivityIndicator")
        }
    }

    @Test
    fun passive_waiting_surfaces_do_not_publish_no_op_click_actions() {
        val router = read(
            "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/" +
                "ui/flow/WhodunitPhaseRouter.kt",
        )
        assertFalse(router.contains("onTap = {}"))
        assertFalse(router.contains("peer cannot acknowledge"))
        assertFalse(router.contains("only the named player's device"))
        assertContains(router, "onAcknowledge = null")
        assertContains(router, "is VoteTurnPresentation.WaitingForVoter -> {")
        assertContains(router, "PeerWaitingForHostScreen(")

        val mafiaRouter = read(
            "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/" +
                "multidevice/MafiaMultiDevicePhaseRouter.kt",
        )
        assertFalse(mafiaRouter.contains(".value ==="))
        assertContains(mafiaRouter, "canAcknowledgeAnnouncement(state, selfPlayerId)")
        assertContains(mafiaRouter, "onAcknowledged = if (mayAcknowledge)")
    }

    @Test
    fun game_catalog_renders_only_registered_shipping_games() {
        val home = read("composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt")
        val english = read("composeApp/src/commonMain/composeResources/values/strings.xml")
        val arabic = read("composeApp/src/commonMain/composeResources/values-ar/strings.xml")

        assertContains(home, "for (game in games)")
        assertFalse(home.contains("ComingSoonTile"))
        assertFalse(english.contains("home_coming_soon_state"))
        assertFalse(arabic.contains("home_coming_soon_state"))
    }

    @Test
    fun nullable_language_override_and_platform_motion_preference_reach_the_app_shell() {
        val app = read("composeApp/src/commonMain/kotlin/com/parlor/app/App.kt")
        val settings = read(
            "composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt",
        )

        assertContains(app, "languageTag?.let(AppLanguage::fromTag)")
        assertContains(app, "rememberSystemReducedMotion()")
        assertContains(app, "shouldReduceMotion(")
        assertContains(settings, "settings.setLanguageOverride(null)")
        assertContains(settings, ".selectableGroup()")
    }

    @Test
    fun fullscreen_recovery_and_confirmation_states_hide_or_replace_interactive_content() {
        val whodunitPeer = read(
            "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/whodunit/" +
                "WhodunitPeerSessionFlow.kt",
        )
        val mafiaPeer = read(
            "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/" +
                "multidevice/MafiaPeerLobbyFlow.kt",
        )
        val whodunitHost = read(
            "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/" +
                "WhodunitGameFlow.kt",
        )
        val mafiaHost = read(
            "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/" +
                "multidevice/MafiaMultiDeviceHostFlow.kt",
        )
        val reveal = read(
            "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/" +
                "WhodunitPhaseRouter.kt",
        )
        val privacyDialog = read(
            "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/" +
                "safety/PrivacyConcernOverlay.kt",
        )

        assertContains(whodunitPeer, "if (hostLost) Modifier.clearAndSetSemantics { }")
        assertContains(mafiaPeer, "if (hostLost) Modifier.clearAndSetSemantics { }")
        assertContains(whodunitPeer, "onHostLostChanged = { hostLost = it }")
        assertContains(mafiaPeer, "onHostLostChanged = { hostLost = it }")
        assertContains(whodunitHost, "when (val gate = startGate)")
        assertContains(mafiaHost, "when (val gate = startGate)")
        assertTrue(Regex("if \\(privacyOpen\\)").findAll(reveal).count() == 2)
        assertContains(privacyDialog, "variant = ParlorButtonVariant.Destructive")

        val reconnecting = read(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "ReconnectingOverlay.kt",
        )
        assertContains(reconnecting, "awaitPointerEvent(PointerEventPass.Initial)")
    }

    @Test
    fun platform_locale_overrides_are_commit_scoped_and_do_not_mutate_shared_resources() {
        val android = read(
            "shared/design-system/src/androidMain/kotlin/com/parlor/designsystem/localization/" +
                "LocalAppLocale.android.kt",
        )
        val ios = read(
            "shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/" +
                "LocalAppLocale.ios.kt",
        )
        val desktop = read(
            "shared/design-system/src/desktopMain/kotlin/com/parlor/designsystem/localization/" +
                "LocalAppLocale.desktop.kt",
        )

        listOf(android, ios, desktop).forEach { source ->
            assertContains(source, "DisposableEffect(")
            assertContains(source, "onDispose")
        }
        assertFalse(android.contains("updateConfiguration"))
        assertFalse(android.contains("@Suppress(\"DEPRECATION\")"))
        assertTrue(android.indexOf("Locale.setDefault") > android.indexOf("DisposableEffect("))
        assertTrue(ios.indexOf("userDefaults.setObject") > ios.indexOf("DisposableEffect("))
        assertTrue(desktop.indexOf("Locale.setDefault") > desktop.indexOf("DisposableEffect("))
    }

    @Test
    fun every_custom_click_or_selection_surface_declares_an_accessibility_role() {
        productionKotlinFiles().forEach { sourceFile ->
            val source = sourceFile.readText()
            Regex("\\.(clickable|selectable|toggleable)\\s*\\(").findAll(source).forEach { match ->
                val end = (match.range.last + INTERACTION_WINDOW_CHARS).coerceAtMost(source.length)
                val invocation = source.substring(match.range.first, end)
                assertContains(
                    invocation,
                    "role = Role.",
                    message =
                        "Missing role near ${sourceFile.relativeTo(root).path}:" +
                            lineAt(source, match.range.first),
                )
            }
        }
    }

    private fun read(path: String): String = File(root, path).readText()

    private fun productionKotlinFiles(): Sequence<File> = sequenceOf(
        File(root, "composeApp/src/commonMain"),
        File(root, "game-modes/mafia/src/commonMain"),
        File(root, "game-modes/whodunit/src/commonMain"),
        File(root, "shared/design-system/src/commonMain"),
    ).flatMap { sourceRoot ->
        sourceRoot.walkTopDown().asSequence().filter { file -> file.isFile && file.extension == "kt" }
    }

    private fun lineAt(source: String, offset: Int): Int = source.take(offset).count { it == '\n' } + 1

    private fun findProjectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }

    private companion object {
        const val INTERACTION_WINDOW_CHARS: Int = 320
    }
}

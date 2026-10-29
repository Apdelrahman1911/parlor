package com.parlor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.parlor.app.lifecycle.AppVisibility
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.home.HomeRecoveryAvailability
import com.parlor.app.shell.home.loadHomeRecoveryAvailability
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.snapshot.SnapshotStore

/** Do not probe protected recovery storage during startup inactivity or background residency. */
@Composable
internal fun rememberAppHomeRecovery(
    currentRoute: AppRoute,
    refreshKey: Int,
    visibility: AppVisibility,
    store: SnapshotStore,
    transport: RoomTransport,
    router: GameShellRouter,
): State<HomeRecoveryAvailability> = produceState<HomeRecoveryAvailability>(
    initialValue = HomeRecoveryAvailability.Loading,
    key1 = currentRoute,
    key2 = refreshKey,
    key3 = visibility,
) {
    value = HomeRecoveryAvailability.Loading
    if (currentRoute != AppRoute.Home || visibility != AppVisibility.Active) return@produceState
    value = loadHomeRecoveryAvailability(
        store = store,
        loadMultiplayer = transport::resumableSession,
        supportsLocalResume = { entry ->
            entry.gameId?.let { gameId -> router.resumeLocal(gameId, entry.sessionId) } != null
        },
        supportsMultiplayerResume = { info ->
            router.resumeMultiplayer(
                gameId = info.gameId,
                gameVersion = info.gameVersion,
                displayName = info.displayName,
                selection = info.deletionSelection,
            ) != null
        },
    )
}

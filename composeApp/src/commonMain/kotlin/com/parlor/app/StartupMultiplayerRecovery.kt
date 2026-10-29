package com.parlor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.parlor.app.lifecycle.AppVisibility
import com.parlor.app.shell.home.HomeRecoveryAvailability
import com.parlor.networking.transport.ResumableSessionInfo
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Process-scoped, non-persistent claim: root recreation and foregrounding cannot auto-retry. */
@OptIn(ExperimentalAtomicApi::class)
internal class StartupMultiplayerRecoveryGate {
    private val attempted = AtomicBoolean(false)

    fun claim(): Boolean = attempted.compareAndSet(expectedValue = false, newValue = true)
}

/** Automatic navigation is foreground-only and must not override any player decision or owned flow. */
internal fun permitsStartupMultiplayerRecovery(
    visibility: AppVisibility,
    navigationUntouched: Boolean,
    ownerIdle: Boolean,
    actionsBlocked: Boolean,
): Boolean = visibility == AppVisibility.Active && navigationUntouched && ownerIdle && !actionsBlocked

/** No suspension between the process claim, fresh ownership checks and selection-bound navigation. */
@Composable
internal fun AttemptStartupMultiplayerRecovery(
    recovery: HomeRecoveryAvailability,
    visibility: AppVisibility,
    gate: StartupMultiplayerRecoveryGate,
    canNavigate: () -> Boolean,
    navigate: (ResumableSessionInfo) -> Unit,
) {
    val currentCanNavigate by rememberUpdatedState(canNavigate)
    val currentNavigate by rememberUpdatedState(navigate)
    LaunchedEffect(recovery, visibility, gate) {
        val completed = recovery as? HomeRecoveryAvailability.Ready ?: return@LaunchedEffect
        if (visibility != AppVisibility.Active) return@LaunchedEffect
        // Failure/absence also consumes the completed foreground lookup. Future Retry is manual.
        if (!gate.claim() || !currentCanNavigate()) return@LaunchedEffect
        completed.resumableMultiplayer?.let(currentNavigate)
    }
}

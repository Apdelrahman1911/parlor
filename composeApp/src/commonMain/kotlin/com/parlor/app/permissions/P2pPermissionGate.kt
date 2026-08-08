package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import com.parlor.networking.transport.LocalNetworkAccess
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform boundary for the runtime permissions required by Party Play.
 *
 * Parlor's shipped transport is NSD/JmDNS plus TCP. It does not provision a
 * Wi-Fi network, so it requests no dangerous Android Nearby/Location
 * permission. The gate remains a platform seam for a future transport that
 * does require one, and exposes status, a request entry point, and an
 * `openAppSettings()` fallback. Apple Local Network access is deliberately
 * modeled from real transport evidence because iOS has no truthful preflight
 * API; an empty discovery result must never be called a proven denial.
 */
interface P2pPermissionGate {
    val status: StateFlow<PermissionStatus>

    /** Whether this platform exposes a relevant per-app network setting. */
    val canOpenNetworkSettings: Boolean

    /**
     * Triggers the platform's permission prompt. Suspends until the user
     * answers. On iOS this only records that the player chose to continue;
     * the system prompt appears when the following transport operation first
     * advertises or browses.
     */
    suspend fun request(): PermissionStatus

    /**
     * Opens the OS app-settings screen so the user can flip the permission
     * back on after it was permanently denied. No-op on platforms that have
     * no permission gate.
     */
    fun openAppSettings()
}

sealed interface PermissionStatus {
    /** This platform requires no runtime permission for Parlor's base LAN. */
    data object NotRequired : PermissionStatus

    /** A real advertise or authenticated-connect operation has succeeded. */
    data object GrantedOperational : PermissionStatus

    /** Apple access is unknown until a real LAN operation is attempted. */
    data object Unknown : PermissionStatus

    /** The transport is currently attempting to establish LAN operation. */
    data object Requesting : PermissionStatus

    /** A stable platform/API signal proved that Settings action is required. */
    data object DeniedActionable : PermissionStatus

    /** Failure was real, but could not truthfully be classified as denial. */
    data object FailureUnclassified : PermissionStatus
}

val PermissionStatus.entersMultiplayerWithoutRationale: Boolean
    get() = this == PermissionStatus.NotRequired || this == PermissionStatus.GrantedOperational

val PermissionStatus.mayAttemptNetwork: Boolean
    get() = this != PermissionStatus.DeniedActionable

/**
 * Platform-bound Composable factory. [networkAccess] is transport evidence,
 * not a permission guess. Lives in commonMain so navigation does not import
 * UIKit or Android APIs.
 */
@Composable
expect fun rememberP2pPermissionGate(
    networkAccess: StateFlow<LocalNetworkAccess>,
): P2pPermissionGate

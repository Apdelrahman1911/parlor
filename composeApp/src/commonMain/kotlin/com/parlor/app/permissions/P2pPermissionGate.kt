package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform boundary for the runtime permissions required by Party Play.
 *
 * Parlor's shipped transport is NSD/JmDNS plus TCP. It does not provision a
 * Wi-Fi network, so it requests no dangerous Android Nearby/Location
 * permission. The gate remains a platform seam for a future transport that
 * does require one, and exposes status, a request entry point, and an
 * `openAppSettings()` fallback for the permanently-denied case.
 *
 * On Desktop and iOS there is no runtime gate for LAN discovery, so the
 * actuals report [PermissionStatus.Granted] unconditionally and the rest
 * of the UI flow short-circuits the rationale screen entirely.
 */
interface P2pPermissionGate {
    val status: StateFlow<PermissionStatus>

    /**
     * Triggers the platform's permission prompt. Suspends until the user
     * answers (or, on platforms without a prompt, returns immediately with
     * [PermissionStatus.Granted]).
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
    /** Permission has been granted (or is not required on this platform). */
    data object Granted : PermissionStatus

    /** Status not yet known — first run or no Activity context yet. */
    data object NotRequested : PermissionStatus

    /** User denied once but can be asked again. */
    data object Denied : PermissionStatus

    /**
     * "Don't ask again" / system-suppressed dialog — only the settings
     * screen can flip this back. The UI surfaces an `openAppSettings()` CTA.
     */
    data object PermanentlyDenied : PermissionStatus
}

/**
 * Platform-bound Composable factory. The current Android, Desktop, and iOS
 * LAN actuals return a no-op granted gate; the OS handles Android firewall
 * and Apple Local Network prompts when the transport first touches the
 * network. Lives in commonMain so navigation does not import platform APIs.
 */
@Composable
expect fun rememberP2pPermissionGate(): P2pPermissionGate

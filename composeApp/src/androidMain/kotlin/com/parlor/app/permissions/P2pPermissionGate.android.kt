package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P2pKit's LAN transport uses NSD/TCP and needs no dangerous runtime
 * permission. Provisioning-only Nearby/Location permissions are intentionally
 * absent until Parlor ships a hotspot or Wi-Fi-join feature.
 */
@Composable
actual fun rememberP2pPermissionGate(): P2pPermissionGate = remember { AndroidLanPermissionGate }

private object AndroidLanPermissionGate : P2pPermissionGate {
    private val _status = MutableStateFlow<PermissionStatus>(PermissionStatus.Granted)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()
    override suspend fun request(): PermissionStatus = PermissionStatus.Granted
    override fun openAppSettings() = Unit
}

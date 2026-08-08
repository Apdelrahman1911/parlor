package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.parlor.networking.transport.LocalNetworkAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P2pKit's LAN transport uses NSD/TCP and needs no dangerous runtime
 * permission. Provisioning-only Nearby/Location permissions are intentionally
 * absent until Parlor ships a hotspot or Wi-Fi-join feature.
 */
@Composable
actual fun rememberP2pPermissionGate(
    networkAccess: StateFlow<LocalNetworkAccess>,
): P2pPermissionGate = remember { AndroidLanPermissionGate }

private object AndroidLanPermissionGate : P2pPermissionGate {
    override val canOpenNetworkSettings: Boolean = false
    private val _status = MutableStateFlow<PermissionStatus>(PermissionStatus.NotRequired)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()
    override suspend fun request(): PermissionStatus = PermissionStatus.NotRequired
    override fun openAppSettings() = Unit
}

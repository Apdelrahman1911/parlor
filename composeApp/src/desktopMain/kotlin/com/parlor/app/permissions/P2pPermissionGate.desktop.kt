package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.parlor.networking.transport.LocalNetworkAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop has no runtime permission gate for LAN discovery — the OS firewall
 * dialog (if any) is handled outside the app. Return a constant
 * [PermissionStatus.NotRequired] gate so the shared rationale UI short-circuits cleanly.
 */
@Composable
actual fun rememberP2pPermissionGate(
    networkAccess: StateFlow<LocalNetworkAccess>,
): P2pPermissionGate = remember { DesktopNoopGate }

private object DesktopNoopGate : P2pPermissionGate {
    override val canOpenNetworkSettings: Boolean = false
    private val _status = MutableStateFlow<PermissionStatus>(PermissionStatus.NotRequired)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()
    override suspend fun request(): PermissionStatus = PermissionStatus.NotRequired
    override fun openAppSettings() = Unit
}

package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop has no runtime permission gate for LAN discovery — the OS firewall
 * dialog (if any) is handled outside the app. Return a constant [Granted]
 * gate so the shared rationale UI short-circuits cleanly.
 */
@Composable
actual fun rememberP2pPermissionGate(): P2pPermissionGate = remember { DesktopNoopGate }

private object DesktopNoopGate : P2pPermissionGate {
    private val _status = MutableStateFlow<PermissionStatus>(PermissionStatus.Granted)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()
    override suspend fun request(): PermissionStatus = PermissionStatus.Granted
    override fun openAppSettings() = Unit
}

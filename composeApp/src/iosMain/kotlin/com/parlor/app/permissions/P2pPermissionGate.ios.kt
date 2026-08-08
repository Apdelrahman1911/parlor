package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS asks for Local Network access inline when Network.framework first tries
 * to advertise or discover the Bonjour service. There is no app-side runtime
 * permission API to manage, so this gate reports [Granted] and the rationale
 * UI is skipped. The Info.plist usage description handles the copy.
 */
@Composable
actual fun rememberP2pPermissionGate(): P2pPermissionGate = remember { IosNoopGate }

private object IosNoopGate : P2pPermissionGate {
    private val _status = MutableStateFlow<PermissionStatus>(PermissionStatus.Granted)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()
    override suspend fun request(): PermissionStatus = PermissionStatus.Granted
    override fun openAppSettings() = Unit
}

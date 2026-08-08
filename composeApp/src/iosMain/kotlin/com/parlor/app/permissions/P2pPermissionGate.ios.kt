package com.parlor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.parlor.networking.transport.LocalNetworkAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS asks for Local Network access inline when Network.framework first
 * advertises or browses. There is no truthful preflight API, so this gate maps
 * only transport-observed evidence and never reports a synthetic grant.
 */
@Composable
actual fun rememberP2pPermissionGate(
    networkAccess: StateFlow<LocalNetworkAccess>,
): P2pPermissionGate {
    val access by networkAccess.collectAsState()
    return remember(access) { IosLocalNetworkGate(access.toPermissionStatus()) }
}

private class IosLocalNetworkGate(initialStatus: PermissionStatus) : P2pPermissionGate {
    override val canOpenNetworkSettings: Boolean = true
    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    override suspend fun request(): PermissionStatus {
        // There is no preflight to re-read after Settings. Returning Unknown
        // explicitly authorizes a new real transport attempt without claiming
        // the setting is now enabled.
        _status.value = PermissionStatus.Unknown
        return PermissionStatus.Unknown
    }

    override fun openAppSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(settingsUrl)
    }
}

private fun LocalNetworkAccess.toPermissionStatus(): PermissionStatus = when (this) {
    LocalNetworkAccess.NotApplicable -> PermissionStatus.Unknown
    LocalNetworkAccess.Unknown -> PermissionStatus.Unknown
    LocalNetworkAccess.Attempting -> PermissionStatus.Requesting
    LocalNetworkAccess.Operational -> PermissionStatus.GrantedOperational
    LocalNetworkAccess.PermissionDenied -> PermissionStatus.DeniedActionable
    LocalNetworkAccess.FailureUnclassified -> PermissionStatus.FailureUnclassified
}

package com.parlor.app.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The runtime permissions required for P2pKit's LAN discovery. The set is
 * SDK-conditional because Google split discovery off `ACCESS_FINE_LOCATION`
 * onto the new `NEARBY_WIFI_DEVICES` permission in API 33.
 */
private val requiredPermissions: List<String>
    get() = if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
actual fun rememberP2pPermissionGate(): P2pPermissionGate {
    val context = LocalContext.current
    val activity = context.findActivity()
    val gate = remember(activity) { AndroidP2pPermissionGate(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        gate.onResult(results)
    }
    DisposableEffect(launcher) {
        gate.attachLauncher(launcher::launch)
        onDispose { gate.detachLauncher() }
    }
    // Re-read OS state every recomposition so the gate reflects a user who
    // toggled the permission via Settings outside the app.
    DisposableEffect(context) {
        gate.refreshStatus()
        onDispose {}
    }
    return gate
}

private class AndroidP2pPermissionGate(
    private val context: Context,
) : P2pPermissionGate {

    private val _status = MutableStateFlow<PermissionStatus>(PermissionStatus.NotRequested)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    private var launch: ((Array<String>) -> Unit)? = null
    private var pending: CompletableDeferred<PermissionStatus>? = null

    fun attachLauncher(launch: (Array<String>) -> Unit) {
        this.launch = launch
    }

    fun detachLauncher() {
        launch = null
        pending?.cancel()
        pending = null
    }

    fun refreshStatus() {
        _status.value = currentSystemStatus()
    }

    private fun currentSystemStatus(): PermissionStatus {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return PermissionStatus.Granted
        val activity = context.findActivity()
        // shouldShowRequestPermissionRationale returns true *after* the user
        // has denied once but not "don't ask again". Before any request it
        // returns false on Android 12-, but that's "NotRequested" semantically,
        // so default to Denied only when we've actually asked at least once
        // (tracked via _status NOT being NotRequested already).
        val sticky = activity != null && missing.all {
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        val prior = _status.value
        return when {
            prior is PermissionStatus.NotRequested && sticky && !hasEverRequested -> {
                // Fresh install, no prior request — treat as NotRequested so we
                // show the rationale instead of jumping to "open settings".
                PermissionStatus.NotRequested
            }
            sticky && hasEverRequested -> PermissionStatus.PermanentlyDenied
            else -> PermissionStatus.Denied
        }
    }

    private var hasEverRequested = false

    override suspend fun request(): PermissionStatus {
        val currentMissing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (currentMissing.isEmpty()) {
            _status.value = PermissionStatus.Granted
            return PermissionStatus.Granted
        }
        val l = launch ?: run {
            // No launcher attached yet (shouldn't happen if the composable
            // ran). Return current best-effort status.
            _status.value = currentSystemStatus()
            return _status.value
        }
        val deferred = CompletableDeferred<PermissionStatus>()
        pending = deferred
        hasEverRequested = true
        l(currentMissing.toTypedArray())
        return deferred.await()
    }

    fun onResult(results: Map<String, Boolean>) {
        val allGranted = requiredPermissions.all { results[it] == true ||
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        val newStatus = if (allGranted) {
            PermissionStatus.Granted
        } else {
            val activity = context.findActivity()
            val sticky = activity != null && requiredPermissions.all {
                results[it] == false && !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            if (sticky) PermissionStatus.PermanentlyDenied else PermissionStatus.Denied
        }
        _status.value = newStatus
        pending?.complete(newStatus)
        pending = null
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

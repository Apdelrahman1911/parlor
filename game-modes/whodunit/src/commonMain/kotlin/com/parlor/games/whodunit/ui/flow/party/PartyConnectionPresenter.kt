package com.parlor.games.whodunit.ui.flow.party

import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.networking.room.PeerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Translates [PeerEvent]s into [com.parlor.designsystem.components.ParlorToast]
 * inserts on the app-wide toast queue, and into a small set of
 * "show / hide" callbacks for the offline banner + reconnecting
 * overlay states the UI tracks separately.
 *
 * Lives at the screen / flow scope. Host vs peer call sites pass in
 * the appropriate SharedFlow (the host bridge has `room.peerEvents`;
 * the peer bridge has `WhodunitPeerRoomBridge.connectionEvents` which
 * already merges transport + bridge-synthesised events).
 *
 * The toast text is supplied via [resolveToast] — we don't import
 * resources here, so the design system stays free of localized
 * strings and the presenter is testable without a compose resource
 * loader.
 */
class PartyConnectionPresenter(
    private val toastState: ParlorToastState,
    private val resolveToast: (ToastKey) -> String?,
    private val onHostLostChanged: (Boolean) -> Unit = {},
    private val onSelfOfflineChanged: (Boolean) -> Unit = {},
) {

    /**
     * Sealed key for the small set of party-connection toasts. The caller
     * maps each key to a localized string via the [resolveToast] callback.
     */
    sealed interface ToastKey {
        data class PeerLeft(val displayName: String) : ToastKey
        data class PeerReconnected(val displayName: String) : ToastKey
        data object HostRestored : ToastKey
    }

    private var job: Job? = null

    /** Begin consuming events. Cancel via [stop] to detach. */
    fun start(events: SharedFlow<PeerEvent>, scope: CoroutineScope) {
        stop()
        job = scope.launch {
            events.collect { event -> handle(event) }
        }
    }

    fun stop() {
        job?.cancel(); job = null
    }

    internal fun handle(event: PeerEvent) {
        when (event) {
            is PeerEvent.PeerLeft -> showToast(
                ToastKey.PeerLeft(event.displayName),
                ParlorToastSeverity.Warning,
            )
            is PeerEvent.PeerReconnected -> showToast(
                ToastKey.PeerReconnected(event.displayName),
                ParlorToastSeverity.Success,
            )
            is PeerEvent.PeerJoined -> Unit  // initial join handled by lobby
            PeerEvent.HostLost -> onHostLostChanged(true)
            PeerEvent.HostRestored -> {
                onHostLostChanged(false)
                showToast(ToastKey.HostRestored, ParlorToastSeverity.Info)
            }
            PeerEvent.SelfOffline -> onSelfOfflineChanged(true)
            PeerEvent.SelfOnline -> onSelfOfflineChanged(false)
        }
    }

    private fun showToast(key: ToastKey, severity: ParlorToastSeverity) {
        val text = resolveToast(key) ?: return
        toastState.show(text, severity)
    }
}

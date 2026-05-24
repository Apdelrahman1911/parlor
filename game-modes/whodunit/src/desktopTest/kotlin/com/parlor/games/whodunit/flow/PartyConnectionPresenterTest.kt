package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.games.whodunit.ui.flow.party.PartyConnectionPresenter
import com.parlor.networking.room.PeerEvent
import kotlin.test.Test

/**
 * Wave 9H-7: [PartyConnectionPresenter] turns [PeerEvent]s into toasts
 * + offline / reconnecting flags. The tests assert each event lands in
 * the right slot — we don't exercise the SharedFlow plumbing here
 * because that's just a 5-line collector.
 */
class PartyConnectionPresenterTest {

    private val alice = PlayerId("alice")

    private fun build(
        toast: ParlorToastState = ParlorToastState(),
        hostLost: MutableList<Boolean> = mutableListOf(),
        selfOffline: MutableList<Boolean> = mutableListOf(),
    ): PartyConnectionPresenter = PartyConnectionPresenter(
        toastState = toast,
        resolveToast = { key ->
            when (key) {
                is PartyConnectionPresenter.ToastKey.PeerLeft -> "${key.displayName} disconnected"
                is PartyConnectionPresenter.ToastKey.PeerReconnected -> "${key.displayName} reconnected"
                PartyConnectionPresenter.ToastKey.HostRestored -> "Reconnected to host"
            }
        },
        onHostLostChanged = { hostLost += it },
        onSelfOfflineChanged = { selfOffline += it },
    )

    @Test
    fun peer_left_pushes_warning_toast() {
        val toast = ParlorToastState()
        val presenter = build(toast = toast)
        presenter.handle(PeerEvent.PeerLeft(alice, "Alice"))
        assertThat(toast.toasts.value.map { it.text }).contains("Alice disconnected")
    }

    @Test
    fun peer_reconnected_pushes_success_toast() {
        val toast = ParlorToastState()
        val presenter = build(toast = toast)
        presenter.handle(PeerEvent.PeerReconnected(alice, "Alice"))
        assertThat(toast.toasts.value.map { it.text }).contains("Alice reconnected")
    }

    @Test
    fun host_restored_pushes_info_toast_and_clears_hostLost() {
        val toast = ParlorToastState()
        val hostLost = mutableListOf<Boolean>()
        val presenter = build(toast = toast, hostLost = hostLost)
        presenter.handle(PeerEvent.HostLost)
        presenter.handle(PeerEvent.HostRestored)
        assertThat(toast.toasts.value.map { it.text }).contains("Reconnected to host")
        assertThat(hostLost).isEqualTo(listOf(true, false))
    }

    @Test
    fun self_offline_and_self_online_drive_banner_flag_without_toasting() {
        val toast = ParlorToastState()
        val flags = mutableListOf<Boolean>()
        val presenter = build(toast = toast, selfOffline = flags)
        presenter.handle(PeerEvent.SelfOffline)
        presenter.handle(PeerEvent.SelfOnline)
        assertThat(flags).isEqualTo(listOf(true, false))
        // No toast for SelfOffline/SelfOnline — the banner is the only surface.
        assertThat(toast.toasts.value).isEqualTo(emptyList())
    }

    @Test
    fun peer_joined_emits_no_toast() {
        val toast = ParlorToastState()
        val presenter = build(toast = toast)
        presenter.handle(PeerEvent.PeerJoined(alice, "Alice"))
        assertThat(toast.toasts.value).isEqualTo(emptyList())
    }
}

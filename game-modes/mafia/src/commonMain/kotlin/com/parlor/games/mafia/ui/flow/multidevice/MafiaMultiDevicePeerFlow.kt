package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.party.MafiaReadinessGate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PeerEvent
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.party.PartyAwareSession
import org.koin.compose.koinInject

/**
 * Multi-device **peer** entry for Mafia.
 *
 * Mirrors `WhodunitMultiplayerPeerFlow` for the Mafia domain. The peer
 * spins up a [MafiaPeerRoomBridge] whose [com.parlor.session.multidevice.ShadowSessionController]
 * is updated by inbound host snapshots and direct-sent private slices. The
 * peer never reduces game state locally — every action it submits is sent
 * to the host over the wire, and every state change is reflected only when
 * the host's snapshot arrives.
 *
 * Privacy on this device is structural: the bridge inbox rejects any
 * `PrivateStateForPlayer` whose `target != selfPlayerId`, and the host
 * never produced another peer's `MafiaPrivate` for transmission in the
 * first place. The router has no way to render another peer's role,
 * coordination snapshot, or detective result because that data simply
 * isn't on this device.
 *
 * Connection chrome ([onHostLostChanged] / [onSelfOfflineChanged]) is
 * fed by the bridge's `connectionEvents` SharedFlow — the same shape used
 * by Whodunit so `PeerSessionFlow` can drive `ReconnectingOverlay` and
 * `OfflineBanner` at the shell root.
 */
@Composable
fun MafiaMultiDevicePeerFlow(
    players: List<Player>,
    selfPlayerId: PlayerId,
    seed: Long,
    room: LocalRoom,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    onHostLostChanged: (Boolean) -> Unit = {},
    onSelfOfflineChanged: (Boolean) -> Unit = {},
) {
    val definition: MafiaDefinition = koinInject()
    val scope = rememberCoroutineScope()

    val initialState = remember(players, seed) {
        definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("mafia-mp-peer-${seed.toString(16)}"),
                caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId,
                players = players,
                randomSeed = seed,
            ),
        )
    }

    val bridge = remember(room, selfPlayerId) {
        MafiaPeerRoomBridge(
            room = room,
            selfPlayerId = selfPlayerId,
            initialPublic = initialState,
            scope = scope,
        )
    }
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    LaunchedEffect(bridge) {
        bridge.hostDisconnected.collect { onBackToHome() }
    }

    LaunchedEffect(bridge) {
        bridge.connectionEvents.collect { event ->
            when (event) {
                PeerEvent.HostLost -> onHostLostChanged(true)
                PeerEvent.HostRestored -> onHostLostChanged(false)
                PeerEvent.SelfOffline -> onSelfOfflineChanged(true)
                PeerEvent.SelfOnline -> onSelfOfflineChanged(false)
                else -> Unit
            }
        }
    }

    val peerPlayMode = remember(selfPlayerId) {
        PlayMode.MultiDevice(selfPlayerId = selfPlayerId, isHost = false)
    }
    val session: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        remember(bridge.controller, peerPlayMode) {
            // Wrapper is a transparent pass-through on peers — wired in for
            // shape uniformity with the local entry. The host runs the
            // readiness gate that actually issues auto-acks.
            PartyAwareSession(bridge.controller, peerPlayMode, MafiaReadinessGate)
        }
    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state

    Box(modifier = modifier.fillMaxSize()) {
        MafiaMultiDevicePhaseRouter(
            state = state,
            selfPlayerId = selfPlayerId,
            isHost = false,
            session = session,
            scope = scope,
            onBackToHome = onBackToHome,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

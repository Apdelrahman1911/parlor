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
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.Clock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.party.MafiaReadinessGate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.SendTarget
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.party.PartyAwareSession
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Multi-device **host** entry for Mafia.
 *
 * Mirrors `WhodunitMultiplayerHostFlow` one-for-one but for the Mafia
 * domain types. The host runs the canonical reducer through a
 * [PassAndPlaySessionController], wraps it in a [PartyAwareSession] with
 * [MafiaReadinessGate] so phase-advance ticks behave identically to
 * pass-and-play, and shells the room transport through a
 * [MafiaHostRoomBridge] that:
 *
 *  - Broadcasts `MafiaProjectionPolicy.toPublic` snapshots on every host
 *    state change.
 *  - Direct-sends each peer their own `MafiaPrivate` slice.
 *  - Ingests `PeerMessage.ActionSubmit`, authorising via
 *    [com.parlor.games.mafia.domain.authority.MafiaActionAuthority] before
 *    forwarding to the controller.
 *
 * The host UI itself renders against the canonical state — same
 * [MafiaMultiDevicePhaseRouter] peers use, but with `isHost = true` so
 * host-only chrome (Resolve night, Open vote, host's setup screen) is
 * surfaced. Privacy is structural: peers receive only their own slice
 * because the bridge never produces another peer's slice for transmission.
 *
 * Pause is not implemented for Mafia in this version (no `paused` field in
 * `MafiaPublic`), so the pause overlay from the Whodunit flow is omitted.
 */
@Composable
fun MafiaMultiDeviceHostFlow(
    players: List<Player>,
    seed: Long,
    room: LocalRoom,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock: Clock = koinInject()
    val definition: MafiaDefinition = koinInject()
    val scope = rememberCoroutineScope()

    val sessionConfig = remember(players, seed) {
        SessionConfig(
            sessionId = SessionId("mafia-mp-host-${seed.toString(16)}"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = players,
            randomSeed = seed,
        )
    }
    val hostPlayMode = remember(room) {
        PlayMode.MultiDevice(selfPlayerId = room.selfPlayerId, isHost = true)
    }
    val rawSession = remember(sessionConfig) {
        PassAndPlaySessionController(
            definition = definition,
            config = sessionConfig,
            reducerContext = DefaultReducerContext(
                clock = clock,
                random = RandomSource.seeded(seed),
            ),
            scope = scope,
        )
    }
    // Bridge talks to the raw controller (it needs hostState to project
    // private slices). The UI submits through the PartyAwareSession wrapper
    // so phase-advance ticks behave identically to pass-and-play; in
    // MultiDevice mode the wrapper is a transparent pass-through for peer
    // actions (peers ack themselves) but still applies host-side gates.
    val session: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        remember(rawSession, hostPlayMode) {
            PartyAwareSession(rawSession, hostPlayMode, MafiaReadinessGate)
        }
    val bridge = remember(rawSession, room, players) {
        MafiaHostRoomBridge(rawSession, room, players, scope)
    }
    LaunchedEffect(bridge) {
        bridge.announceStart(
            caseId = "default",
            modeId = MafiaIds.ClassicModeId.raw,
            seed = seed,
        )
    }
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state

    Box(modifier = modifier.fillMaxSize()) {
        MafiaMultiDevicePhaseRouter(
            state = state,
            selfPlayerId = room.selfPlayerId,
            isHost = true,
            session = session,
            scope = scope,
            onBackToHome = {
                scope.launch {
                    runCatching { room.send(SendTarget.Broadcast, HostMessage.EndSession) }
                    onBackToHome()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

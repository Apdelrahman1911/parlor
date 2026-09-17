package com.parlor.app.shell.game.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_action_failed
import com.parlor.app.resources.room_game_action_recovery
import com.parlor.app.resources.room_game_ended
import com.parlor.app.resources.room_game_ended_body
import com.parlor.app.resources.room_game_error
import com.parlor.app.resources.room_game_host_recovery
import com.parlor.app.resources.room_game_loading
import com.parlor.app.resources.room_game_private_cover
import com.parlor.app.resources.room_game_recovery
import com.parlor.app.resources.room_game_recovery_body
import com.parlor.app.resources.room_game_starting
import com.parlor.app.resources.room_game_wait_player
import com.parlor.core.ids.CaseId
import com.parlor.core.time.Clock
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.PeerCommandDelivery
import com.parlor.session.multidevice.PeerCommandProgress
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.RetainedValueResult
import com.parlor.session.multidevice.ValidatedSessionStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun <S : GameState, A : GameAction, E : GameEvent> HostedGamePanel(
    spec: MultiplayerGameSpec<S, A, E>, session: ProcessMultiplayerSession, roster: List<RoomMember>, caseId: CaseId,
    onLeave: () -> Unit, onNewRoom: () -> Unit, busy: Boolean, modifier: Modifier,
) {
    val clock: Clock = koinInject()
    val lookup by produceState<RetainedValueResult<*>?>(null, session) {
        value = session.getOrCreateRuntime(spec.kind("host")) { scope ->
            val info = session.room.info.value
            val players = listOf(Player(info.hostPlayerId, info.hostDisplayName, 0)) +
                roster.mapIndexed { index, member -> Player(member.playerId, member.displayName, index + 1) }
            require(spec.validRoster(players) && spec.acceptsSettings(caseId.raw, players.size))
            HostedGameRuntime(spec, clock, players, checkNotNull(session.hostSeed), caseId, session.room, scope)
        }
    }
    val runtime = hostRuntime(lookup, spec)
    if (runtime == null) {
        RoomGameStatus(stringResource(if (lookup == null) Res.string.room_game_starting else Res.string.room_game_error),
            onLeave, modifier, onNewRoom = onNewRoom.takeIf { lookup != null }, enabled = !busy)
        return
    }
    val gate by runtime.startGate.collectAsState()
    val terminal by runtime.bridge.terminalReason.collectAsState()
    val own by runtime.session.privateStateFor(session.room.selfPlayerId).collectAsState()
    when {
        terminal != null || spec.isAborted(own.state) -> RoomGameStatus(stringResource(Res.string.room_game_ended), onLeave, modifier,
            body = stringResource(Res.string.room_game_ended_body), onNewRoom = onNewRoom, enabled = !busy)
        gate is HostStartGateState.Failed -> RoomGameStatus(stringResource(Res.string.room_game_error), onLeave, modifier,
            body = roomGameError((gate as HostStartGateState.Failed).error), onNewRoom = onNewRoom, enabled = !busy)
        gate != HostStartGateState.Started -> RoomGameStatus(
            stringResource(Res.string.room_game_starting), onLeave, modifier, enabled = !busy)
        else -> GameTableSurface(spec, own.state, true, session.room, runtime.actions,
            runtime.bridge.recoveryEpoch, onLeave, busy, modifier)
    }
}

@Composable
internal fun <S : GameState, A : GameAction, E : GameEvent> PeerGamePanel(
    spec: MultiplayerGameSpec<S, A, E>, session: ProcessMultiplayerSession, start: ValidatedSessionStart,
    onLeave: () -> Unit, onFinalLeave: () -> Unit, busy: Boolean, modifier: Modifier,
) {
    val lookup by produceState<RetainedValueResult<*>?>(null, session) {
        value = session.getOrCreateRuntime(spec.kind("peer")) { scope ->
            PeerGameRuntime(spec, start.offer, start.protocol, session.room, scope)
        }
    }
    val runtime = peerRuntime(lookup, spec)
    if (runtime == null) {
        RoomGameStatus(stringResource(if (lookup == null) Res.string.room_game_loading else Res.string.room_game_error),
            onLeave, modifier, enabled = !busy)
        return
    }
    val bridge = runtime.bridge
    GameCommandOutcomeEffect(bridge)
    val terminal by bridge.terminalReason.collectAsState()
    val terminalError by bridge.terminalError.collectAsState()
    val hasSnapshot by bridge.hasAuthoritativeSnapshot.collectAsState()
    val snapshotError by bridge.initialSnapshotError.collectAsState()
    val connection by bridge.connectionState.collectAsState()
    val progress by bridge.commandProgress.collectAsState()
    val own by runtime.session.privateStateFor(session.room.selfPlayerId).collectAsState()
    when {
        terminal != null || terminalError != null || spec.isAborted(own.state) ->
            RoomGameStatus(stringResource(Res.string.room_game_ended), onFinalLeave, modifier,
                body = stringResource(Res.string.room_game_ended_body), enabled = !busy)
        !hasSnapshot -> RoomGameStatus(
            stringResource(if (snapshotError == null) Res.string.room_game_loading else Res.string.room_game_error),
            onLeave, modifier, enabled = !busy)
        connection.hostLost || connection.selfOffline -> RoomGameStatus(
            title = if (connection.hostLost) stringResource(Res.string.room_game_host_recovery,
                session.room.info.value.hostDisplayName.asBidiArgument())
                else stringResource(Res.string.room_game_recovery),
            onLeave = onLeave, modifier = modifier, body = stringResource(Res.string.room_game_recovery_body), enabled = !busy,
        )
        else -> GameTableSurface(spec, own.state, false, session.room, runtime.actions,
            bridge.recoveryEpoch, onLeave, busy || progress !is PeerCommandProgress.Idle, modifier)
    }
}

@Composable
private fun <S : GameState, A : GameAction, E : GameEvent> GameTableSurface(
    spec: MultiplayerGameSpec<S, A, E>, state: S, isHost: Boolean, room: LocalRoom,
    actions: GameActionSubmission<A>, recovery: StateFlow<Long>, onLeave: () -> Unit, busy: Boolean, modifier: Modifier,
) {
    val lifecycle: AppLifecycleCoordinator = koinInject()
    val visibility by lifecycle.visibility.collectAsState()
    val roomLifecycle by room.lifecycle.collectAsState()
    val foregroundReady by room.foregroundReady.collectAsState()
    val recoveryEpoch by recovery.collectAsState()
    val pending by actions.pending.collectAsState()
    val failure by actions.failure.collectAsState()
    val toast = LocalParlorToastState.current
    val failedCopy = stringResource(Res.string.room_game_action_failed)
    val active = remember(room, actions) { MutableStateFlow(true) }
    DisposableEffect(active) { onDispose { active.value = false } }
    LaunchedEffect(failure) {
        failure?.let { toast.show(failedCopy, ParlorToastSeverity.Warning); actions.acknowledgeFailure(it) }
    }
    val missing = state.players.firstOrNull { it.id in spec.disconnected(state) }
    val ready = roomLifecycle == RoomLifecycleState.Active && foregroundReady && missing == null
    val stillAllowed = {
        val current = lifecycle.visibility.value
        active.value && !current.privateContentCovered && current.privacyEpoch == visibility.privacyEpoch &&
            recovery.value == recoveryEpoch && room.lifecycle.value == RoomLifecycleState.Active && room.foregroundReady.value
    }
    when {
        visibility.privateContentCovered -> RoomGameStatus(
            stringResource(Res.string.room_game_private_cover), onLeave, modifier, enabled = !busy)
        !ready -> RoomGameStatus(
            title = if (missing != null) stringResource(Res.string.room_game_wait_player, missing.displayName.asBidiArgument())
                else stringResource(Res.string.room_game_recovery),
            onLeave = onLeave, modifier = modifier, body = stringResource(Res.string.room_game_recovery_body), enabled = !busy,
        )
        else -> spec.Table(state, room.selfPlayerId, isHost, !busy && !pending,
            combinedGamePrivacyEpoch(visibility.privacyEpoch, recoveryEpoch),
            onAction = { actions.trySubmit(it, !busy && !pending, stillAllowed) }, onLeave = onLeave, modifier = modifier)
    }
}

@Composable
private fun <S : GameState, A : GameAction, E : GameEvent> GameCommandOutcomeEffect(bridge: PeerGameBridge<S, A, E>) {
    val toast = LocalParlorToastState.current
    val rejected = stringResource(Res.string.room_game_action_failed)
    val recovering = stringResource(Res.string.room_game_action_recovery)
    LaunchedEffect(bridge, rejected, recovering) {
        bridge.commandProgress.collect { progress ->
            if (progress is PeerCommandProgress.Awaiting && progress.delivery == PeerCommandDelivery.RecoveryTimedOut) {
                toast.show(recovering, ParlorToastSeverity.Warning)
            }
            val resolved = progress as? PeerCommandProgress.Resolved ?: return@collect
            if (resolved.outcome.status != CommandStatus.Applied) toast.show(rejected, ParlorToastSeverity.Warning)
            bridge.acknowledgeCommandOutcome(resolved.outcome.commandId)
        }
    }
}

// The retained key binds game ID, role and codec version; the registry forbids duplicate IDs.
// Verify that key before the single erasure cast at the process-owned runtime boundary.
@Suppress("UNCHECKED_CAST")
private fun <S : GameState, A : GameAction, E : GameEvent> hostRuntime(
    lookup: RetainedValueResult<*>?, spec: MultiplayerGameSpec<S, A, E>,
): HostedGameRuntime<S, A, E>? = ((lookup as? RetainedValueResult.Ready<*>)?.value as? HostedGameRuntime<*, *, *>)
    ?.takeIf { it.runtimeKind == spec.kind("host") } as? HostedGameRuntime<S, A, E>

@Suppress("UNCHECKED_CAST") // Same validated retained-key boundary as hostRuntime.
private fun <S : GameState, A : GameAction, E : GameEvent> peerRuntime(
    lookup: RetainedValueResult<*>?, spec: MultiplayerGameSpec<S, A, E>,
): PeerGameRuntime<S, A, E>? = ((lookup as? RetainedValueResult.Ready<*>)?.value as? PeerGameRuntime<*, *, *>)
    ?.takeIf { it.runtimeKind == spec.kind("peer") } as? PeerGameRuntime<S, A, E>

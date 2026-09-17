package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_action_failed
import com.parlor.app.resources.room_game_approve
import com.parlor.app.resources.room_game_cannot_start
import com.parlor.app.resources.room_game_code
import com.parlor.app.resources.room_game_decline
import com.parlor.app.resources.room_game_host
import com.parlor.app.resources.room_game_leave
import com.parlor.app.resources.room_game_local
import com.parlor.app.resources.room_game_players
import com.parlor.app.resources.room_game_request
import com.parlor.app.resources.room_game_start
import com.parlor.app.resources.room_game_wait
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.RoomMember
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun <S : GameState, A : GameAction, E : GameEvent> HostRoomControls(
    spec: MultiplayerGameSpec<S, A, E>, room: LocalRoom, setup: GameHostSetupCheckpoint,
    onStart: () -> Unit, onLeave: () -> Unit, operationInFlight: Boolean, modifier: Modifier,
) {
    val info by room.info.collectAsState()
    val members by room.members.collectAsState()
    val pending by room.pendingAdmissions.collectAsState()
    val state by setup.state.collectAsState()
    val foregroundReady by room.foregroundReady.collectAsState()
    val connected = members.filter(RoomMember::connected)
    val count = connected.size + 1
    var admitting by remember(room) { mutableStateOf(emptySet<PlayerId>()) }
    val scope = rememberCoroutineScope()
    val toast = LocalParlorToastState.current
    val failed = stringResource(Res.string.room_game_action_failed)
    val enabled = !operationInFlight && !state.starting && !state.frozen && foregroundReady
    val canStart = enabled && admitting.isEmpty() && pending.isEmpty() && spec.acceptsSettings(state.caseId.raw, count)
    HeroBackdrop(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).parlorSafeContentPadding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(stringResource(Res.string.room_game_local), color = ParlorTheme.colors.textSecondary)
            ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.l, hero = true) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    Text(stringResource(Res.string.room_game_code), color = ParlorTheme.colors.textSecondary)
                    Text(info.code, color = ParlorTheme.colors.accentEmber, style = ParlorTheme.typography.displayHero)
                    Text(stringResource(Res.string.room_game_host, info.hostDisplayName), color = ParlorTheme.colors.textPrimary)
                }
            }
            Text(stringResource(Res.string.room_game_players, count), color = ParlorTheme.colors.textPrimary,
                style = ParlorTheme.typography.headingMedium, modifier = Modifier.semantics { heading() })
            Text(info.hostDisplayName, color = ParlorTheme.colors.textSecondary)
            connected.forEach { Text(it.displayName, color = ParlorTheme.colors.textPrimary) }
            if (connected.isEmpty()) Text(stringResource(Res.string.room_game_wait), color = ParlorTheme.colors.textSecondary)
            pending.forEach { request ->
                ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
                    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                        Text(stringResource(Res.string.room_game_request, request.displayName.asBidiArgument()),
                            color = ParlorTheme.colors.textPrimary)
                        val act: (Boolean) -> Unit = { approve ->
                            if (enabled && request.playerId !in admitting) {
                                admitting = admitting + request.playerId
                                scope.launch {
                                    try {
                                        val result = if (approve) room.approveAdmission(request.playerId)
                                            else room.rejectAdmission(request.playerId)
                                        if (result is Result.Failure) toast.show(failed, ParlorToastSeverity.Warning)
                                    } finally { admitting = admitting - request.playerId }
                                }
                            }
                        }
                        RoomButton(stringResource(Res.string.room_game_approve, request.displayName.asBidiArgument()),
                            { act(true) }, enabled && request.playerId !in admitting)
                        RoomButton(stringResource(Res.string.room_game_decline, request.displayName.asBidiArgument()),
                            { act(false) }, enabled && request.playerId !in admitting, true)
                    }
                }
            }
            spec.Settings(state.caseId, count, enabled, setup::select)
            if (!canStart) Text(stringResource(Res.string.room_game_cannot_start), color = ParlorTheme.colors.textSecondary)
            state.error?.let { Text(roomGameError(it), color = ParlorTheme.colors.textSecondary) }
            RoomButton(stringResource(Res.string.room_game_start, count), {
                val currentCount = room.members.value.count(RoomMember::connected) + 1
                if (room.pendingAdmissions.value.isEmpty() && spec.acceptsSettings(setup.state.value.caseId.raw, currentCount)) onStart()
            }, canStart)
            RoomButton(stringResource(Res.string.room_game_leave), onLeave, !operationInFlight, true)
        }
    }
}

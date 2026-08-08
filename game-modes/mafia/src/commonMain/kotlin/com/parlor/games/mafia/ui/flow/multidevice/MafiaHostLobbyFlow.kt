package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.StickyActionBar
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_host_cancel
import com.parlor.games.mafia.resources.md_host_cancel_description
import com.parlor.games.mafia.resources.md_host_approve
import com.parlor.games.mafia.resources.md_host_approve_description
import com.parlor.games.mafia.resources.md_host_decline
import com.parlor.games.mafia.resources.md_host_decline_description
import com.parlor.games.mafia.resources.md_host_error_detail
import com.parlor.games.mafia.resources.md_host_error_title
import com.parlor.games.mafia.resources.md_host_eyebrow
import com.parlor.games.mafia.resources.md_host_hosting_as_format
import com.parlor.games.mafia.resources.md_host_opening_room
import com.parlor.games.mafia.resources.md_host_join_request_format
import com.parlor.games.mafia.resources.md_host_pending_eyebrow
import com.parlor.games.mafia.resources.md_host_player_bullet_format
import com.parlor.games.mafia.resources.md_host_players_in_room
import com.parlor.games.mafia.resources.md_host_room_code
import com.parlor.games.mafia.resources.md_host_start_description
import com.parlor.games.mafia.resources.md_host_start_need_more
import com.parlor.games.mafia.resources.md_host_start_pending
import com.parlor.games.mafia.resources.md_host_start_too_many
import com.parlor.games.mafia.resources.md_host_start_with_format
import com.parlor.games.mafia.resources.md_host_waiting_for_players
import com.parlor.games.mafia.resources.setup_back
import com.parlor.games.mafia.resources.setup_back_description
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Mafia-side host lobby. Mirrors composeApp's shell `HostSessionFlow` but
 * is Mafia-specific: no case loading (Mafia has no external content), and
 * once started it dispatches to [MafiaMultiDeviceHostFlow] instead of the
 * Whodunit host flow. Lives in the Mafia module so composeApp's shell
 * doesn't need to know anything Mafia-specific.
 */
@Composable
fun MafiaHostLobbyFlow(
    transport: RoomTransport,
    hostName: String,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    var hostError by remember { mutableStateOf<NetError?>(null) }
    var started by remember { mutableStateOf(false) }
    val seed = remember { RandomSource.system().nextLong() }

    LaunchedEffect(transport) {
        when (
            val result = transport.host(
                HostConfig(
                    roomDisplayName = hostName,
                    maxRemotePlayers = MafiaSettings.MAX_PLAYERS - 1,
                ),
            )
        ) {
            is Result.Success -> room = result.data
            is Result.Failure -> hostError = result.error
        }
    }

    val current = room
    LaunchedEffect(current) {
        if (current != null) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    current.leave()
                }
            }
        }
    }

    when {
        hostError != null -> MafiaLobbyErrorState(
            title = stringResource(Res.string.md_host_error_title),
            detail = stringResource(Res.string.md_host_error_detail),
            onBack = onBackToHome,
            modifier = modifier,
        )
        current == null -> MafiaLobbyLoadingState(
            label = stringResource(Res.string.md_host_opening_room),
            modifier = modifier,
        )
        !started -> MafiaHostLobbyContent(
            room = current,
            hostName = hostName,
            onStart = {
                scope.launch {
                    current.closeAdmissions()
                    started = true
                }
            },
            onLeave = {
                scope.launch {
                    current.leave()
                    onBackToHome()
                }
            },
            modifier = modifier,
        )
        else -> {
            val members by current.members.collectAsState()
            val players = remember(members, hostName, current) {
                val hostPlayer = Player(
                    id = current.info.value.hostPlayerId,
                    displayName = hostName,
                    seat = 0,
                )
                val peers = members.mapIndexed { index, member ->
                    Player(member.playerId, member.displayName, seat = index + 1)
                }
                listOf(hostPlayer) + peers
            }
            MafiaMultiDeviceHostFlow(
                players = players,
                seed = seed,
                room = current,
                onBackToHome = onBackToHome,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MafiaHostLobbyContent(
    room: LocalRoom,
    hostName: String,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info by room.info.collectAsState()
    val members by room.members.collectAsState()
    val pendingAdmissions by room.pendingAdmissions.collectAsState()
    val scope = rememberCoroutineScope()
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ParlorTheme.spacing.l,
                        end = ParlorTheme.spacing.l,
                        top = ParlorTheme.spacing.l,
                        bottom = ParlorTheme.spacing.xxxl + ParlorTheme.spacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            ) {
                EyebrowLabel(text = stringResource(Res.string.md_host_eyebrow))

                ParlorCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = ParlorTheme.elevation.dramatic,
                    cornerRadius = ParlorTheme.radii.elevated,
                    contentPadding = ParlorTheme.spacing.l,
                    hero = true,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                        EyebrowLabel(text = stringResource(Res.string.md_host_room_code), accent = false)
                        Text(
                            text = info.code,
                            style = ParlorTheme.typography.displayHero,
                            color = ParlorTheme.colors.accentEmber,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(Res.string.md_host_hosting_as_format, hostName),
                            style = ParlorTheme.typography.bodyMedium,
                            color = ParlorTheme.colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                EyebrowLabel(text = stringResource(Res.string.md_host_players_in_room), accent = false)
                if (members.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.md_host_waiting_for_players),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textTertiary,
                    )
                } else {
                    members.forEach { member ->
                        Text(
                            text = stringResource(Res.string.md_host_player_bullet_format, member.displayName),
                            style = ParlorTheme.typography.bodyLarge,
                            color = ParlorTheme.colors.textPrimary,
                        )
                    }
                }

                if (pendingAdmissions.isNotEmpty()) {
                    EyebrowLabel(
                        text = stringResource(Res.string.md_host_pending_eyebrow),
                        accent = false,
                    )
                    pendingAdmissions.forEach { admission ->
                        ParlorCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = ParlorTheme.spacing.m,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.md_host_join_request_format,
                                        admission.displayName,
                                    ),
                                    style = ParlorTheme.typography.bodyLarge,
                                    color = ParlorTheme.colors.textPrimary,
                                )
                                ParlorButton(
                                    label = stringResource(Res.string.md_host_approve),
                                    contentDescription = stringResource(
                                        Res.string.md_host_approve_description,
                                        admission.displayName,
                                    ),
                                    onClick = {
                                        scope.launch {
                                            room.approveAdmission(admission.playerId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ParlorButton(
                                    label = stringResource(Res.string.md_host_decline),
                                    contentDescription = stringResource(
                                        Res.string.md_host_decline_description,
                                        admission.displayName,
                                    ),
                                    onClick = {
                                        scope.launch {
                                            room.rejectAdmission(admission.playerId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = ParlorButtonVariant.Secondary,
                                )
                            }
                        }
                    }
                }
            }

            val playerCount = members.size + 1
            val canStart =
                playerCount in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS &&
                    pendingAdmissions.isEmpty()
            val startLabel = when {
                pendingAdmissions.isNotEmpty() ->
                    stringResource(Res.string.md_host_start_pending)
                playerCount < MafiaSettings.MIN_PLAYERS ->
                    stringResource(Res.string.md_host_start_need_more)
                playerCount > MafiaSettings.MAX_PLAYERS ->
                    stringResource(
                        Res.string.md_host_start_too_many,
                        MafiaSettings.MAX_PLAYERS,
                    )
                else -> stringResource(Res.string.md_host_start_with_format, playerCount)
            }
            StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                ParlorButton(
                    label = startLabel,
                    contentDescription = stringResource(Res.string.md_host_start_description),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canStart,
                )
                ParlorButton(
                    label = stringResource(Res.string.md_host_cancel),
                    contentDescription = stringResource(Res.string.md_host_cancel_description),
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun MafiaLobbyLoadingState(label: String, modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CandleFlame(size = androidx.compose.ui.unit.Dp(72f))
            Text(
                text = label,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MafiaLobbyErrorState(
    title: String,
    detail: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.setup_back),
                contentDescription = stringResource(Res.string.setup_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

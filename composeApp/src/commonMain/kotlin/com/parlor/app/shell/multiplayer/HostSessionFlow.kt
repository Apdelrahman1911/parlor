package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.error_back
import com.parlor.app.resources.error_back_description
import com.parlor.app.resources.host_cancel
import com.parlor.app.resources.host_error_title
import com.parlor.app.resources.host_start_description
import com.parlor.app.resources.host_start_solo
import com.parlor.app.resources.host_start_with_players_format
import com.parlor.app.resources.host_cancel_description
import com.parlor.app.resources.host_members_empty
import com.parlor.app.resources.host_members_eyebrow
import com.parlor.app.resources.host_room_code_eyebrow
import com.parlor.app.resources.host_starting
import com.parlor.app.resources.host_title
import com.parlor.app.resources.host_hosting_as_format
import com.parlor.app.shell.netErrorMessage
import com.parlor.content.repository.CaseRepository
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
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
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.ui.flow.WhodunitMultiplayerHostFlow
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * End-to-end host flow: open a room, show the lobby, and once the host taps
 * **Start**, hand off to [WhodunitMultiplayerHostFlow] which announces the
 * session and renders the game. Owns the [LocalRoom] for the lifetime of
 * the session and tears it down on [onBackToLibrary].
 */
@Composable
fun HostSessionFlow(
    transport: RoomTransport,
    caseId: String,
    modeId: ModeId,
    hostName: String,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))
    val scope = rememberCoroutineScope()

    var room by remember { mutableStateOf<LocalRoom?>(null) }
    // Keep the typed error so the rendering site can localise it.
    var hostError by remember { mutableStateOf<NetError?>(null) }
    var started by remember { mutableStateOf(false) }
    val seed = remember(caseId) { RandomSource.system().nextLong() }

    val caseResult by produceState<Result<ValidatedCase<WhodunitCase>, DataError>?>(
        initialValue = null,
        key1 = caseId,
    ) {
        value = repository.loadCase(CaseId(caseId), payloadValidator)
    }

    LaunchedEffect(transport) {
        when (val result = transport.host(HostConfig(roomDisplayName = hostName))) {
            is Result.Success -> room = result.data
            is Result.Failure -> hostError = result.error
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val active = room
            if (active != null) {
                scope.launch { runCatching { active.leave() } }
            }
        }
    }

    val current = room
    val case = (caseResult as? Result.Success)?.data
    when {
        hostError != null -> HostErrorState(netErrorMessage(hostError!!), onBack = onBackToLibrary, modifier = modifier)
        current == null || case == null -> HostLoadingState(modifier = modifier)
        !started -> HostLobbyContent(
            room = current,
            hostName = hostName,
            modifier = modifier,
            onStart = { started = true },
            onLeave = onBackToLibrary,
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
            WhodunitMultiplayerHostFlow(
                case = case,
                modeId = modeId,
                players = players,
                seed = seed,
                room = current,
                onBackToLibrary = onBackToLibrary,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun HostLobbyContent(
    room: LocalRoom,
    hostName: String,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info by room.info.collectAsState()
    val members by room.members.collectAsState()

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
            EyebrowLabel(text = stringResource(Res.string.host_title))

            // Hero room-code card — the screen's signature moment. The
            // indigo-tinted hero surface + dramatic elevation make the code
            // unmistakable when the host turns the phone to a friend.
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.dramatic,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.l,
                hero = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    EyebrowLabel(
                        text = stringResource(Res.string.host_room_code_eyebrow),
                        accent = false,
                    )
                    Text(
                        text = info.code,
                        style = ParlorTheme.typography.displayHero,
                        color = ParlorTheme.colors.accentEmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(Res.string.host_hosting_as_format).replace("%1\$s", hostName),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            EyebrowLabel(text = stringResource(Res.string.host_members_eyebrow), accent = false)
            if (members.isEmpty()) {
                Text(
                    text = stringResource(Res.string.host_members_empty),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                )
            } else {
                members.forEach { member ->
                    Text(
                        text = "· ${member.displayName}",
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textPrimary,
                    )
                }
            }

        }

        val startLabel = if (members.isEmpty()) {
            stringResource(Res.string.host_start_solo)
        } else {
            stringResource(Res.string.host_start_with_players_format)
                .replace("%1\$s", (members.size + 1).toString())
        }
        StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            ParlorButton(
                label = startLabel,
                contentDescription = stringResource(Res.string.host_start_description),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.host_cancel),
                contentDescription = stringResource(Res.string.host_cancel_description),
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Secondary,
            )
        }
        }
    }
}

@Composable
private fun HostLoadingState(modifier: Modifier = Modifier) {
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
                text = stringResource(Res.string.host_starting),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HostErrorState(error: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.host_error_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.error_back),
                contentDescription = stringResource(Res.string.error_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

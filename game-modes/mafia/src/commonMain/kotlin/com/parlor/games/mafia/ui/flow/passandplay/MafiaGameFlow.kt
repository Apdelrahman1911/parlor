package com.parlor.games.mafia.ui.flow.passandplay

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.party.MafiaReadinessGate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.snapshot.MAFIA_PASS_AND_PLAY_MODE
import com.parlor.games.mafia.snapshot.MAFIA_PLAY_MODE_KEY
import com.parlor.games.mafia.snapshot.MAFIA_SNAPSHOT_VERSION
import com.parlor.games.mafia.snapshot.ResumedMafiaSession
import com.parlor.games.mafia.snapshot.loadMafiaResumedSession
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.setup_back
import com.parlor.games.mafia.resources.setup_back_description
import com.parlor.games.mafia.resources.setup_eyebrow
import com.parlor.games.mafia.resources.setup_loading
import com.parlor.games.mafia.resources.setup_player_count_button_description_format
import com.parlor.games.mafia.resources.setup_player_count_button_format
import com.parlor.games.mafia.resources.setup_player_count_headline
import com.parlor.games.mafia.resources.setup_player_count_range_format
import com.parlor.games.mafia.resources.recovery_error_back
import com.parlor.games.mafia.resources.recovery_error_back_description
import com.parlor.games.mafia.resources.recovery_error_body
import com.parlor.games.mafia.resources.recovery_error_discard
import com.parlor.games.mafia.resources.recovery_error_discard_description
import com.parlor.games.mafia.resources.recovery_error_eyebrow
import com.parlor.games.mafia.resources.recovery_error_retry
import com.parlor.games.mafia.resources.recovery_error_retry_description
import com.parlor.games.mafia.resources.recovery_error_title
import com.parlor.games.mafia.resources.recovery_discard_failed
import com.parlor.games.mafia.resources.recovery_save_failed
import com.parlor.games.mafia.ui.screens.setup.MafiaPlayerEntryScreen
import org.jetbrains.compose.resources.stringResource
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.party.PartyAwareSession
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.storage.snapshot.SerializedSnapshotWriter
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.storage.snapshot.SnapshotWriteStatus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Entry composable for the Mafia pass-and-play flow.
 *
 * Drives a small pre-session state machine (player count → names) and then
 * hands off to [SessionDrivenFlow] which builds the
 * [PassAndPlaySessionController], wraps it in a [PartyAwareSession] using
 * [MafiaReadinessGate], and renders [MafiaPassAndPlayPhaseRouter] against
 * the live state. The setup screen — where the host edits [com.parlor.games.mafia.domain.settings.MafiaSettings]
 * and starts the game — runs **inside** the session at the Setup phase, so
 * the reducer is the source of truth for the chosen settings.
 *
 * No multi-device / peer entry here; Mafia M3 will add separate composables
 * for that, following the Whodunit split.
 */
@Composable
fun MafiaGameFlow(
    onBackToHome: () -> Unit,
    resumeSessionId: SessionId? = null,
    modifier: Modifier = Modifier,
) {
    val definition: MafiaDefinition = koinInject()
    val snapshotStore: SnapshotStore = koinInject()
    val recoveryScope = rememberCoroutineScope()
    val toastState = LocalParlorToastState.current
    val discardFailureText = stringResource(Res.string.recovery_discard_failed)
    var recoveryAttempt by remember(resumeSessionId) { mutableStateOf(0) }
    var discardInFlight by remember(resumeSessionId) { mutableStateOf(false) }

    val resumeResult by produceState<Result<ResumedMafiaSession, DataError>?>(
        initialValue = null,
        key1 = resumeSessionId,
        key2 = recoveryAttempt,
    ) {
        value = null
        value = if (resumeSessionId == null) {
            null
        } else {
            loadMafiaResumedSession(snapshotStore, definition, resumeSessionId)
        }
    }

    var pre by remember { mutableStateOf(PreSession()) }

    val selectedPlayerCount = pre.playerCount
    val enteredPlayers = pre.players
    when {
        resumeSessionId != null && resumeResult == null -> MafiaLoadingScreen(modifier)
        resumeSessionId != null && resumeResult is Result.Failure -> MafiaRecoveryErrorScreen(
            onBack = onBackToHome,
            onRetry = { recoveryAttempt++ },
            onDiscard = {
                if (!discardInFlight) {
                    discardInFlight = true
                    recoveryScope.launch {
                        when (snapshotStore.delete(resumeSessionId)) {
                            is Result.Success -> onBackToHome()
                            is Result.Failure -> {
                                discardInFlight = false
                                toastState.show(discardFailureText, ParlorToastSeverity.Danger)
                            }
                        }
                    }
                }
            },
            actionsEnabled = !discardInFlight,
            modifier = modifier,
        )
        resumeSessionId != null && resumeResult is Result.Success -> {
            val resumed = (resumeResult as Result.Success).data
            SessionDrivenFlow(
                players = resumed.state.players,
                onBackToHome = onBackToHome,
                restoredState = resumed.state,
                restoredSessionId = resumed.sessionId,
                modifier = modifier,
            )
        }
        selectedPlayerCount == null -> MafiaPlayerCountScreen(
            range = definition.supportedPlayerCounts,
            onCountSelected = { count -> pre = pre.copy(playerCount = count) },
            onBack = onBackToHome,
            modifier = modifier,
        )
        enteredPlayers == null -> MafiaPlayerEntryScreen(
            playerCount = selectedPlayerCount,
            onConfirm = { names ->
                pre = pre.copy(
                    players = names.mapIndexed { i, n ->
                        Player(
                            id = PlayerId("p${i + 1}"),
                            displayName = n.trim(),
                            seat = i,
                        )
                    },
                )
            },
            modifier = modifier,
        )
        else -> SessionDrivenFlow(
            players = enteredPlayers,
            onBackToHome = onBackToHome,
            modifier = modifier,
        )
    }
}

/** Pre-session config captured before the controller is built. */
private data class PreSession(
    val playerCount: Int? = null,
    val players: List<Player>? = null,
)

@Composable
private fun SessionDrivenFlow(
    players: List<Player>,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    restoredState: MafiaState? = null,
    restoredSessionId: SessionId? = null,
) {
    val clock: Clock = koinInject()
    val definition: MafiaDefinition = koinInject()
    val snapshotStore: SnapshotStore = koinInject()
    val scope = rememberCoroutineScope()

    val seed = remember(players, restoredState) {
        restoredState?.hostOnly?.randomSeed ?: RandomSource.system().nextLong()
    }

    val sessionConfig = remember(players, seed, restoredSessionId) {
        SessionConfig(
            sessionId = restoredSessionId ?: SessionId("mafia-local-${seed.toString(16)}"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = players,
            randomSeed = seed,
        )
    }

    val playMode: PlayMode = remember { PlayMode.PassAndPlay }

    val session: SessionController<MafiaState, MafiaAction, com.parlor.games.mafia.domain.event.MafiaEvent> =
        remember(sessionConfig, restoredState) {
            val ctx = DefaultReducerContext(
                clock = clock,
                random = RandomSource.seeded(seed),
            )
            val raw = PassAndPlaySessionController(
                definition = definition,
                config = sessionConfig,
                reducerContext = ctx,
                scope = scope,
                restoredState = restoredState,
            )
            PartyAwareSession(
                delegate = raw,
                playMode = playMode,
                gate = MafiaReadinessGate,
            )
        }

    val canonicalState = requireNotNull(session.canonicalState) {
        "The local Mafia flow requires an authoritative controller"
    }
    val snapshotWriter = remember(sessionConfig, definition, snapshotStore, clock) {
        val codec = definition.snapshotCodec()
        SerializedSnapshotWriter(
            store = snapshotStore,
            sessionId = sessionConfig.sessionId,
            snapshotFor = { state: MafiaState ->
                com.parlor.engine.snapshot.GameSnapshot(
                    sessionId = sessionConfig.sessionId,
                    gameId = MafiaIds.GameId,
                    engineVersion = MAFIA_SNAPSHOT_VERSION,
                    createdAt = clock.now(),
                    phaseId = state.phase.id,
                    payload = codec.encode(state),
                    metadata = mapOf(MAFIA_PLAY_MODE_KEY to MAFIA_PASS_AND_PLAY_MODE),
                )
            },
            isCompleted = { state -> state.phase == MafiaPhase.PostGame },
        )
    }
    val persistenceStatus by snapshotWriter.status.collectAsState()
    val toastState = LocalParlorToastState.current
    val saveFailureText = stringResource(Res.string.recovery_save_failed)
    LaunchedEffect(canonicalState, snapshotWriter) {
        canonicalState.collect { state -> snapshotWriter.persist(state) }
    }
    LaunchedEffect(persistenceStatus) {
        if (persistenceStatus is SnapshotWriteStatus.Failed) {
            toastState.show(saveFailureText, ParlorToastSeverity.Danger)
        }
    }
    var exitInFlight by remember(sessionConfig.sessionId) { mutableStateOf(false) }
    val exitAfterFlush: () -> Unit = {
        if (!exitInFlight) {
            exitInFlight = true
            scope.launch {
                if (snapshotWriter.persist(canonicalState.value) is Result.Success) {
                    onBackToHome()
                } else {
                    exitInFlight = false
                }
            }
        }
    }

    // PaP is the canonical host — we render off the full state so the router
    // can read everyone's private slice (which Mafia is which, coordination
    // submissions, detective results) without going through privateStateFor
    // for every cycle decision. Multi-device peers will use the public
    // projection + their own privateStateFor in M3.
    val authoritativeState = requireNotNull(session.hostState) {
        "The local Mafia flow requires a host projection"
    }
    val hostProjection by authoritativeState.collectAsState()
    val state = hostProjection.state

    Box(modifier = modifier.fillMaxSize()) {
        MafiaPassAndPlayPhaseRouter(
            playMode = playMode,
            state = state,
            session = session,
            scope = scope,
            onBackToHome = exitAfterFlush,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MafiaRecoveryErrorScreen(
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.recovery_error_eyebrow))
            Text(
                text = stringResource(Res.string.recovery_error_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.recovery_error_body),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.recovery_error_retry),
                contentDescription = stringResource(Res.string.recovery_error_retry_description),
                onClick = onRetry,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.recovery_error_discard),
                contentDescription = stringResource(Res.string.recovery_error_discard_description),
                onClick = onDiscard,
                enabled = actionsEnabled,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.recovery_error_back),
                contentDescription = stringResource(Res.string.recovery_error_back_description),
                onClick = onBack,
                enabled = actionsEnabled,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Small Mafia-internal player-count picker.
 *
 * Whodunit's `PlayerCountScreen` is in another module; Mafia is structurally
 * independent so we render a minimal local version rather than reaching
 * across module boundaries. Renders one button per supported value in the
 * Mafia definition's [com.parlor.games.mafia.MafiaDefinition.supportedPlayerCounts]
 * range and a Back affordance.
 */
@Composable
private fun MafiaPlayerCountScreen(
    range: IntRange,
    onCountSelected: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.setup_eyebrow), textAlign = TextAlign.Center)
            Text(
                text = stringResource(Res.string.setup_player_count_headline),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    Res.string.setup_player_count_range_format,
                    range.first,
                    range.last,
                ),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                ) {
                    range.forEach { n ->
                        ParlorButton(
                            label = stringResource(Res.string.setup_player_count_button_format, n),
                            contentDescription = stringResource(
                                Res.string.setup_player_count_button_description_format,
                                n,
                            ),
                            onClick = { onCountSelected(n) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            ParlorButton(
                label = stringResource(Res.string.setup_back),
                contentDescription = stringResource(Res.string.setup_back_description),
                onClick = onBack,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Spinner used during transient state transitions (Setup before settings applied, etc.). */
@Composable
internal fun MafiaLoadingScreen(modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.setup_loading),
                accent = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

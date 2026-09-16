package com.parlor.games.lastlight.ui.flow.passandplay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.random.SessionSeedSource
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.designsystem.components.SessionExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.components.SessionExitOverlay
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.local_recovery_retry
import com.parlor.games.lastlight.resources.local_save_failed
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.games.lastlight.ui.flow.common.LastLightSessionTable
import com.parlor.networking.room.RoomInputPolicy
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.storage.snapshot.SnapshotWriteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class LocalMatch(val config: SessionConfig, val restoredState: LastLightState? = null)

/** Local setup, authenticated resume, private handoff and transactional exit. */
@Composable
fun LastLightGameFlow(
    onBackToHome: () -> Unit,
    resumeSessionId: SessionId? = null,
    backRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val definition: LastLightDefinition = koinInject()
    val store: SnapshotStore = koinInject()
    val seedSource: SessionSeedSource = koinInject()
    val scope = rememberCoroutineScope()
    val visibility = LocalLastLightProcessVisibility.current
    var names by remember(resumeSessionId) { mutableStateOf(listOf("", "")) }
    var active by remember(resumeSessionId) { mutableStateOf<LocalMatch?>(null) }
    var resumeTarget by remember(resumeSessionId) { mutableStateOf(resumeSessionId) }
    var recoveryAttempt by remember(resumeSessionId) { mutableStateOf(0) }
    var recoveryFailed by remember(resumeSessionId) { mutableStateOf(false) }
    var discardInFlight by remember(resumeSessionId) { mutableStateOf(false) }
    var discardFailed by remember(resumeSessionId) { mutableStateOf(false) }
    var activeBackRequest by remember(resumeSessionId) { mutableLongStateOf(0L) }

    LaunchedEffect(resumeTarget, recoveryAttempt) {
        val target = resumeTarget ?: return@LaunchedEffect
        recoveryFailed = false
        when (val result = LastLightSnapshotRecovery.load(store, definition, target)) {
            is Result.Success -> {
                val resumed = result.data
                names = resumed.state.players.map { it.displayName }
                active = LocalMatch(
                    config = createLocalLastLightSessionConfig(
                        players = resumed.state.players,
                        randomSeed = resumed.state.hostOnly.randomSeed,
                        restoredSessionId = resumed.sessionId,
                    ),
                    restoredState = resumed.state,
                )
                resumeTarget = null
            }
            is Result.Failure -> recoveryFailed = true
        }
    }
    LaunchedEffect(backRequestId) {
        if (backRequestId > 0L && !discardInFlight) {
            if (active != null) activeBackRequest = backRequestId else onBackToHome()
        }
    }

    val match = active
    val recoveryTarget = resumeTarget
    when {
        match != null -> LocalSessionFlow(
            match = match,
            onBackToHome = onBackToHome,
            onReturnToSetup = { active = null; activeBackRequest = 0L },
            backRequestId = activeBackRequest,
            modifier = modifier,
        )
        recoveryTarget != null && recoveryFailed -> LastLightRecoveryScreen(
            onRetry = {
                if (recoveryFailed && !discardInFlight && visibility.isForeground) {
                    recoveryFailed = false
                    recoveryAttempt++
                    discardFailed = false
                }
            },
            onDiscard = {
                if (recoveryFailed && !discardInFlight && visibility.isForeground) {
                    discardInFlight = true
                    scope.launch {
                        try {
                            when (deleteRecoveredMatch(store, recoveryTarget)) {
                                is Result.Success -> onBackToHome()
                                is Result.Failure -> discardFailed = true
                            }
                        } finally {
                            discardInFlight = false
                        }
                    }
                }
            },
            onBack = { if (!discardInFlight) onBackToHome() },
            actionsEnabled = visibility.isForeground && !discardInFlight,
            discardFailed = discardFailed,
            modifier = modifier,
        )
        recoveryTarget != null -> LastLightLoadingScreen(modifier)
        else -> LastLightLocalSetupScreen(
            names = names,
            onNamesChanged = { names = it },
            onStart = {
                val normalized = names.map(RoomInputPolicy::normalizeDisplayName)
                if (active == null && visibility.isForeground && normalized.size in 2..6 &&
                    RoomInputPolicy.areValidDistinctDisplayNames(normalized)
                ) {
                    activeBackRequest = 0L
                    active = LocalMatch(
                        createLocalLastLightSessionConfig(
                            players = lastLightLocalPlayers(normalized),
                            randomSeed = seedSource.nextSeed(),
                        ),
                    )
                }
            },
            onBack = onBackToHome,
            actionsEnabled = visibility.isForeground,
            modifier = modifier,
        )
    }
}

@Composable
private fun LocalSessionFlow(
    match: LocalMatch,
    onBackToHome: () -> Unit,
    onReturnToSetup: () -> Unit,
    backRequestId: Long,
    modifier: Modifier,
) {
    val definition: LastLightDefinition = koinInject()
    val store: SnapshotStore = koinInject()
    val clock: Clock = koinInject()
    val scope = rememberCoroutineScope()
    val visibility = LocalLastLightProcessVisibility.current
    val visibilityReader = LocalLastLightVisibilityReader.current
    val runtime = remember(match, definition, store, clock, visibilityReader) {
        val controller = PassAndPlaySessionController(
            definition = definition,
            config = match.config,
            reducerContext = DefaultReducerContext(clock, RandomSource.seeded(match.config.randomSeed)),
            scope = scope,
            restoredState = match.restoredState,
        )
        LastLightLocalSession(match.config, controller, definition, store, clock, scope, visibilityReader = visibilityReader)
    }
    val presentation by runtime.presentation.collectAsState()
    val persistence by runtime.persistenceStatus.collectAsState()
    val tableState = rememberSaveableStateHolder()
    DisposableEffect(runtime, tableState) {
        onDispose { tableState.removeState(match.config.sessionId.raw) }
    }
    SideEffect { runtime.setVisibility(visibility) }
    LaunchedEffect(runtime) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { runtime.dispose() }
        }
    }
    LaunchedEffect(backRequestId) { if (backRequestId > 0L) runtime.requestExit() }

    Column(modifier = modifier.fillMaxSize()) {
        if (persistence is SnapshotWriteStatus.Failed || presentation.issue == LastLightLocalIssue.SaveFailed) {
            SnapshotFailureBanner(onRetry = runtime::retrySave)
        }
        if (presentation.exitConfirmationOpen) {
            SessionExitConfirmation(
                kind = SessionExitKind.Local,
                onStay = runtime::stay,
                onExit = { runtime.setVisibility(visibility); runtime.saveAndExit(onBackToHome) },
                exitInFlight = presentation.exitInFlight,
                destructive = false,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            SessionExitOverlay(
                visible = true,
                onClick = runtime::requestExit,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                tableState.SaveableStateProvider(match.config.sessionId.raw) {
                    LocalSessionTable(runtime, presentation, visibility, onReturnToSetup)
                }
            }
        }
    }
}

@Composable
private fun LocalSessionTable(
    runtime: LastLightLocalSession,
    presentation: LastLightLocalPresentation,
    visibility: LastLightProcessVisibility,
    onReturnToSetup: () -> Unit,
) {
    val handoffName = presentation.handoffPlayerName
    LastLightSessionTable(
        sessionId = runtime.config.sessionId.raw,
        view = presentation.game,
        isHost = true,
        canSendAction = presentation.canSendAction && visibility.isForeground,
        pendingAction = presentation.pendingAction,
        privateContentVisible = visibility.isForeground,
        canAdvanceRound = presentation.game.phase == GamePhase.ROUND_ENDED,
        canReturnToLobby = presentation.game.phase == GamePhase.FINISHED,
        onPlay = { runtime.setVisibility(visibility); runtime.play(it) },
        onChallenge = { runtime.setVisibility(visibility); runtime.challenge() },
        onNextRound = { runtime.setVisibility(visibility); runtime.nextRound() },
        onReturnToLobby = { runtime.setVisibility(visibility); runtime.returnToSetup(onReturnToSetup) },
        privacyEpoch = visibility.concealmentEpoch,
        celebrateAnyWinner = true,
        cover = if (handoffName == null) null else {
            {
                LastLightHandoffScreen(
                    playerName = handoffName,
                    enabled = visibility.isForeground && presentation.pendingAction == null,
                    rejected = presentation.issue == LastLightLocalIssue.ActionRejected,
                    onTakeDevice = { runtime.setVisibility(visibility); runtime.takeDevice() },
                )
            }
        },
    )
}

@Composable
private fun SnapshotFailureBanner(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
            .padding(ParlorTheme.spacing.m),
    ) {
        Text(
            stringResource(Res.string.local_save_failed),
            style = ParlorTheme.typography.bodyMedium,
            color = ParlorTheme.colors.textPrimary,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.local_recovery_retry)) }
    }
}

private suspend fun deleteRecoveredMatch(store: SnapshotStore, sessionId: SessionId): EmptyResult<DataError> = try {
    store.delete(sessionId)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
    // Sanitize unexpected platform I/O failures at the same boundary as the snapshot writer.
    Result.Failure(DataError.IoError("snapshot_io"))
}

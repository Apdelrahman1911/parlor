package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.parlor.content.repository.CaseRepository
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.ContinueWithoutDialog
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.HostDisconnectedOverlay
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.WhodunitPlayModePolicy
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitContentIdentity
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.modes.ClassicVoteMode
import com.parlor.games.whodunit.domain.modes.EliminationMode
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.ui.components.HideScreen
import com.parlor.games.whodunit.ui.screens.peer.PeerWaitingForHostScreen
import com.parlor.games.whodunit.ui.screens.postgame.PostGameScreen
import com.parlor.games.whodunit.ui.screens.reveal.CharacterRevealGateScreen
import com.parlor.games.whodunit.ui.screens.reveal.CharacterRevealHandoffScreen
import com.parlor.games.whodunit.ui.screens.reveal.DossierRevealScreen
import com.parlor.games.whodunit.ui.screens.reveal.HideAndPassScreen
import com.parlor.games.whodunit.ui.screens.reveal.RevealStageScreen
import com.parlor.games.whodunit.ui.screens.round.ClueRevealScreen
import com.parlor.games.whodunit.ui.screens.round.DiscussionScreen
import com.parlor.games.whodunit.ui.screens.round.RoundTitleCardScreen
import com.parlor.games.whodunit.ui.screens.safety.PauseOverlay
import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernAffordance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernDialog
import com.parlor.games.whodunit.ui.timer.runDiscussionTickerLoop
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.pause_open_description
import com.parlor.games.whodunit.resources.host_continue_without_description_format
import com.parlor.games.whodunit.resources.host_continue_without_dialog_body_format
import com.parlor.games.whodunit.resources.host_continue_without_dialog_cancel
import com.parlor.games.whodunit.resources.host_continue_without_dialog_confirm_description_format
import com.parlor.games.whodunit.resources.host_continue_without_dialog_confirm_format
import com.parlor.games.whodunit.resources.host_continue_without_dialog_title_format
import com.parlor.games.whodunit.resources.host_continue_without_format
import com.parlor.games.whodunit.resources.host_leave_session
import com.parlor.games.whodunit.resources.host_leave_session_description
import com.parlor.games.whodunit.resources.host_peer_away_body_format
import com.parlor.games.whodunit.resources.host_peer_away_title
import com.parlor.games.whodunit.resources.host_start_cancel
import com.parlor.games.whodunit.resources.host_start_cancel_description
import com.parlor.games.whodunit.resources.host_start_failed_body
import com.parlor.games.whodunit.resources.host_start_failed_timeout
import com.parlor.games.whodunit.resources.host_start_failed_title
import com.parlor.games.whodunit.resources.host_start_retry
import com.parlor.games.whodunit.resources.host_start_retry_description
import com.parlor.games.whodunit.resources.host_starting
import com.parlor.games.whodunit.resources.peer_briefing_body
import com.parlor.games.whodunit.resources.peer_briefing_title
import com.parlor.games.whodunit.resources.peer_intro_body
import com.parlor.games.whodunit.resources.peer_intro_title
import com.parlor.games.whodunit.resources.peer_postgame_body
import com.parlor.games.whodunit.resources.peer_postgame_title
import com.parlor.games.whodunit.resources.peer_leave_room
import com.parlor.games.whodunit.resources.peer_leave_room_description
import com.parlor.games.whodunit.resources.peer_reveal_ack_body
import com.parlor.games.whodunit.resources.peer_reveal_ack_title
import com.parlor.games.whodunit.resources.peer_round_body
import com.parlor.games.whodunit.resources.peer_round_title
import com.parlor.games.whodunit.resources.peer_waiting_eyebrow
import com.parlor.games.whodunit.resources.peer_waiting_for_host
import com.parlor.games.whodunit.resources.whodunit_error_back
import com.parlor.games.whodunit.resources.whodunit_error_back_description
import com.parlor.games.whodunit.resources.whodunit_error_eyebrow
import com.parlor.games.whodunit.resources.whodunit_error_title
import com.parlor.games.whodunit.resources.whodunit_data_error_corrupted
import com.parlor.games.whodunit.resources.whodunit_data_error_disk_full
import com.parlor.games.whodunit.resources.whodunit_data_error_io
import com.parlor.games.whodunit.resources.whodunit_data_error_not_found
import com.parlor.games.whodunit.resources.whodunit_data_error_permission_denied
import com.parlor.games.whodunit.resources.whodunit_data_error_unknown
import com.parlor.games.whodunit.resources.whodunit_loading_eyebrow
import com.parlor.games.whodunit.resources.whodunit_recovery_discard
import com.parlor.games.whodunit.resources.whodunit_recovery_discard_description
import com.parlor.games.whodunit.resources.whodunit_recovery_discard_failed
import com.parlor.games.whodunit.resources.whodunit_recovery_retry
import com.parlor.games.whodunit.resources.whodunit_recovery_retry_description
import com.parlor.games.whodunit.resources.whodunit_save_failed
import com.parlor.games.whodunit.resources.whodunit_unsupported_mode_body
import com.parlor.games.whodunit.resources.whodunit_unsupported_mode_eyebrow
import com.parlor.games.whodunit.resources.whodunit_unsupported_mode_title
import com.parlor.games.whodunit.resources.whodunit_vote_counting
import com.parlor.games.whodunit.resources.peer_paused_body
import com.parlor.games.whodunit.resources.peer_paused_eyebrow
import com.parlor.games.whodunit.resources.peer_command_duplicate
import com.parlor.games.whodunit.resources.peer_command_invalid
import com.parlor.games.whodunit.resources.peer_command_session_error
import com.parlor.games.whodunit.resources.peer_command_stale
import com.parlor.games.whodunit.resources.peer_initial_snapshot_failed
import com.parlor.games.whodunit.resources.peer_initial_snapshot_loading
import org.jetbrains.compose.resources.stringResource
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.games.whodunit.ui.screens.setup.PlayerCountDisplayStrategy
import com.parlor.games.whodunit.ui.screens.setup.PlayerCountScreen
import com.parlor.games.whodunit.ui.screens.setup.PlayerEntryScreen
import com.parlor.games.whodunit.ui.screens.setup.PublicIntroScreen
import com.parlor.games.whodunit.ui.screens.setup.RulesBriefingScreen
import com.parlor.games.whodunit.ui.screens.vote.TiedRevoteScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteBallotScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteHandoffScreen
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.games.whodunit.domain.party.WhodunitReadinessGate
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.ViewerContext
import com.parlor.session.party.PartyAwareSession
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.session.multidevice.PeerCommandProgress
import com.parlor.session.multidevice.PeerCommandDelivery
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.beginExit
import com.parlor.session.multidevice.toHostStartGateState
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.storage.snapshot.SerializedSnapshotWriter
import com.parlor.storage.snapshot.SnapshotWriteStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * The reducer-driven Whodunit game flow.
 *
 * Loads *The Last Dinner* through [CaseRepository], constructs a real
 * [PassAndPlaySessionController] around [WhodunitDefinition], and routes
 * screens by observing `session.publicState`. UI events submit real
 * [WhodunitAction]s; the reducer drives phase transitions.
 *
 * Phase 6.2: when [resumeSessionId] is non-null, the flow looks up the
 * persisted snapshot, decodes its payload to a [WhodunitState], skips the
 * pre-session setup screens, and boots the controller at the saved phase. A
 * missing or corrupt snapshot drops back to the library — never to a half-
 * configured fresh game.
 */
@Composable
fun WhodunitGameFlow(
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    resumeSessionId: SessionId? = null,
    caseId: String = "last-dinner",
    // The local entry from Home → Browse Cases is functionally pass-and-play
    // (single phone, multiple players around it). Multi-device entry points
    // are separate composables — see [WhodunitMultiplayerHostFlow] /
    // [WhodunitMultiplayerPeerFlow].
    playMode: PlayMode = PlayMode.PassAndPlay,
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))
    val snapshotStore: SnapshotStore = koinInject()
    val definition: WhodunitDefinition = koinInject()
    val recoveryScope = rememberCoroutineScope()
    val toastState = LocalParlorToastState.current
    val discardFailureText = stringResource(Res.string.whodunit_recovery_discard_failed)
    var recoveryAttempt by remember(resumeSessionId) { mutableStateOf(0) }
    var discardInFlight by remember(resumeSessionId) { mutableStateOf(false) }

    if (resumeSessionId == null && !WhodunitPlayModePolicy.supportsLocalEntry(playMode)) {
        UnsupportedLocalPlayModeScreen(onBackToLibrary, modifier)
        return
    }

    val resumeResult by produceState<Result<ResumedSession, DataError>?>(
        initialValue = null,
        key1 = resumeSessionId,
        key2 = recoveryAttempt,
    ) {
        value = null
        value = if (resumeSessionId == null) null
        else loadResumedSession(snapshotStore, definition, resumeSessionId)
    }

    val caseResult by produceState<Result<ValidatedCase<WhodunitCase>, DataError>?>(
        initialValue = null,
        key1 = resumeSessionId,
        key2 = resumeResult,
        key3 = caseId,
    ) {
        val targetCaseId = when (val r = resumeResult) {
            is Result.Success -> r.data.state.public.caseId.raw
            else -> caseId
        }
        // For a fresh launch, kick off the load right away. For resume, only
        // load once the snapshot has decoded successfully (so a corrupt resume
        // bails out fast instead of loading content unnecessarily).
        if (resumeSessionId == null || resumeResult is Result.Success) {
            val loadedCase = repository.loadCase(CaseId(targetCaseId), payloadValidator)
            value = if (loadedCase is Result.Success && resumeResult is Result.Success) {
                withContext(Dispatchers.Default) {
                    when (
                        validateResumedSessionForCase(
                            resumed = (resumeResult as Result.Success).data,
                            case = loadedCase.data,
                        )
                    ) {
                        is Result.Success -> loadedCase
                        is Result.Failure -> Result.Failure(DataError.CorruptedData)
                    }
                }
            } else {
                loadedCase
            }
        }
    }

    val discardResume: () -> Unit = {
        val id = resumeSessionId
        if (id != null && !discardInFlight) {
            discardInFlight = true
            recoveryScope.launch {
                when (snapshotStore.delete(id)) {
                    is Result.Success -> onBackToLibrary()
                    is Result.Failure -> {
                        discardInFlight = false
                        toastState.show(discardFailureText, ParlorToastSeverity.Danger)
                    }
                }
            }
        }
    }

    when {
        resumeSessionId != null && resumeResult is Result.Failure -> RecoveryErrorScreen(
            error = (resumeResult as Result.Failure).error,
            onBack = onBackToLibrary,
            onRetry = { recoveryAttempt++ },
            onDiscard = discardResume,
            actionsEnabled = !discardInFlight,
            modifier = modifier,
        )
        caseResult == null -> LoadingScreen(modifier)
        caseResult is Result.Failure -> if (resumeSessionId != null) {
            RecoveryErrorScreen(
                error = (caseResult as Result.Failure).error,
                onBack = onBackToLibrary,
                onRetry = { recoveryAttempt++ },
                onDiscard = discardResume,
                actionsEnabled = !discardInFlight,
                modifier = modifier,
            )
        } else {
            ErrorScreen(
                error = (caseResult as Result.Failure).error,
                onBack = onBackToLibrary,
                modifier = modifier,
            )
        }
        else -> {
            val case = (caseResult as Result.Success).data
            val resumed = (resumeResult as? Result.Success)?.data
            if (resumed != null) {
                // The persisted play mode wins over the incoming prop. The
                // only shipping local mode is PassAndPlay. Snapshots that
                // pre-date this metadata field fall back to the entry prop.
                val resumedPlayMode = resumed.playMode ?: playMode
                if (WhodunitPlayModePolicy.supportsLocalEntry(resumedPlayMode)) {
                    SessionDrivenFlow(
                        case = case,
                        modeId = resumed.state.public.modeId,
                        players = resumed.state.players,
                        playMode = resumedPlayMode,
                        onBackToLibrary = onBackToLibrary,
                        restoredState = resumed.state,
                        restoredSessionId = resumed.sessionId,
                        modifier = modifier,
                    )
                } else {
                    UnsupportedLocalPlayModeScreen(onBackToLibrary, modifier)
                }
            } else {
                ConfiguredFlow(
                    case = case,
                    playMode = playMode,
                    onBackToLibrary = onBackToLibrary,
                    modifier = modifier,
                )
            }
        }
    }
}

/** Result of decoding a persisted snapshot: ready to feed into `SessionDrivenFlow`. */
internal data class ResumedSession(
    val sessionId: SessionId,
    val state: WhodunitState,
    /** Exact case identity for snapshots written by content-bound builds. */
    val contentIdentity: WhodunitContentIdentity?,
    /**
     * Play mode read back from `GameSnapshot.metadata[PLAY_MODE_KEY]`.
     * PassAndPlay is the only supported value. Retired Solo snapshots are
     * deleted during loading; MultiDevice sessions are not stored here.
     * `null` means the snapshot pre-dates the metadata field.
     */
    val playMode: PlayMode?,
)

private const val PLAY_MODE_KEY = "playMode"
private const val PLAY_MODE_SOLO = "Solo"
private const val PLAY_MODE_PASS_AND_PLAY = "PassAndPlay"
private const val CASE_VERSION_KEY = "caseVersion"
private const val CASE_DIGEST_KEY = "caseDigest"

private fun PlayMode.serializeForMetadata(): String? = when (this) {
    is PlayMode.Solo -> null
    is PlayMode.PassAndPlay -> PLAY_MODE_PASS_AND_PLAY
    // MultiDevice never persists — the room is the source of truth; if the
    // host dies, peers leave and resume is a fresh local game (or nothing).
    is PlayMode.MultiDevice -> null
}

internal suspend fun loadResumedSession(
    snapshotStore: SnapshotStore,
    definition: WhodunitDefinition,
    sessionId: SessionId,
): Result<ResumedSession, DataError> {
    val snapshot = when (val loaded = snapshotStore.load(sessionId)) {
        is Result.Failure -> return Result.Failure(loaded.error)
        is Result.Success -> loaded.data
    }

    return try {
        // The snapshot envelope is authoritative for routing. Never try to
        // decode another game's bytes with Whodunit's codec, and never restore
        // a future/incompatible engine schema merely because its payload
        // happens to deserialize today.
        if (snapshot.sessionId != sessionId ||
            snapshot.gameId != WhodunitIds.GameId ||
            snapshot.engineVersion.major != ENGINE_VERSION.major ||
            snapshot.engineVersion > ENGINE_VERSION
        ) {
            return Result.Failure(DataError.CorruptedData)
        }
        val persistedPlayMode = snapshot.metadata[PLAY_MODE_KEY]
        if (persistedPlayMode == PLAY_MODE_SOLO) {
            return when (val deleted = snapshotStore.delete(sessionId)) {
                is Result.Success -> Result.Failure(DataError.NotFound)
                is Result.Failure -> Result.Failure(deleted.error)
            }
        }
        if (persistedPlayMode != null && persistedPlayMode != PLAY_MODE_PASS_AND_PLAY) {
            return Result.Failure(DataError.CorruptedData)
        }
        val persistedCaseVersion = snapshot.metadata[CASE_VERSION_KEY]
        val persistedCaseDigest = snapshot.metadata[CASE_DIGEST_KEY]
        if ((persistedCaseVersion == null) != (persistedCaseDigest == null)) {
            return Result.Failure(DataError.CorruptedData)
        }
        val contentIdentity = if (persistedCaseVersion != null && persistedCaseDigest != null) {
            WhodunitContentIdentity(persistedCaseVersion, persistedCaseDigest)
        } else {
            null
        }
        val state = definition.snapshotCodec().decode(snapshot.payload)
        if (snapshot.phaseId != state.phase.id) {
            return Result.Failure(DataError.CorruptedData)
        }
        val playMode = persistedPlayMode?.let { PlayMode.PassAndPlay }
        Result.Success(ResumedSession(sessionId, state, contentIdentity, playMode))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.Failure(DataError.CorruptedData)
    }
}

/**
 * Binds a decoded local snapshot to the exact validated case that will drive
 * its reducer. New snapshots must match the persisted content identity. A
 * legacy snapshot without that metadata is accepted only when every stored
 * gameplay reference still exists and agrees with the currently loaded case.
 */
internal fun validateResumedSessionForCase(
    resumed: ResumedSession,
    case: ValidatedCase<WhodunitCase>,
): Result<Unit, DataError> = try {
    val persistedIdentity = resumed.contentIdentity
    if (persistedIdentity != null && persistedIdentity != case.envelope.contentIdentity()) {
        Result.Failure(DataError.CorruptedData)
    } else {
        WhodunitStateValidator.requireValidForCase(
            state = resumed.state,
            case = case,
        )
        Result.Success(Unit)
    }
} catch (_: Exception) {
    Result.Failure(DataError.CorruptedData)
}

// ============================================================================= Loading / Error ==

@Composable
internal fun LoadingScreen(modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CandleFlame(size = ParlorTheme.iconSize.xl)
            EyebrowLabel(text = stringResource(Res.string.whodunit_loading_eyebrow), accent = false)
        }
    }
}

@Composable
internal fun UnsupportedLocalPlayModeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = stringResource(Res.string.whodunit_unsupported_mode_eyebrow),
                accent = false,
            )
            Text(
                text = stringResource(Res.string.whodunit_unsupported_mode_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.whodunit_unsupported_mode_body),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.whodunit_error_back),
                contentDescription = stringResource(Res.string.whodunit_error_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

private fun whodunitDataErrorResource(error: DataError) = when (error) {
    is DataError.NotFound -> Res.string.whodunit_data_error_not_found
    is DataError.CorruptedData -> Res.string.whodunit_data_error_corrupted
    is DataError.IoError -> Res.string.whodunit_data_error_io
    is DataError.DiskFull -> Res.string.whodunit_data_error_disk_full
    is DataError.PermissionDenied -> Res.string.whodunit_data_error_permission_denied
    is DataError.Unknown -> Res.string.whodunit_data_error_unknown
}

@Composable
private fun ErrorScreen(error: DataError, onBack: () -> Unit, modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.whodunit_error_eyebrow), accent = false)
            Text(
                text = stringResource(Res.string.whodunit_error_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = stringResource(whodunitDataErrorResource(error)),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.whodunit_error_back),
                contentDescription = stringResource(Res.string.whodunit_error_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun RecoveryErrorScreen(
    error: DataError,
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
            EyebrowLabel(text = stringResource(Res.string.whodunit_error_eyebrow), accent = false)
            Text(
                text = stringResource(Res.string.whodunit_error_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(whodunitDataErrorResource(error)),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.whodunit_recovery_retry),
                contentDescription = stringResource(Res.string.whodunit_recovery_retry_description),
                onClick = onRetry,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.whodunit_recovery_discard),
                contentDescription = stringResource(Res.string.whodunit_recovery_discard_description),
                onClick = onDiscard,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
            ParlorButton(
                label = stringResource(Res.string.whodunit_error_back),
                contentDescription = stringResource(Res.string.whodunit_error_back_description),
                onClick = onBack,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

// ===================================================================== Pre-session config ==

/**
 * Holds the user choices captured before the session is constructed.
 * Once `modeId` and `players` are non-null, we can build the session.
 */
private data class PreSession(
    val modeId: ModeId? = null,
    val playerCount: Int? = null,
    val players: List<Player>? = null,
)

@Composable
private fun ConfiguredFlow(
    case: ValidatedCase<WhodunitCase>,
    playMode: PlayMode,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pre by remember { mutableStateOf(PreSession()) }
    val selectedMode = pre.modeId
    val selectedPlayerCount = pre.playerCount
    val enteredPlayers = pre.players

    when {
        selectedMode == null -> ModeSelectionScreen(
            onModeSelected = { mode -> pre = pre.copy(modeId = mode) },
            modifier = modifier,
        )
        selectedPlayerCount == null -> {
            val moduleRange = when (selectedMode) {
                WhodunitIds.ClassicVoteModeId -> ClassicVoteMode.supportedPlayerCounts
                WhodunitIds.EliminationModeId -> EliminationMode.supportedPlayerCounts
                else -> 4..8
            }
            val caseRange = case.envelope.supportedPlayerCounts.toIntRange()
            val effective = maxOf(moduleRange.first, caseRange.first)..minOf(moduleRange.last, caseRange.last)
            PlayerCountScreen(
                moduleRange = moduleRange,
                caseSupportedRange = effective,
                displayStrategy = PlayerCountDisplayStrategy.HideUnsupported,
                onCountSelected = { count -> pre = pre.copy(playerCount = count) },
                modifier = modifier,
            )
        }
        enteredPlayers == null -> PlayerEntryScreen(
            playerCount = selectedPlayerCount,
            onConfirm = { names ->
                pre = pre.copy(
                    players = names.mapIndexed { i, n ->
                        Player(PlayerId("p${i + 1}"), n.trim().ifBlank { "Player ${i + 1}" }, seat = i)
                    },
                )
            },
            modifier = modifier,
        )
        else -> SessionDrivenFlow(
            case = case,
            modeId = selectedMode,
            players = enteredPlayers,
            playMode = playMode,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier,
        )
    }
}

// ========================================================================== In-session ==

@Composable
private fun SessionDrivenFlow(
    case: ValidatedCase<WhodunitCase>,
    modeId: ModeId,
    players: List<Player>,
    playMode: PlayMode,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    restoredState: WhodunitState? = null,
    restoredSessionId: SessionId? = null,
) {
    val clock: Clock = koinInject()
    val definition: WhodunitDefinition = koinInject()
    val snapshotStore: SnapshotStore = koinInject()
    val scope = rememberCoroutineScope()

    // Seed source: restored snapshot wins so the resumed random stream picks
    // up where it left off. Fresh sessions get a system-random seed.
    val seed = remember(case.envelope.caseId, modeId, players, restoredState) {
        restoredState?.hostOnly?.randomSeed ?: RandomSource.system().nextLong()
    }

    val sessionConfig = remember(case.envelope.caseId, modeId, players, seed, restoredSessionId) {
        SessionConfig(
            sessionId = restoredSessionId ?: SessionId("local-${seed.toString(16)}"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = players,
            randomSeed = seed,
        )
    }

    val session = remember(sessionConfig, restoredState, playMode) {
        val ctx = WhodunitReducerContext(
            clock = clock,
            random = RandomSource.seeded(seed),
            case = case,
        )
        val raw = PassAndPlaySessionController(
            definition = definition,
            config = sessionConfig,
            reducerContext = ctx,
            scope = scope,
            restoredState = restoredState,
        )
        // Local modes need auto-ack at the session boundary so per-phase UI
        // buttons stay dumb. In MultiDevice the wrapper is a transparent
        // pass-through (peers send their own acks).
        PartyAwareSession(
            delegate = raw,
            playMode = playMode,
            gate = WhodunitReadinessGate,
        )
    }

    val canonicalState = requireNotNull(session.canonicalState) {
        "The local Whodunit flow requires an authoritative controller"
    }
    val contentIdentity = remember(case.envelope) { case.envelope.contentIdentity() }
    val snapshotWriter = remember(
        sessionConfig,
        definition,
        snapshotStore,
        playMode,
        clock,
        contentIdentity,
    ) {
        val codec = definition.snapshotCodec()
        val metadata = buildMap {
            playMode.serializeForMetadata()?.let { put(PLAY_MODE_KEY, it) }
            put(CASE_VERSION_KEY, contentIdentity.version)
            put(CASE_DIGEST_KEY, contentIdentity.digest)
        }
        SerializedSnapshotWriter(
            store = snapshotStore,
            sessionId = sessionConfig.sessionId,
            snapshotFor = { state: WhodunitState ->
                com.parlor.engine.snapshot.GameSnapshot(
                    sessionId = sessionConfig.sessionId,
                    gameId = WhodunitIds.GameId,
                    engineVersion = ENGINE_VERSION,
                    createdAt = clock.now(),
                    phaseId = state.phase.id,
                    payload = codec.encode(state),
                    metadata = metadata,
                )
            },
            isCompleted = { state -> state.phase is WhodunitPhase.PostGame },
        )
    }
    val persistenceStatus by snapshotWriter.status.collectAsState()
    val toastState = LocalParlorToastState.current
    val saveFailureText = stringResource(Res.string.whodunit_save_failed)

    // Canonical state is updated in the reducer commit section. StateFlow is
    // conflated, so a slow disk write retains at most the latest state rather
    // than building an unbounded queue; the writer serializes UI flush/delete
    // requests with this collector.
    LaunchedEffect(canonicalState, snapshotWriter) {
        canonicalState.collect { state -> snapshotWriter.persist(state) }
    }
    LaunchedEffect(persistenceStatus) {
        if (persistenceStatus is SnapshotWriteStatus.Failed) {
            toastState.show(saveFailureText, ParlorToastSeverity.Danger)
        }
    }

    var exitInFlight by remember(sessionConfig.sessionId) { mutableStateOf(false) }
    val requestExit: (discard: Boolean) -> Unit = { discard ->
        if (!exitInFlight) {
            exitInFlight = true
            scope.launch {
                val result = if (discard) {
                    snapshotWriter.discard()
                } else {
                    snapshotWriter.persist(canonicalState.value)
                }
                if (result is Result.Success) {
                    onBackToLibrary()
                } else {
                    exitInFlight = false
                }
            }
        }
    }
    val exitAfterFlush: () -> Unit = { requestExit(false) }

    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state
    val payload = case.payload

    // Auto-advance from Setup → roles assigned → PublicIntro.
    LaunchedEffect(state.phase) {
        if (state.phase is WhodunitPhase.Setup) {
            session.submit(WhodunitAction.AssignRoles(seed))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PhaseRouter(
            playMode = playMode,
            phase = state.phase,
            state = state,
            case = case,
            payload = payload,
            session = session,
            scope = scope,
            onBackToLibrary = exitAfterFlush,
            modifier = Modifier.fillMaxSize(),
        )

        // Pause chrome — visible on every in-game screen except during the
        // overlay itself. Tapping it submits the Pause action; the reducer
        // flips public.paused, the snapshot writer fires on PauseEngaged.
        if (!state.public.paused &&
            state.phase is WhodunitPhase.Round &&
            state.public.voteState !is VoteState.Collecting
        ) {
            PauseAffordance(
                onPause = { scope.launch { session.submit(WhodunitAction.Pause) } },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(ParlorTheme.spacing.m),
            )
        }

        if (state.public.paused) {
            PauseOverlay(
                onResume = { scope.launch { session.submit(WhodunitAction.Resume) } },
                onResumeLater = {
                    exitAfterFlush()
                },
                onEndNow = {
                    requestExit(true)
                },
            )
        }
    }
}

@Composable
private fun PauseAffordance(
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDescription = stringResource(Res.string.pause_open_description)
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = openDescription }
            .clickable(onClick = onPause),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "II",
            style = ParlorTheme.typography.labelSmall,
            color = ParlorTheme.colors.accentEmber,
            textAlign = TextAlign.Center,
        )
    }
}


/**
 * Engine version stamped on persisted snapshots. Minor versions may be
 * migrated by the game codec; future versions and another major are rejected.
 * 1.1 adds the role-assignment generation and migrates assigned 1.0 snapshots
 * whose JSON does not yet contain that field.
 */
private val ENGINE_VERSION: com.parlor.core.versioning.SemVer =
    com.parlor.core.versioning.SemVer(1, 1, 0)


// ====================================================================== Multi-device ==

/**
 * Multi-device host entry. Builds a [PassAndPlaySessionController] as in
 * pass-and-play, wraps it in a [WhodunitHostRoomBridge] that broadcasts the
 * public projection on every state change and routes per-player private
 * slices, and renders the standard [PhaseRouter] so the host plays from the
 * same UI as solo play. `Start Game` on the lobby called
 * `bridge.announceStart(...)` before transitioning here; this composable
 * just runs the game.
 */
@Composable
fun WhodunitMultiplayerHostFlow(
    case: ValidatedCase<WhodunitCase>,
    modeId: ModeId,
    players: List<Player>,
    seed: Long,
    room: LocalRoom,
    onBackToLibrary: () -> Unit,
    onRetryStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock: Clock = koinInject()
    val definition: WhodunitDefinition = koinInject()
    val scope = rememberCoroutineScope()

    // Freeze the roster at game start. `players` is recomputed by the caller
    // from the live room membership on every change, so keying the canonical
    // session on it meant a peer dropping or returning mid-game rebuilt the
    // controller and wiped roles/phase/votes. Membership churn after start is
    // handled through the bridge (MarkPlayerDisconnected/Reconnected), never by
    // reconstructing the session. See PROBLEMS_PARLOR.md → CC-01.
    val rosterAtStart = remember { players }

    val sessionConfig = remember(case.envelope.caseId, modeId, rosterAtStart, seed) {
        SessionConfig(
            sessionId = SessionId("mp-host-${seed.toString(16)}"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = rosterAtStart,
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
            reducerContext = WhodunitReducerContext(
                clock = clock,
                random = RandomSource.seeded(seed),
                case = case,
            ),
            scope = scope,
        )
    }
    // The bridge talks to the raw controller for broadcasting host state.
    // The UI submits through the PartyAwareSession wrapper. In MultiDevice
    // mode the wrapper is a transparent pass-through — peers ack themselves
    // — but using the wrapper everywhere keeps the surrounding code uniform
    // with the local entries.
    val partySession: SessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        remember(rawSession, hostPlayMode) {
            PartyAwareSession(rawSession, hostPlayMode, WhodunitReadinessGate)
        }
    val bridge = remember(rawSession, room, rosterAtStart) {
        WhodunitHostRoomBridge(
            rawSession,
            room,
            rosterAtStart,
            scope,
            reconcileRoomTopology = true,
            requireStartHandshake = true,
        )
    }
    val session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        remember(partySession, bridge) {
            PublishingWhodunitSessionController(partySession, bridge)
        }
    var startGate by remember(bridge) {
        mutableStateOf<HostStartGateState>(HostStartGateState.Starting)
    }
    LaunchedEffect(bridge) {
        val contentIdentity = case.envelope.contentIdentity()
        val result = bridge.announceStart(
            caseId = case.envelope.caseId,
            modeId = modeId.raw,
            caseVersion = contentIdentity.version,
            caseDigest = contentIdentity.digest,
        ).toHostStartGateState()
        if (startGate != HostStartGateState.Exiting) startGate = result
    }
    LaunchedEffect(bridge) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                try {
                    bridge.terminate(SessionEndReason.HostLeft)
                } finally {
                    try {
                        room.leave()
                    } finally {
                        bridge.close()
                    }
                }
            }
        }
    }

    var terminalExitInFlight by remember(bridge) { mutableStateOf(false) }
    val exitToLibrary: (SessionEndReason) -> Unit = { reason ->
        if (!terminalExitInFlight) {
            terminalExitInFlight = true
            scope.launch {
                bridge.terminate(reason)
                room.leave()
                onBackToLibrary()
            }
        }
    }
    val retryStartAfterTerminal: () -> Unit = {
        if (!terminalExitInFlight) {
            terminalExitInFlight = true
            scope.launch {
                bridge.terminate(SessionEndReason.Cancelled)
                room.leave()
                onRetryStart()
            }
        }
    }

    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state
    val payload = case.payload
    var confirmContinueFor by remember { mutableStateOf<Player?>(null) }
    val disconnectedPlayer = state.public.disconnectedPlayers
        .asSequence()
        .mapNotNull { playerId -> state.players.firstOrNull { it.id == playerId } }
        .firstOrNull()

    LaunchedEffect(disconnectedPlayer?.id) {
        if (disconnectedPlayer == null) {
            confirmContinueFor = null
        } else if (confirmContinueFor?.id != disconnectedPlayer.id) {
            confirmContinueFor = null
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase is WhodunitPhase.Setup) {
            session.submit(WhodunitAction.AssignRoles(seed))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (startGate == HostStartGateState.Started) {
            PhaseRouter(
                playMode = hostPlayMode,
                phase = state.phase,
                state = state,
                case = case,
                payload = payload,
                session = session,
                scope = scope,
                onBackToLibrary = { exitToLibrary(SessionEndReason.HostLeft) },
                modifier = Modifier.fillMaxSize(),
            )

            if (!state.public.paused &&
                state.phase is WhodunitPhase.Round &&
                state.public.voteState !is VoteState.Collecting
            ) {
                PauseAffordance(
                    onPause = { scope.launch { session.submit(WhodunitAction.Pause) } },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(ParlorTheme.spacing.m),
                )
            }
            if (state.public.paused) {
                PauseOverlay(
                    onResume = { scope.launch { session.submit(WhodunitAction.Resume) } },
                    onResumeLater = null,
                    onEndNow = { exitToLibrary(SessionEndReason.Cancelled) },
                )
            }
        }
        if (
            startGate == HostStartGateState.Started &&
            disconnectedPlayer != null &&
            state.phase !is WhodunitPhase.PostGame
        ) {
            val playerName = disconnectedPlayer.displayName
            if (confirmContinueFor?.id == disconnectedPlayer.id) {
                ContinueWithoutDialog(
                    title = stringResource(
                        Res.string.host_continue_without_dialog_title_format,
                        playerName,
                    ),
                    body = stringResource(
                        Res.string.host_continue_without_dialog_body_format,
                        playerName,
                    ),
                    cancelLabel = stringResource(Res.string.host_continue_without_dialog_cancel),
                    confirmLabel = stringResource(
                        Res.string.host_continue_without_dialog_confirm_format,
                        playerName,
                    ),
                    confirmContentDescription = stringResource(
                        Res.string.host_continue_without_dialog_confirm_description_format,
                        playerName,
                    ),
                    onCancel = { confirmContinueFor = null },
                    onConfirm = {
                        confirmContinueFor = null
                        scope.launch {
                            bridge.continueWithout(disconnectedPlayer.id)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HostDisconnectedOverlay(
                    title = stringResource(Res.string.host_peer_away_title),
                    body = stringResource(
                        Res.string.host_peer_away_body_format,
                        playerName,
                    ),
                    continueLabel = stringResource(
                        Res.string.host_continue_without_format,
                        playerName,
                    ),
                    continueContentDescription = stringResource(
                        Res.string.host_continue_without_description_format,
                        playerName,
                    ),
                    leaveLabel = stringResource(Res.string.host_leave_session),
                    leaveContentDescription = stringResource(
                        Res.string.host_leave_session_description,
                    ),
                    onContinue = { confirmContinueFor = disconnectedPlayer },
                    onLeave = { exitToLibrary(SessionEndReason.Cancelled) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        when (val gate = startGate) {
            HostStartGateState.Started -> Unit
            HostStartGateState.Starting,
            HostStartGateState.Exiting -> ReconnectingOverlay(
                title = stringResource(Res.string.host_starting),
                leaveLabel = stringResource(Res.string.host_start_cancel),
                leaveContentDescription = stringResource(
                    Res.string.host_start_cancel_description,
                ),
                onLeave = {
                    if (startGate != HostStartGateState.Exiting) {
                        startGate = startGate.beginExit()
                        exitToLibrary(SessionEndReason.Cancelled)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            is HostStartGateState.Failed -> HostDisconnectedOverlay(
                title = stringResource(Res.string.host_start_failed_title),
                body = stringResource(
                    if (gate.error == NetError.Timeout) {
                        Res.string.host_start_failed_timeout
                    } else {
                        Res.string.host_start_failed_body
                    },
                ),
                continueLabel = stringResource(Res.string.host_start_retry),
                continueContentDescription = stringResource(
                    Res.string.host_start_retry_description,
                ),
                leaveLabel = stringResource(Res.string.host_start_cancel),
                leaveContentDescription = stringResource(
                    Res.string.host_start_cancel_description,
                ),
                onContinue = {
                    if (startGate !is HostStartGateState.Exiting) {
                        startGate = startGate.beginExit()
                        retryStartAfterTerminal()
                    }
                },
                onLeave = {
                    if (startGate !is HostStartGateState.Exiting) {
                        startGate = startGate.beginExit()
                        exitToLibrary(SessionEndReason.Cancelled)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Multi-device peer entry. Spins up a [WhodunitPeerRoomBridge] that holds
 * a `ShadowSessionController` updated by inbound host snapshots, and renders
 * the same [PhaseRouter] the host uses. The peer never reduces game state
 * locally — every action it submits is sent to the host, and every state
 * change is reflected when the host's snapshot arrives.
 */
@Composable
fun WhodunitMultiplayerPeerFlow(
    case: ValidatedCase<WhodunitCase>,
    modeId: ModeId,
    players: List<Player>,
    selfPlayerId: PlayerId,
    seed: Long,
    room: LocalRoom,
    protocol: SessionProtocol,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Wave 9H-8: PeerSessionFlow uses these to drive the
     * [com.parlor.designsystem.components.ReconnectingOverlay] and
     * [com.parlor.designsystem.components.OfflineBanner] at the screen
     * root. The peer bridge synthesises HostLost / SelfOffline via
     * its `connectionEvents` SharedFlow; we forward those transitions
     * up so the chrome composes over the whole flow.
     */
    onHostLostChanged: (Boolean) -> Unit = {},
    onSelfOfflineChanged: (Boolean) -> Unit = {},
) {
    val definition: WhodunitDefinition = koinInject()
    val scope = rememberCoroutineScope()

    val initialState = remember(case.envelope.caseId, players, modeId, seed) {
        definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("mp-peer-${seed.toString(16)}"),
                caseId = CaseId(case.envelope.caseId),
                modeId = modeId,
                players = players,
                // `seed` is the public SessionStarting nonce on this peer,
                // never the host's hidden reducer seed. The peer does not
                // reduce and starts from a structurally redacted placeholder.
                randomSeed = 0L,
            ),
        )
    }

    val bridge = remember(room, selfPlayerId, protocol) {
        WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = selfPlayerId,
            initialPublic = initialState,
            scope = scope,
            protocol = protocol,
        )
    }
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    val toastState = LocalParlorToastState.current
    val staleCommandCopy = stringResource(Res.string.peer_command_stale)
    val invalidCommandCopy = stringResource(Res.string.peer_command_invalid)
    val sessionCommandCopy = stringResource(Res.string.peer_command_session_error)
    val duplicateCommandCopy = stringResource(Res.string.peer_command_duplicate)
    LaunchedEffect(
        bridge,
        staleCommandCopy,
        invalidCommandCopy,
        sessionCommandCopy,
        duplicateCommandCopy,
    ) {
        bridge.commandProgress.collect { progress ->
            if (
                progress is PeerCommandProgress.Awaiting &&
                progress.delivery == PeerCommandDelivery.RecoveryTimedOut
            ) {
                toastState.show(sessionCommandCopy, ParlorToastSeverity.Danger)
                return@collect
            }
            val resolved = progress as? PeerCommandProgress.Resolved ?: return@collect
            val presentation = when (resolved.outcome.status) {
                CommandStatus.Applied -> null
                CommandStatus.Duplicate -> duplicateCommandCopy to ParlorToastSeverity.Info
                CommandStatus.StaleRevision,
                CommandStatus.SequenceGap -> staleCommandCopy to ParlorToastSeverity.Warning
                CommandStatus.InvalidAction,
                CommandStatus.Unauthorized,
                CommandStatus.PayloadTooLarge,
                CommandStatus.UnknownCommand -> invalidCommandCopy to ParlorToastSeverity.Danger
                CommandStatus.IncompatibleVersion,
                CommandStatus.SessionEnded,
                CommandStatus.SessionSuspended ->
                    sessionCommandCopy to ParlorToastSeverity.Danger
            }
            presentation?.let { (text, severity) -> toastState.show(text, severity) }
            bridge.acknowledgeCommandOutcome(resolved.outcome.commandId)
        }
    }

    LaunchedEffect(bridge) {
        bridge.hostDisconnected.collect { onBackToLibrary() }
    }

    // Reachability is state, not a lossy one-shot event: a collector attached
    // after an immediate disconnect must still render the correct overlay.
    val connectionState by bridge.connectionState.collectAsState()
    LaunchedEffect(connectionState.hostLost) {
        onHostLostChanged(connectionState.hostLost)
    }
    LaunchedEffect(connectionState.selfOffline) {
        onSelfOfflineChanged(connectionState.selfOffline)
    }

    val peerPlayMode = remember(selfPlayerId) {
        PlayMode.MultiDevice(selfPlayerId = selfPlayerId, isHost = false)
    }
    val session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        remember(bridge.controller, peerPlayMode) {
            // Multi-device peer: wrapper is a transparent pass-through.
            // Wired in for shape uniformity with the local entry — local
            // mode is the only one that actually issues auto-acks.
            PartyAwareSession(bridge.controller, peerPlayMode, WhodunitReadinessGate)
        }
    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state
    val payload = case.payload
    val hasAuthoritativeSnapshot by bridge.hasAuthoritativeSnapshot.collectAsState()
    val initialSnapshotError by bridge.initialSnapshotError.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (hasAuthoritativeSnapshot) {
            PhaseRouter(
                playMode = peerPlayMode,
                phase = state.phase,
                state = state,
                case = case,
                payload = payload,
                session = session,
                scope = scope,
                onBackToLibrary = onBackToLibrary,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.public.paused) {
                PeerHostPausedBanner(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            ReconnectingOverlay(
                title = stringResource(
                    if (initialSnapshotError == null) {
                        Res.string.peer_initial_snapshot_loading
                    } else {
                        Res.string.peer_initial_snapshot_failed
                    },
                ),
                leaveLabel = stringResource(Res.string.peer_leave_room),
                leaveContentDescription = stringResource(
                    Res.string.peer_leave_room_description,
                ),
                onLeave = onBackToLibrary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Publishes host-originated mutations through the same ordered coordinator
 * used by peer commands. No state-flow observer is involved, so one reducer
 * mutation always advances exactly one authoritative revision.
 */
private class PublishingWhodunitSessionController(
    private val delegate: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    private val bridge: WhodunitHostRoomBridge,
) : SessionController<WhodunitState, WhodunitAction, WhodunitEvent> by delegate {
    override suspend fun submit(
        action: WhodunitAction,
    ): Result<SubmissionReceipt, SubmitError> = bridge.submitHostAction(action)
}

/**
 * Tiny banner shown on peer devices when the host has paused. Peers can't
 * resume — only the host can. Tapping does nothing; the banner clears
 * automatically when the host resumes (state.public.paused = false).
 */
@Composable
private fun PeerHostPausedBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ParlorTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            Text(
                text = stringResource(Res.string.peer_paused_eyebrow).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )
            Text(
                text = stringResource(Res.string.peer_paused_body),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

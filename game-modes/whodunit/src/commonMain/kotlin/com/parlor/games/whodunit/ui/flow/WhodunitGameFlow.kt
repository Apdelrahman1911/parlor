package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.modes.ClassicVoteMode
import com.parlor.games.whodunit.domain.modes.EliminationMode
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
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
import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernDialog
import com.parlor.games.whodunit.ui.timer.runDiscussionTickerLoop
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.pause_open_description
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
import com.parlor.games.whodunit.resources.whodunit_loading_eyebrow
import com.parlor.games.whodunit.resources.whodunit_vote_counting
import com.parlor.games.whodunit.resources.peer_paused_body
import com.parlor.games.whodunit.resources.peer_paused_eyebrow
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
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.SendTarget
import com.parlor.session.SessionController
import com.parlor.session.ViewerContext
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
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
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))
    val snapshotStore: SnapshotStore = koinInject()
    val definition: WhodunitDefinition = koinInject()

    val resumeResult by produceState<Result<ResumedSession, DataError>?>(
        initialValue = null,
        key1 = resumeSessionId,
    ) {
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
            value = repository.loadCase(CaseId(targetCaseId), payloadValidator)
        }
    }

    when {
        resumeSessionId != null && resumeResult is Result.Failure -> ErrorScreen(
            error = (resumeResult as Result.Failure).error,
            onBack = onBackToLibrary,
            modifier = modifier,
        )
        caseResult == null -> LoadingScreen(modifier)
        caseResult is Result.Failure -> ErrorScreen(
            error = (caseResult as Result.Failure).error,
            onBack = onBackToLibrary,
            modifier = modifier,
        )
        else -> {
            val case = (caseResult as Result.Success).data
            val resumed = (resumeResult as? Result.Success)?.data
            if (resumed != null) {
                SessionDrivenFlow(
                    case = case,
                    modeId = resumed.state.public.modeId,
                    players = resumed.state.players,
                    onBackToLibrary = onBackToLibrary,
                    restoredState = resumed.state,
                    restoredSessionId = resumed.sessionId,
                    modifier = modifier,
                )
            } else {
                ConfiguredFlow(
                    case = case,
                    onBackToLibrary = onBackToLibrary,
                    modifier = modifier,
                )
            }
        }
    }
}

/** Result of decoding a persisted snapshot: ready to feed into `SessionDrivenFlow`. */
private data class ResumedSession(
    val sessionId: SessionId,
    val state: WhodunitState,
)

private suspend fun loadResumedSession(
    snapshotStore: SnapshotStore,
    definition: WhodunitDefinition,
    sessionId: SessionId,
): Result<ResumedSession, DataError> = when (val loaded = snapshotStore.load(sessionId)) {
    is Result.Failure -> Result.Failure(loaded.error)
    is Result.Success -> runCatching {
        val state = definition.snapshotCodec().decode(loaded.data.payload)
        Result.Success(ResumedSession(sessionId, state))
    }.getOrElse { Result.Failure(DataError.CorruptedData) }
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
                text = error.toString(),
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
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pre by remember { mutableStateOf(PreSession()) }

    when {
        pre.modeId == null -> ModeSelectionScreen(
            onModeSelected = { mode -> pre = pre.copy(modeId = mode) },
            modifier = modifier,
        )
        pre.playerCount == null -> {
            val moduleRange = when (pre.modeId) {
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
        pre.players == null -> PlayerEntryScreen(
            playerCount = pre.playerCount!!,
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
            modeId = pre.modeId!!,
            players = pre.players!!,
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

    val session = remember(sessionConfig, restoredState) {
        val ctx = WhodunitReducerContext(
            clock = clock,
            random = RandomSource.seeded(seed),
            case = case.payload,
        )
        PassAndPlaySessionController(
            definition = definition,
            config = sessionConfig,
            reducerContext = ctx,
            scope = scope,
            restoredState = restoredState,
        )
    }

    // Track the latest verdict via events (the reducer emits WinnerDecided on
    // transition to Reveal; the state doesn't carry the verdict directly).
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    LaunchedEffect(session) {
        @Suppress("UNCHECKED_CAST")
        val events = session.events as SharedFlow<WhodunitEvent>
        events.collect { event ->
            if (event is WhodunitEvent.WinnerDecided) verdict = event.winner
        }
    }

    // Phase 6.1 snapshot writer: persist the canonical (host) state on every
    // PhaseEntered so the game can be resumed after process death. PostGame
    // deletes the snapshot — game is over, nothing to resume.
    //
    // Phase 6.3 adds an eager write on PauseEngaged so a quick pause-then-crash
    // doesn't lose recent un-phase-transitioning actions (e.g., briefing-card
    // taps that don't change the phase).
    LaunchedEffect(session) {
        val codec = definition.snapshotCodec()
        @Suppress("UNCHECKED_CAST")
        val events = session.events as SharedFlow<WhodunitEvent>
        events.collect { event ->
            val canonicalState = session.hostState?.value?.state ?: return@collect
            when {
                event is WhodunitEvent.PhaseEntered && event.phase is WhodunitPhase.PostGame -> {
                    snapshotStore.delete(sessionConfig.sessionId)
                }
                event is WhodunitEvent.PhaseEntered || event == WhodunitEvent.PauseEngaged -> {
                    snapshotStore.save(
                        com.parlor.engine.snapshot.GameSnapshot(
                            sessionId = sessionConfig.sessionId,
                            gameId = WhodunitIds.GameId,
                            engineVersion = ENGINE_VERSION,
                            createdAt = clock.now(),
                            phaseId = canonicalState.phase.id,
                            payload = codec.encode(canonicalState),
                        ),
                    )
                }
            }
        }
    }

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
            phase = state.phase,
            state = state,
            case = case,
            payload = payload,
            session = session,
            scope = scope,
            verdict = verdict,
            onVerdictClear = { verdict = null },
            onBackToLibrary = onBackToLibrary,
            modifier = Modifier.fillMaxSize(),
        )

        // Pause chrome — visible on every in-game screen except during the
        // overlay itself. Tapping it submits the Pause action; the reducer
        // flips public.paused, the snapshot writer fires on PauseEngaged.
        if (!state.public.paused && state.phase !is WhodunitPhase.PostGame) {
            PauseAffordance(
                onPause = { scope.launch { session.submit(WhodunitAction.Pause) } },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ParlorTheme.spacing.m),
            )
        }

        if (state.public.paused) {
            PauseOverlay(
                onResume = { scope.launch { session.submit(WhodunitAction.Resume) } },
                onResumeLater = {
                    // Snapshot was already written on PauseEngaged; just leave.
                    onBackToLibrary()
                },
                onEndNow = {
                    scope.launch {
                        snapshotStore.delete(sessionConfig.sessionId)
                        onBackToLibrary()
                    }
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
 * Engine version stamped on persisted snapshots. Bumped when the engine state
 * shape changes incompatibly; the snapshot store refuses to restore a
 * snapshot whose engineVersion is older than what this app supports.
 */
private val ENGINE_VERSION: com.parlor.core.versioning.SemVer =
    com.parlor.core.versioning.SemVer(1, 0, 0)


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
    modifier: Modifier = Modifier,
) {
    val clock: Clock = koinInject()
    val definition: WhodunitDefinition = koinInject()
    val scope = rememberCoroutineScope()

    val sessionConfig = remember(case.envelope.caseId, modeId, players, seed) {
        SessionConfig(
            sessionId = SessionId("mp-host-${seed.toString(16)}"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = players,
            randomSeed = seed,
        )
    }
    val session = remember(sessionConfig) {
        PassAndPlaySessionController(
            definition = definition,
            config = sessionConfig,
            reducerContext = WhodunitReducerContext(
                clock = clock,
                random = RandomSource.seeded(seed),
                case = case.payload,
            ),
            scope = scope,
        )
    }
    val bridge = remember(session, room, players) {
        WhodunitHostRoomBridge(session, room, players, scope)
    }
    LaunchedEffect(bridge) {
        bridge.announceStart(
            caseId = case.envelope.caseId,
            modeId = modeId.raw,
            seed = seed,
        )
    }
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    var verdict by remember { mutableStateOf<Verdict?>(null) }
    LaunchedEffect(session) {
        @Suppress("UNCHECKED_CAST")
        (session.events as SharedFlow<WhodunitEvent>).collect { event ->
            if (event is WhodunitEvent.WinnerDecided) verdict = event.winner
        }
    }

    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state
    val payload = case.payload

    LaunchedEffect(state.phase) {
        if (state.phase is WhodunitPhase.Setup) {
            session.submit(WhodunitAction.AssignRoles(seed))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PhaseRouter(
            phase = state.phase,
            state = state,
            case = case,
            payload = payload,
            session = session,
            scope = scope,
            verdict = verdict,
            onVerdictClear = { verdict = null },
            onBackToLibrary = {
                scope.launch {
                    runCatching { room.send(SendTarget.Broadcast, HostMessage.EndSession) }
                    onBackToLibrary()
                }
            },
            modifier = Modifier.fillMaxSize(),
            selfPlayerId = room.selfPlayerId,
            isHost = true,
        )

        if (!state.public.paused && state.phase !is WhodunitPhase.PostGame) {
            PauseAffordance(
                onPause = { scope.launch { session.submit(WhodunitAction.Pause) } },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ParlorTheme.spacing.m),
            )
        }
        if (state.public.paused) {
            PauseOverlay(
                onResume = { scope.launch { session.submit(WhodunitAction.Resume) } },
                onResumeLater = { onBackToLibrary() },
                onEndNow = {
                    scope.launch {
                        runCatching { room.send(SendTarget.Broadcast, HostMessage.EndSession) }
                        onBackToLibrary()
                    }
                },
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
                randomSeed = seed,
            ),
        )
    }

    val bridge = remember(room, selfPlayerId) {
        WhodunitPeerRoomBridge(
            room = room,
            selfPlayerId = selfPlayerId,
            initialPublic = initialState,
            scope = scope,
        )
    }
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    LaunchedEffect(bridge) {
        bridge.hostDisconnected.collect { onBackToLibrary() }
    }

    // Wave 9H-8: forward HostLost / SelfOffline to the screen root.
    // Toast emission for PeerLeft / PeerReconnected / HostRestored
    // is layered later when the host bridge's peerEvents surface
    // reaches this flow (host-side concern); the offline banner +
    // reconnecting overlay only need the boolean state.
    LaunchedEffect(bridge) {
        bridge.connectionEvents.collect { event ->
            when (event) {
                com.parlor.networking.room.PeerEvent.HostLost ->
                    onHostLostChanged(true)
                com.parlor.networking.room.PeerEvent.HostRestored ->
                    onHostLostChanged(false)
                com.parlor.networking.room.PeerEvent.SelfOffline ->
                    onSelfOfflineChanged(true)
                com.parlor.networking.room.PeerEvent.SelfOnline ->
                    onSelfOfflineChanged(false)
                else -> Unit
            }
        }
    }

    val session = bridge.controller
    val verdict by remember { mutableStateOf<Verdict?>(null) }
    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state
    val payload = case.payload

    Box(modifier = modifier.fillMaxSize()) {
        PhaseRouter(
            phase = state.phase,
            state = state,
            case = case,
            payload = payload,
            session = session,
            scope = scope,
            verdict = verdict,
            onVerdictClear = { /* peer never replays — replay is host-driven */ },
            onBackToLibrary = onBackToLibrary,
            modifier = Modifier.fillMaxSize(),
            selfPlayerId = selfPlayerId,
            isHost = false,
        )
        if (state.public.paused) {
            PeerHostPausedBanner(modifier = Modifier.align(Alignment.Center))
        }
    }
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

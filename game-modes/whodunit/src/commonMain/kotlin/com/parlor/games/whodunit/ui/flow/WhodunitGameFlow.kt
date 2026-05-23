package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.parlor.designsystem.components.ParlorButton
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
import com.parlor.games.whodunit.ui.screens.postgame.PostGameScreen
import com.parlor.games.whodunit.ui.screens.reveal.CharacterRevealGateScreen
import com.parlor.games.whodunit.ui.screens.reveal.CharacterRevealHandoffScreen
import com.parlor.games.whodunit.ui.screens.reveal.DossierRevealScreen
import com.parlor.games.whodunit.ui.screens.reveal.HideAndPassScreen
import com.parlor.games.whodunit.ui.screens.reveal.RevealStageScreen
import com.parlor.games.whodunit.ui.screens.round.ClueRevealScreen
import com.parlor.games.whodunit.ui.screens.round.DiscussionScreen
import com.parlor.games.whodunit.ui.screens.round.RoundTitleCardScreen
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.games.whodunit.ui.screens.setup.PlayerCountDisplayStrategy
import com.parlor.games.whodunit.ui.screens.setup.PlayerCountScreen
import com.parlor.games.whodunit.ui.screens.setup.PlayerEntryScreen
import com.parlor.games.whodunit.ui.screens.setup.PublicIntroScreen
import com.parlor.games.whodunit.ui.screens.setup.RulesBriefingScreen
import com.parlor.games.whodunit.ui.screens.vote.TiedRevoteScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteBallotScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteHandoffScreen
import com.parlor.session.ViewerContext
import com.parlor.session.passandplay.PassAndPlaySessionController
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
 * Replaces the previous `WhodunitSetupDemo` placeholder. The reducer is the
 * single source of truth for game progress; the UI is a pure projection.
 */
@Composable
fun WhodunitGameFlow(
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))

    val caseResult by produceState<Result<ValidatedCase<WhodunitCase>, DataError>?>(initialValue = null) {
        value = repository.loadCase(CaseId("last-dinner"), payloadValidator)
    }

    when (val r = caseResult) {
        null -> LoadingScreen(modifier)
        is Result.Failure -> ErrorScreen(error = r.error, onBack = onBackToLibrary, modifier = modifier)
        is Result.Success -> ConfiguredFlow(
            case = r.data,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier,
        )
    }
}

// ============================================================================= Loading / Error ==

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "…",
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.accentEmber,
            )
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
            Text(
                text = "Couldn't load the case.",
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = error.toString(),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
            )
            ParlorButton(
                label = "Back",
                contentDescription = "Return to the library.",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
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
) {
    val clock: Clock = koinInject()
    val definition: WhodunitDefinition = koinInject()
    val scope = rememberCoroutineScope()

    // Generate one seed for the whole session; AssignRoles re-uses it.
    val seed = remember(case.envelope.caseId, modeId, players) {
        RandomSource.system().nextLong()
    }

    val session = remember(case.envelope.caseId, modeId, players, seed) {
        val config = SessionConfig(
            sessionId = SessionId("local-${seed.toString(16)}"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = players,
            randomSeed = seed,
        )
        val ctx = WhodunitReducerContext(
            clock = clock,
            random = RandomSource.seeded(seed),
            case = case.payload,
        )
        PassAndPlaySessionController(
            definition = definition,
            config = config,
            reducerContext = ctx,
            scope = scope,
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

    val publicProjection by session.publicState.collectAsState()
    val state = publicProjection.state
    val payload = case.payload

    // Auto-advance from Setup → roles assigned → PublicIntro.
    LaunchedEffect(state.phase) {
        if (state.phase is WhodunitPhase.Setup) {
            session.submit(WhodunitAction.AssignRoles(seed))
        }
    }

    when (val phase = state.phase) {
        is WhodunitPhase.Setup -> LoadingScreen(modifier)
        is WhodunitPhase.PublicIntro -> PublicIntroScreen(
            title = case.envelope.title,
            intro = payload.publicIntro,
            bedrockClues = payload.bedrockClues,
            onContinue = { scope.launch { session.submit(WhodunitAction.AdvanceFromIntro) } },
            modifier = modifier,
        )
        is WhodunitPhase.RulesBriefing -> RulesBriefingScreen(
            cardIndex = state.public.briefingCardIndex,
            onAdvance = { next -> scope.launch { session.submit(WhodunitAction.AdvanceBriefingCard(next)) } },
            modifier = modifier,
        )
        is WhodunitPhase.CharacterReveal -> CharacterRevealSegment(
            session = session,
            phase = phase,
            payload = payload,
            players = state.players,
            modifier = modifier,
        )
        is WhodunitPhase.Round -> RoundSegment(
            session = session,
            roundIndex = phase.index,
            state = state,
            payload = payload,
            modifier = modifier,
        )
        is WhodunitPhase.FinalVote -> VoteSegment(
            session = session,
            state = state,
            players = state.players,
            modifier = modifier,
        )
        is WhodunitPhase.TiedRevote -> {
            // After the user taps "Begin Revote", voteState transitions from
            // Tied → Collecting. The phase remains TiedRevote during the revote.
            if (state.public.voteState is VoteState.Collecting) {
                VoteSegment(session, state, state.players, modifier)
            } else {
                TiedRevoteSegment(session, state, modifier)
            }
        }
        is WhodunitPhase.Reveal -> RevealStageScreen(
            verdict = verdict ?: Verdict.PlayersWin(payload.characters.first().id),
            killerDisplayName = killerDisplayName(state, payload),
            revealNarrative = payload.revealNarratives[killerCharIdFromVerdict(verdict)
                ?: state.players.firstOrNull()?.id?.raw.orEmpty()]
                ?: "",
            onAcknowledge = { scope.launch { session.submit(WhodunitAction.AcknowledgeReveal) } },
            modifier = modifier,
        )
        is WhodunitPhase.PostGame -> PostGameScreen(
            onReplaySameCase = {
                verdict = null
                scope.launch { session.submit(WhodunitAction.BeginReplay) }
            },
            onTryOtherMode = onBackToLibrary,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier,
        )
    }
}

private fun killerCharIdFromVerdict(verdict: Verdict?): String? = when (verdict) {
    is Verdict.PlayersWin -> verdict.killerCharacterId
    is Verdict.KillerWins -> verdict.killerCharacterId
    null -> null
}

private fun killerDisplayName(state: WhodunitState, payload: WhodunitCase): String {
    // The public projection redacts hostOnly, so we don't have the killer's
    // character id directly. Fall back to the resolved-vote target.
    val accusedId = (state.public.voteState as? VoteState.Resolved)?.accusedPlayerId
    val accusedPlayer = state.players.firstOrNull { it.id == accusedId }
    return accusedPlayer?.displayName ?: payload.characters.firstOrNull()?.displayName.orEmpty()
}

// =================================================================== Character reveal ==

@Composable
private fun CharacterRevealSegment(
    session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    phase: WhodunitPhase.CharacterReveal,
    payload: WhodunitCase,
    players: List<Player>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentPlayer = players.getOrNull(phase.playerIndex) ?: return
    val nextPlayer = players.getOrNull(phase.playerIndex + 1)

    // Inform the session who is "holding" the device. The reducer doesn't
    // care, but PassAndPlaySessionController's activeViewer is part of the
    // privacy ceremony.
    LaunchedEffect(currentPlayer.id) {
        session.setActiveViewer(ViewerContext.Player(currentPlayer.id))
    }

    val privateProjection by session.privateStateFor(currentPlayer.id).collectAsState()
    val privateData = privateProjection.state.privatePerPlayer[currentPlayer.id]
    val role = privateData?.role
    val characterId = privateData?.characterId?.raw
    val character = characterId?.let { id -> payload.characters.firstOrNull { it.id == id } }

    var stage by remember(phase.playerIndex) { mutableStateOf(RevealStage.Handoff) }

    when (stage) {
        RevealStage.Handoff -> CharacterRevealHandoffScreen(
            playerName = currentPlayer.displayName,
            onContinue = { stage = RevealStage.Gate },
            modifier = modifier,
        )
        RevealStage.Gate -> CharacterRevealGateScreen(
            playerName = currentPlayer.displayName,
            onRevealed = {
                scope.launch { session.submit(WhodunitAction.StartCharacterReveal(currentPlayer.id)) }
                stage = RevealStage.Dossier
            },
            modifier = modifier,
        )
        RevealStage.Dossier -> {
            if (character == null || role == null) {
                LoadingScreen(modifier)
            } else {
                DossierRevealScreen(
                    character = character,
                    role = role,
                    onDone = { stage = RevealStage.Hide },
                    modifier = modifier,
                )
            }
        }
        RevealStage.Hide -> HideAndPassScreen(
            nextPlayerName = nextPlayer?.displayName,
            onTap = {
                scope.launch {
                    session.setActiveViewer(ViewerContext.Public)
                    session.submit(WhodunitAction.CompleteCharacterReveal(currentPlayer.id))
                }
            },
            modifier = modifier,
        )
    }
}

private enum class RevealStage { Handoff, Gate, Dossier, Hide }

// ============================================================================== Rounds ==

@Composable
private fun RoundSegment(
    session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    roundIndex: Int,
    state: WhodunitState,
    payload: WhodunitCase,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clueThisRound = state.public.revealedClues.firstOrNull { it.roundIndex == roundIndex }
    val timer = state.public.timer
    val (title, tagline) = roundTitleAndTagline(roundIndex, state.players.size)

    when {
        clueThisRound == null -> RoundTitleCardScreen(
            roundIndex = roundIndex,
            title = title,
            tagline = tagline,
            onContinue = { scope.launch { session.submit(WhodunitAction.RevealNextClue) } },
            modifier = modifier,
        )
        timer == null && state.public.voteState == VoteState.Idle -> ClueRevealScreen(
            clue = clueThisRound,
            onContinue = {
                scope.launch {
                    session.submit(WhodunitAction.StartDiscussionTimer(DISCUSSION_SECONDS))
                }
            },
            modifier = modifier,
        )
        timer != null -> DiscussionScreen(
            timer = timer,
            revealedClues = state.public.revealedClues,
            onAdvance = { scope.launch { session.submit(WhodunitAction.AdvanceFromDiscussion) } },
            modifier = modifier,
        )
        // If voteState transitioned to Collecting (Elimination mode after
        // discussion), the phase is still Round but voting is open.
        state.public.voteState is VoteState.Collecting -> VoteSegment(
            session = session,
            state = state,
            players = state.players,
            modifier = modifier,
        )
        else -> RoundTitleCardScreen(
            roundIndex = roundIndex,
            title = title,
            tagline = tagline,
            onContinue = { scope.launch { session.submit(WhodunitAction.RevealNextClue) } },
            modifier = modifier,
        )
    }
}

private fun roundTitleAndTagline(roundIndex: Int, playerCount: Int): Pair<String, String> {
    val isLast = if (playerCount <= 4) roundIndex >= 3 else roundIndex >= 4
    return when (roundIndex) {
        1 -> "Alibis" to "Where were you when it happened?"
        2 -> "Motives" to "Why would anyone want them dead?"
        3 -> if (isLast) {
            "Final Evidence" to "One last truth before the vote."
        } else {
            "Contradictions" to "Someone's story doesn't fit."
        }
        4 -> "Final Evidence" to "One last truth before the vote."
        else -> "Round $roundIndex" to ""
    }
}

private const val DISCUSSION_SECONDS = 180

// ============================================================================== Voting ==

@Composable
private fun VoteSegment(
    session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    state: WhodunitState,
    players: List<Player>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vote = state.public.voteState as? VoteState.Collecting

    if (vote == null) {
        // Not in a collecting state — could be Idle (vote not yet opened) or
        // Resolved/Tied/NoResolution (handled by phase router). Render nothing.
        return
    }

    val votedOrAbstained = vote.castSoFar.keys + vote.abstained
    val nextVoter = vote.ballotPlayerIds.firstOrNull { it !in votedOrAbstained }
    val nextVoterName = nextVoter?.let { id -> players.firstOrNull { it.id == id }?.displayName }

    // Per-voter ceremony: handoff cover → ballot. We track the local stage so
    // each voter sees a hand-off before voting.
    var ballotOpen by remember(nextVoter) { mutableStateOf(false) }

    if (nextVoter == null) {
        // Everyone has voted or abstained — close the vote.
        LaunchedEffect(Unit) {
            session.submit(WhodunitAction.CloseVote)
        }
        // While the reducer tallies, briefly show a dim hide screen.
        HideScreen(
            line = "Counting…",
            onTap = {},
            modifier = modifier,
        )
        return
    }

    if (!ballotOpen) {
        VoteHandoffScreen(
            nextVoterName = nextVoterName ?: "the next voter",
            onContinue = { ballotOpen = true },
            modifier = modifier,
        )
    } else {
        val candidates = players
            .filter { it.id !in state.public.eliminatedPlayers }
            .filter { it.id != nextVoter }   // can't vote for yourself
            .map { it.id to it.displayName }
        VoteBallotScreen(
            currentVoterName = nextVoterName ?: "Voter",
            candidates = candidates,
            onVote = { target ->
                scope.launch {
                    session.submit(WhodunitAction.CastVote(nextVoter, target))
                    ballotOpen = false
                }
            },
            onAbstain = {
                scope.launch {
                    session.submit(WhodunitAction.AbstainVote(nextVoter))
                    ballotOpen = false
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun TiedRevoteSegment(
    session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    state: WhodunitState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tied = state.public.voteState as? VoteState.Tied ?: return
    val tiedNames = tied.tiedPlayerIds.mapNotNull { id ->
        state.players.firstOrNull { it.id == id }?.displayName
    }
    TiedRevoteScreen(
        tiedNames = tiedNames,
        debateSecondsRemaining = tied.debateSecondsRemaining,
        onBeginRevote = { scope.launch { session.submit(WhodunitAction.OpenVote) } },
        modifier = modifier,
    )
}

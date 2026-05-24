package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.peer_briefing_body
import com.parlor.games.whodunit.resources.peer_briefing_title
import com.parlor.games.whodunit.resources.peer_intro_body
import com.parlor.games.whodunit.resources.peer_intro_title
import com.parlor.games.whodunit.resources.peer_leave_room
import com.parlor.games.whodunit.resources.peer_postgame_body
import com.parlor.games.whodunit.resources.peer_postgame_title
import com.parlor.games.whodunit.resources.peer_round_body
import com.parlor.games.whodunit.resources.peer_round_title
import com.parlor.games.whodunit.resources.peer_waiting_eyebrow
import com.parlor.games.whodunit.resources.peer_waiting_for_host
import com.parlor.games.whodunit.resources.whodunit_vote_counting
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
import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernAffordance
import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernDialog
import com.parlor.games.whodunit.ui.screens.setup.PublicIntroScreen
import com.parlor.games.whodunit.ui.screens.setup.RulesBriefingScreen
import com.parlor.games.whodunit.ui.screens.vote.TiedRevoteScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteBallotScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteHandoffScreen
import com.parlor.games.whodunit.ui.timer.runDiscussionTickerLoop
import com.parlor.session.SessionController
import com.parlor.session.ViewerContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Wave 9H-10: extracted from `WhodunitGameFlow.kt` to keep the router +
 * per-phase segment composables in their own file. Pure mechanical
 * split — no behavior change. The router and segments stay marked
 * `internal` so `WhodunitGameFlow.kt` (which still owns the entry
 * point + host/peer multi-device wrappers) can keep calling them.
 *
 * Pure phase-to-screen routing extracted from [SessionDrivenFlow] so the
 * pause chrome and overlay can sit cleanly on top in a single Box.
 *
 * [selfPlayerId] is the local device's player identity in multi-device
 * mode, or `null` for pass-and-play (where every phase's UI is for
 * whoever is holding the phone). Used by phases whose UI must differ
 * per peer — most importantly [WhodunitPhase.CharacterReveal], where a
 * peer must NOT render another player's dossier (it doesn't have that
 * player's private bucket and the shadow controller throws if asked
 * for it).
 */
@Composable
internal fun PhaseRouter(
    phase: WhodunitPhase,
    state: WhodunitState,
    case: ValidatedCase<WhodunitCase>,
    payload: WhodunitCase,
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    scope: kotlinx.coroutines.CoroutineScope,
    verdict: Verdict?,
    onVerdictClear: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    selfPlayerId: PlayerId? = null,
    isHost: Boolean = true,
) {
    val waitingHint = stringResource(Res.string.peer_waiting_for_host)
    val waitingEyebrow = stringResource(Res.string.peer_waiting_eyebrow)
    when (phase) {
        is WhodunitPhase.Setup -> LoadingScreen(modifier)
        is WhodunitPhase.PublicIntro -> if (isHost) {
            PublicIntroScreen(
                title = case.envelope.title,
                intro = payload.publicIntro,
                bedrockClues = payload.bedrockClues,
                onContinue = { scope.launch { session.submit(WhodunitAction.AdvanceFromIntro) } },
                modifier = modifier,
            )
        } else {
            PeerWaitingForHostScreen(
                eyebrow = waitingEyebrow,
                title = stringResource(Res.string.peer_intro_title),
                body = stringResource(Res.string.peer_intro_body),
                waitingHint = waitingHint,
                modifier = modifier,
            )
        }
        is WhodunitPhase.RulesBriefing -> if (isHost) {
            RulesBriefingScreen(
                cardIndex = state.public.briefingCardIndex,
                onAdvance = { next -> scope.launch { session.submit(WhodunitAction.AdvanceBriefingCard(next)) } },
                modifier = modifier,
            )
        } else {
            PeerWaitingForHostScreen(
                eyebrow = waitingEyebrow,
                title = stringResource(Res.string.peer_briefing_title),
                body = stringResource(Res.string.peer_briefing_body),
                waitingHint = waitingHint,
                modifier = modifier,
            )
        }
        is WhodunitPhase.CharacterReveal -> CharacterRevealSegment(
            session = session,
            phase = phase,
            payload = payload,
            players = state.players,
            selfPlayerId = selfPlayerId,
            modifier = modifier,
        )
        is WhodunitPhase.Round -> if (isHost) {
            RoundSegment(
                session = session,
                roundIndex = phase.index,
                state = state,
                payload = payload,
                modifier = modifier,
            )
        } else {
            PeerWaitingForHostScreen(
                eyebrow = waitingEyebrow,
                title = stringResource(Res.string.peer_round_title),
                body = stringResource(Res.string.peer_round_body),
                waitingHint = waitingHint,
                modifier = modifier,
            )
        }
        is WhodunitPhase.FinalVote -> VoteSegment(
            session = session,
            state = state,
            players = state.players,
            modifier = modifier,
            selfPlayerId = selfPlayerId,
            isHost = isHost,
        )
        is WhodunitPhase.TiedRevote -> {
            // After the user taps "Begin Revote", voteState transitions from
            // Tied → Collecting. The phase remains TiedRevote during the revote.
            if (state.public.voteState is VoteState.Collecting) {
                VoteSegment(session, state, state.players, modifier, selfPlayerId, isHost)
            } else {
                if (isHost) {
                    TiedRevoteSegment(session, state, modifier)
                } else {
                    PeerWaitingForHostScreen(
                        eyebrow = waitingEyebrow,
                        title = stringResource(Res.string.peer_round_title),
                        body = stringResource(Res.string.peer_round_body),
                        waitingHint = waitingHint,
                        modifier = modifier,
                    )
                }
            }
        }
        is WhodunitPhase.Reveal -> if (isHost) {
            RevealStageScreen(
                verdict = verdict ?: Verdict.PlayersWin(payload.characters.first().id),
                killerDisplayName = killerDisplayName(state, payload),
                revealNarrative = payload.revealNarratives[killerCharIdFromVerdict(verdict)
                    ?: state.players.firstOrNull()?.id?.raw.orEmpty()]
                    ?: "",
                onAcknowledge = { scope.launch { session.submit(WhodunitAction.AcknowledgeReveal) } },
                modifier = modifier,
            )
        } else {
            // Peer still sees the verdict — but acknowledge is host-driven.
            RevealStageScreen(
                verdict = verdict ?: Verdict.PlayersWin(payload.characters.first().id),
                killerDisplayName = killerDisplayName(state, payload),
                revealNarrative = payload.revealNarratives[killerCharIdFromVerdict(verdict)
                    ?: state.players.firstOrNull()?.id?.raw.orEmpty()]
                    ?: "",
                onAcknowledge = { /* peer cannot acknowledge — host closes the reveal */ },
                modifier = modifier,
            )
        }
        is WhodunitPhase.PostGame -> if (isHost) {
            PostGameScreen(
                onReplaySameCase = {
                    onVerdictClear()
                    scope.launch { session.submit(WhodunitAction.BeginReplay) }
                },
                onTryOtherMode = onBackToLibrary,
                onBackToLibrary = onBackToLibrary,
                modifier = modifier,
            )
        } else {
            PeerWaitingForHostScreen(
                eyebrow = waitingEyebrow,
                title = stringResource(Res.string.peer_postgame_title),
                body = stringResource(Res.string.peer_postgame_body),
                waitingHint = stringResource(Res.string.peer_leave_room),
                modifier = modifier,
            )
        }
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
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    phase: WhodunitPhase.CharacterReveal,
    payload: WhodunitCase,
    players: List<Player>,
    modifier: Modifier = Modifier,
    selfPlayerId: PlayerId? = null,
) {
    val scope = rememberCoroutineScope()
    val phaseCurrentPlayer = players.getOrNull(phase.playerIndex) ?: return

    // Multi-device authority: only the device whose `selfPlayerId` matches the
    // active reveal index renders the dossier ceremony. Every other peer
    // shows a "waiting" screen — and crucially, never calls
    // `session.privateStateFor(otherPlayer.id)`, which would throw on a
    // shadow controller (each peer only holds its own private slice).
    val localActor = resolveLocalRevealActor(phase, players, selfPlayerId)
    if (localActor == null) {
        com.parlor.games.whodunit.ui.screens.reveal.CharacterRevealWaitingScreen(
            activePlayerName = phaseCurrentPlayer.displayName,
            modifier = modifier,
        )
        return
    }

    val currentPlayer = localActor
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
    var privacyOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        when (stage) {
            RevealStage.Handoff -> CharacterRevealHandoffScreen(
                playerName = currentPlayer.displayName,
                onContinue = { stage = RevealStage.Gate },
                modifier = Modifier.fillMaxSize(),
            )
            RevealStage.Gate -> CharacterRevealGateScreen(
                playerName = currentPlayer.displayName,
                onRevealed = {
                    scope.launch { session.submit(WhodunitAction.StartCharacterReveal(currentPlayer.id)) }
                    stage = RevealStage.Dossier
                },
                modifier = Modifier.fillMaxSize(),
            )
            RevealStage.Dossier -> {
                if (character == null || role == null) {
                    LoadingScreen(Modifier.fillMaxSize())
                } else {
                    DossierRevealScreen(
                        character = character,
                        role = role,
                        onDone = { stage = RevealStage.Hide },
                        modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Privacy-concern affordance: visible on the *cover* screens (Handoff
        // and Hide) where no private dossier is on screen. Hidden during
        // Gate and Dossier so it can't be triggered while a private card is
        // actually visible.
        if (stage == RevealStage.Handoff || stage == RevealStage.Hide) {
            PrivacyConcernAffordance(
                onOpen = { privacyOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(ParlorTheme.spacing.l),
            )
        }

        if (privacyOpen) {
            PrivacyConcernDialog(
                onContinue = { privacyOpen = false },
                onReroll = {
                    privacyOpen = false
                    scope.launch { session.submit(WhodunitAction.RequestReroll) }
                },
            )
        }
    }
}

private enum class RevealStage { Handoff, Gate, Dossier, Hide }

// ============================================================================== Rounds ==

@Composable
private fun RoundSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    roundIndex: Int,
    state: WhodunitState,
    payload: WhodunitCase,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clueThisRound = state.public.revealedClues.firstOrNull { it.roundIndex == roundIndex }
    val timer = state.public.timer
    val (title, tagline) = roundTitleAndTagline(roundIndex, state.players.size)

    // Real-time discussion ticker. Keyed on the timer's stable id so:
    //  - a new round starting its own timer spawns a fresh loop,
    //  - composition rebuilds within the same round reuse the existing loop
    //    (no duplicate `TimerTicked` dispatches),
    //  - clearing the timer (TimerExpired / AdvanceFromDiscussion) cancels
    //    the LaunchedEffect.
    val timerId = timer?.timerId
    LaunchedEffect(timerId, session) {
        if (timerId != null) {
            runDiscussionTickerLoop(session, timerId)
        }
    }

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
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    state: WhodunitState,
    players: List<Player>,
    modifier: Modifier = Modifier,
    selfPlayerId: PlayerId? = null,
    isHost: Boolean = true,
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

    if (nextVoter == null) {
        // Everyone has voted or abstained — close the vote. Only the host
        // submits CloseVote; peers just wait for the host's tally to ripple
        // back as a phase change.
        if (isHost) {
            LaunchedEffect(Unit) {
                session.submit(WhodunitAction.CloseVote)
            }
        }
        // While the reducer tallies, briefly show a dim hide screen.
        HideScreen(
            line = stringResource(Res.string.whodunit_vote_counting),
            onTap = {},
            modifier = modifier,
        )
        return
    }

    // Multi-device per-voter gating: only the device whose self is nextVoter
    // sees the ballot. Other devices show the standard hand-off cover with
    // the next voter's name. Pass-and-play (selfPlayerId == null) keeps the
    // pre-existing tap-to-continue behaviour for everyone on one phone.
    val isLocalVoter = selfPlayerId == null || nextVoter == selfPlayerId

    var ballotOpen by remember(nextVoter) { mutableStateOf(false) }

    if (!isLocalVoter) {
        VoteHandoffScreen(
            nextVoterName = nextVoterName ?: "the next voter",
            onContinue = { /* not the local voter — host drives the flow */ },
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
            onRefuse = {
                scope.launch {
                    session.submit(WhodunitAction.RefuseToVote(nextVoter))
                    ballotOpen = false
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun TiedRevoteSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
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

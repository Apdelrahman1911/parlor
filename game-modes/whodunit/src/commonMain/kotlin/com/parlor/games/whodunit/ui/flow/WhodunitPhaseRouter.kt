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
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.rules.WhodunitRoundPolicy
import com.parlor.games.whodunit.domain.state.PartyReadiness
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
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
import com.parlor.games.whodunit.ui.screens.safety.PrivacyConcernUiPolicy
import com.parlor.games.whodunit.ui.screens.safety.privacyConcernUiPolicy
import com.parlor.games.whodunit.ui.screens.setup.PublicIntroScreen
import com.parlor.games.whodunit.ui.screens.setup.RulesBriefingScreen
import com.parlor.games.whodunit.ui.screens.vote.EliminationInnocentOutcomeScreen
import com.parlor.games.whodunit.ui.screens.vote.TiedRevoteScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteBallotScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteHandoffScreen
import com.parlor.games.whodunit.ui.timer.runDiscussionTickerLoop
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.ViewerContext
import com.parlor.session.isHost
import com.parlor.session.selfPlayerId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Host/local dispatch for the Whodunit per-phase UI.
 *
 * Peers use [PeerPhaseRouter], whose type requires a complete own-player
 * projection from one authoritative revision. Keeping these entry points
 * separate prevents a peer screen from accidentally combining independently
 * collected public and private flows.
 */
@Composable
internal fun HostPhaseRouter(
    playMode: PlayMode,
    phase: WhodunitPhase,
    state: WhodunitState,
    case: ValidatedCase<WhodunitCase>,
    payload: WhodunitCase,
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    scope: kotlinx.coroutines.CoroutineScope,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(playMode.isHost) { "A peer must render through PeerPhaseRouter" }
    if (playMode is PlayMode.Solo) {
        UnsupportedLocalPlayModeScreen(onBackToLibrary, modifier)
        return
    }
    // The verdict lives on state.public so it travels in the public slice of
    // each PlayerSnapshot and persists in the game snapshot. No side-channel
    // event listener or resume-time outcome guess is required.
    val verdict = state.public.verdict
    HostPhaseScreens(
        playMode = playMode,
        phase = phase,
        state = state,
        case = case,
        payload = payload,
        session = session,
        scope = scope,
        verdict = verdict,
        onBackToLibrary = onBackToLibrary,
        modifier = modifier,
    )
}

/**
 * Peer-only router. [projection] is the sole rendering source for this tree:
 * its public fields and own-private bucket were decoded from the same host
 * snapshot and therefore cannot cross role-assignment generations.
 */
@Composable
internal fun PeerPhaseRouter(
    playMode: PlayMode.MultiDevice,
    projection: PrivateProjection<WhodunitState>,
    payload: WhodunitCase,
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    modifier: Modifier = Modifier,
) {
    require(!playMode.isHost) { "A host must render through HostPhaseRouter" }
    require(projection.playerId == playMode.selfPlayerId) {
        "Peer projection belongs to another player"
    }
    val state = projection.state
    PeerPhaseScreens(
        playMode = playMode,
        phase = state.phase,
        state = state,
        payload = payload,
        session = session,
        verdict = state.public.verdict,
        modifier = modifier,
    )
}

// ==================================================================== Host screens ==
//
// What PassAndPlay and MultiDevice-host render. UI buttons just
// submit the host action; PartyAwareSession (wrapping the session in local
// modes) issues per-player acks underneath. The router stays free of any
// readiness ceremony knowledge.

@Composable
private fun HostPhaseScreens(
    playMode: PlayMode,
    phase: WhodunitPhase,
    state: WhodunitState,
    case: ValidatedCase<WhodunitCase>,
    payload: WhodunitCase,
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    scope: kotlinx.coroutines.CoroutineScope,
    verdict: Verdict?,
    onBackToLibrary: () -> Unit,
    modifier: Modifier,
) {
    val multiDeviceSelf = (playMode as? PlayMode.MultiDevice)?.selfPlayerId
    var introAdvanceRequested by remember(phase, multiDeviceSelf) { mutableStateOf(false) }
    var briefingAdvanceRequested by remember(phase, multiDeviceSelf) { mutableStateOf(false) }

    if (multiDeviceSelf != null) {
        LaunchedEffect(phase, multiDeviceSelf) {
            when (phase) {
                WhodunitPhase.PublicIntro ->
                    session.submit(WhodunitAction.AcknowledgeIntro(multiDeviceSelf))
                WhodunitPhase.RulesBriefing ->
                    session.submit(WhodunitAction.AcknowledgeBriefing(multiDeviceSelf))
                else -> Unit
            }
        }

        val active = PartyReadiness.activeRoster(state.players, state.public.droppedPlayers)
        val introReady = PartyReadiness.isComplete(state.public.introAcknowledged, active)
        val briefingReady = PartyReadiness.isComplete(state.public.briefingReady, active)
        LaunchedEffect(phase, introAdvanceRequested, introReady) {
            if (phase == WhodunitPhase.PublicIntro && introAdvanceRequested && introReady) {
                session.submit(WhodunitAction.AdvanceFromIntro)
            }
        }
        LaunchedEffect(phase, briefingAdvanceRequested, briefingReady, state.public.briefingCardIndex) {
            if (phase == WhodunitPhase.RulesBriefing &&
                briefingAdvanceRequested &&
                briefingReady &&
                state.public.briefingCardIndex == LAST_BRIEFING_CARD_INDEX
            ) {
                session.submit(WhodunitAction.AdvanceBriefingCard(BRIEFING_COMPLETION_INDEX))
            }
        }
    }

    when (phase) {
        is WhodunitPhase.Setup -> LoadingScreen(modifier)

        is WhodunitPhase.PublicIntro -> PublicIntroScreen(
            title = case.envelope.title,
            intro = payload.publicIntro,
            bedrockClues = payload.bedrockClues,
            onContinue = {
                introAdvanceRequested = true
                scope.launch { session.submit(WhodunitAction.AdvanceFromIntro) }
            },
            modifier = modifier,
        )

        is WhodunitPhase.RulesBriefing -> RulesBriefingScreen(
            cardIndex = state.public.briefingCardIndex,
            onAdvance = { next ->
                if (next == BRIEFING_COMPLETION_INDEX) briefingAdvanceRequested = true
                scope.launch { session.submit(WhodunitAction.AdvanceBriefingCard(next)) }
            },
            modifier = modifier,
        )

        is WhodunitPhase.CharacterReveal -> when (playMode) {
            // Guarded at HostPhaseRouter's entry. Kept exhaustive so a future
            // PlayMode change cannot accidentally make Solo executable here.
            is PlayMode.Solo -> UnsupportedLocalPlayModeScreen(onBackToLibrary, modifier)
            // Pass-and-play: the device holds every player's private slice
            // and we drive a single local cursor through the full hand-off
            // ceremony so the phone can move safely around the table.
            is PlayMode.PassAndPlay -> LocalCharacterRevealSegment(
                session = session,
                roster = state.players,
                rolesViewed = state.public.rolesViewed,
                droppedPlayers = state.public.droppedPlayers,
                roleAssignmentGeneration = state.public.roleAssignmentGeneration,
                payload = payload,
                modifier = modifier,
            )
            // MultiDevice host is also a player at the table — they render only
            // their own dossier and wait while the other peers do the same.
            is PlayMode.MultiDevice -> SessionBackedSelfCharacterRevealSegment(
                session = session,
                selfPlayerId = playMode.selfPlayerId,
                isHost = true,
                payload = payload,
                modifier = modifier,
            )
        }

        is WhodunitPhase.Round -> RoundSegment(
            session = session,
            roundIndex = phase.index,
            state = state,
            payload = payload,
            playMode = playMode,
            modifier = modifier,
        )

        is WhodunitPhase.FinalVote -> VoteSegment(
            session = session,
            state = state,
            players = state.players,
            modifier = modifier,
            playMode = playMode,
        )

        is WhodunitPhase.TiedRevote -> {
            // After "Begin Revote" voteState transitions Tied → Collecting and
            // the same vote UI takes over. While still Tied, show the revote
            // explainer card.
            if (state.public.voteState is VoteState.Collecting) {
                VoteSegment(session, state, state.players, playMode, modifier)
            } else {
                TiedRevoteSegment(session, state, modifier)
            }
        }

        is WhodunitPhase.Reveal -> {
            // The reducer always sets `state.public.verdict` before entering
            // Reveal. If it's null here, something has gone wrong — render a
            // benign loading state instead of guessing the outcome (a wrong
            // guess would tell players the killer won when they actually won,
            // or name the wrong character).
            if (verdict == null) {
                LoadingScreen(modifier)
            } else {
                RevealStageScreen(
                    verdict = verdict,
                    killerDisplayName = killerDisplayName(verdict, payload),
                    revealNarrative = revealNarrativeFor(verdict, payload),
                    onAcknowledge = { scope.launch { session.submit(WhodunitAction.AcknowledgeReveal) } },
                    modifier = modifier,
                )
            }
        }

        is WhodunitPhase.PostGame -> PostGameScreen(
            onReplaySameCase = {
                scope.launch { session.submit(WhodunitAction.BeginReplay) }
            },
            onTryOtherMode = onBackToLibrary,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier,
        )
    }
}

// ==================================================================== Peer screens ==
//
// What a non-host MultiDevice peer renders. Most phases are "waiting on
// host" covers; two are peer-interactive (the peer's own character reveal,
// and casting the peer's own ballot when it's their turn).

@Composable
private fun PeerPhaseScreens(
    playMode: PlayMode.MultiDevice,
    phase: WhodunitPhase,
    state: WhodunitState,
    payload: WhodunitCase,
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    verdict: Verdict?,
    modifier: Modifier,
) {
    val waitingHint = stringResource(Res.string.peer_waiting_for_host)
    val waitingEyebrow = stringResource(Res.string.peer_waiting_eyebrow)
    LaunchedEffect(phase, playMode.selfPlayerId) {
        when (phase) {
            WhodunitPhase.PublicIntro ->
                session.submit(WhodunitAction.AcknowledgeIntro(playMode.selfPlayerId))
            WhodunitPhase.RulesBriefing ->
                session.submit(WhodunitAction.AcknowledgeBriefing(playMode.selfPlayerId))
            else -> Unit
        }
    }
    when (phase) {
        is WhodunitPhase.Setup -> LoadingScreen(modifier)

        is WhodunitPhase.PublicIntro -> PeerWaitingForHostScreen(
            eyebrow = waitingEyebrow,
            title = stringResource(Res.string.peer_intro_title),
            body = stringResource(Res.string.peer_intro_body),
            waitingHint = waitingHint,
            modifier = modifier,
        )

        is WhodunitPhase.RulesBriefing -> PeerWaitingForHostScreen(
            eyebrow = waitingEyebrow,
            title = stringResource(Res.string.peer_briefing_title),
            body = stringResource(Res.string.peer_briefing_body),
            waitingHint = waitingHint,
            modifier = modifier,
        )

        is WhodunitPhase.CharacterReveal -> SelfCharacterRevealSegment(
            session = session,
            state = state,
            selfPlayerId = playMode.selfPlayerId,
            isHost = false,
            payload = payload,
            modifier = modifier,
        )

        is WhodunitPhase.Round -> {
            // Elimination mode opens a vote inside the Round phase (after the
            // discussion timer). Peers must be able to cast their ballot —
            // showing the waiting screen here would leave Elimination peers
            // unable to participate in any round vote.
            val vs = state.public.voteState
            when {
                vs is VoteState.Collecting -> VoteSegment(
                    session = session,
                    state = state,
                    players = state.players,
                    modifier = modifier,
                    playMode = playMode,
                )
                // The host is holding on the "innocent eliminated" announcement
                // — peers see the same card, read-only, until the host advances.
                vs is VoteState.Resolved && !vs.wasKiller -> {
                    val accusedName = state.players
                        .firstOrNull { it.id == vs.accusedPlayerId }
                        ?.displayName ?: ""
                    EliminationInnocentOutcomeScreen(
                        eliminatedPlayerName = accusedName,
                        onContinue = null,
                        modifier = modifier,
                    )
                }
                else -> PeerWaitingForHostScreen(
                    eyebrow = waitingEyebrow,
                    title = stringResource(Res.string.peer_round_title),
                    body = stringResource(Res.string.peer_round_body),
                    waitingHint = waitingHint,
                    modifier = modifier,
                )
            }
        }

        is WhodunitPhase.FinalVote -> VoteSegment(
            session = session,
            state = state,
            players = state.players,
            modifier = modifier,
            playMode = playMode,
        )

        is WhodunitPhase.TiedRevote -> {
            if (state.public.voteState is VoteState.Collecting) {
                VoteSegment(session, state, state.players, playMode, modifier)
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

        is WhodunitPhase.Reveal -> {
            if (verdict == null) {
                // Peer is waiting for the host's next PublicStateSnapshot that
                // carries the verdict. Show a loading state rather than guess.
                LoadingScreen(modifier)
            } else {
                RevealStageScreen(
                    verdict = verdict,
                    killerDisplayName = killerDisplayName(verdict, payload),
                    revealNarrative = revealNarrativeFor(verdict, payload),
                    onAcknowledge = { /* peer cannot acknowledge — host closes the reveal */ },
                    modifier = modifier,
                )
            }
        }

        is WhodunitPhase.PostGame -> PeerWaitingForHostScreen(
            eyebrow = waitingEyebrow,
            title = stringResource(Res.string.peer_postgame_title),
            body = stringResource(Res.string.peer_postgame_body),
            waitingHint = stringResource(Res.string.peer_leave_room),
            modifier = modifier,
        )
    }
}

// ================================================================== Shared helpers ==

private fun killerCharIdFromVerdict(verdict: Verdict): String = when (verdict) {
    is Verdict.PlayersWin -> verdict.killerCharacterId
    is Verdict.KillerWins -> verdict.killerCharacterId
}

/**
 * Resolves the killer's display name from the verdict, not from the vote
 * target. The two diverge for [com.parlor.games.whodunit.domain.event.KillerWinCause.SurvivedToFinalTwo]
 * — the killer was never accused — and for [com.parlor.games.whodunit.domain.event.KillerWinCause.TieUnresolved],
 * where no single accused exists.
 */
private fun killerDisplayName(verdict: Verdict, payload: WhodunitCase): String {
    val killerCharId = killerCharIdFromVerdict(verdict)
    return payload.characters.firstOrNull { it.id == killerCharId }?.displayName.orEmpty()
}

private fun revealNarrativeFor(
    verdict: Verdict,
    payload: WhodunitCase,
): String = payload.revealNarratives[killerCharIdFromVerdict(verdict)] ?: ""

// =================================================================== Character reveal ==
//
// CharacterReveal is the simultaneous-reveal phase: every active-roster
// player has to be added to `state.public.rolesViewed` before the host's
// `AdvanceFromCharacterReveal` succeeds. `phase.playerIndex` is vestigial
// (always 0) and MUST NOT be used to drive the UI.
//
// Three distinct UIs live below — one per topology — with no shared
// "is selfPlayerId null?" branch:
//
//   - [LocalCharacterRevealSegment]   Pass-and-play. One device passed
//     around the table, full Handoff → Gate → Dossier → Hide ceremony so
//     each player only sees their own role.
//
//   - [SelfCharacterRevealSegment]    MultiDevice (host as player, or peer).
//     Each device only ever renders for its own self player, then waits
//     while peers finish. The host's Advance is auto-issued by the reducer's
//     gate (or by PartyAwareSession in local modes — not used here).

private enum class RevealStage { Handoff, Gate, Dossier, Hide }

/**
 * Pass-and-play character-reveal flow.
 *
 * Drives a single local cursor: the first active-roster player not yet in
 * `rolesViewed`. After the current player taps "Hide and Pass", the reducer
 * adds them to `rolesViewed` and the recomposition picks the next pending
 * player. Once everyone has confirmed, the segment submits the host's
 * `AdvanceFromCharacterReveal` exactly once and the phase moves on.
 */
@Composable
private fun LocalCharacterRevealSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    roster: List<Player>,
    rolesViewed: Set<PlayerId>,
    droppedPlayers: Set<PlayerId>,
    roleAssignmentGeneration: Long,
    payload: WhodunitCase,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pending = roster.filter { it.id !in droppedPlayers && it.id !in rolesViewed }
    val currentPlayer = pending.firstOrNull()

    // Everyone has confirmed — advance the phase. The LaunchedEffect runs
    // once per "all done" entry; the next state snapshot moves us out.
    if (currentPlayer == null) {
        LaunchedEffect(Unit) {
            session.submit(WhodunitAction.AdvanceFromCharacterReveal)
        }
        LoadingScreen(modifier)
        return
    }

    val nextPlayer = pending.getOrNull(1)

    // Inform the session who is "holding" the device. PassAndPlaySessionController
    // uses activeViewer as part of the privacy ceremony.
    LaunchedEffect(currentPlayer.id) {
        session.setActiveViewer(ViewerContext.Player(currentPlayer.id))
    }

    val privateProjection by session.privateStateFor(currentPlayer.id).collectAsState()
    val privateData = privateProjection.state.privatePerPlayer[currentPlayer.id]
    val role = privateData?.role
    val characterId = privateData?.characterId?.raw
    val character = characterId?.let { id -> payload.characters.firstOrNull { it.id == id } }

    var stage by remember(currentPlayer.id, roleAssignmentGeneration) {
        mutableStateOf(RevealStage.Handoff)
    }
    var privacyOpen by remember(currentPlayer.id, roleAssignmentGeneration) {
        mutableStateOf(false)
    }
    val dossierUnlocked = privateData?.dossierUnlocked == true

    // The reducer, not the tap, authorizes private disclosure. Waiting for the
    // projected unlock also keeps a rejected/stale network command on the
    // retryable gate instead of showing cached dossier data optimistically.
    LaunchedEffect(currentPlayer.id, roleAssignmentGeneration, dossierUnlocked, stage) {
        when {
            dossierUnlocked && stage == RevealStage.Gate -> stage = RevealStage.Dossier
            !dossierUnlocked && (stage == RevealStage.Dossier || stage == RevealStage.Hide) -> {
                stage = RevealStage.Gate
            }
        }
    }

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
                    scope.launch {
                        session.submit(
                            WhodunitAction.StartCharacterReveal(
                                currentPlayer.id,
                                roleAssignmentGeneration,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            RevealStage.Dossier -> {
                if (!dossierUnlocked || character == null || role == null) {
                    LoadingScreen(Modifier.fillMaxSize())
                } else {
                    DossierRevealScreen(
                        character = character,
                        role = role,
                        onDone = { stage = RevealStage.Hide },
                        modifier = Modifier.fillMaxSize(),
                        allCharacters = payload.characters,
                        deflectionTargets = privateData.deflectionTargets,
                    )
                }
            }
            RevealStage.Hide -> HideAndPassScreen(
                nextPlayerName = nextPlayer?.displayName,
                onTap = {
                    scope.launch {
                        session.setActiveViewer(ViewerContext.Public)
                        session.submit(
                            WhodunitAction.CompleteCharacterReveal(
                                currentPlayer.id,
                                roleAssignmentGeneration,
                            )
                        )
                    }
                    // The keyed remember(currentPlayer.id) resets `stage` to
                    // Handoff when the cursor advances on recomposition.
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                policy = PrivacyConcernUiPolicy.HostMayReroll,
                onContinue = { privacyOpen = false },
                onReroll = {
                    privacyOpen = false
                    scope.launch { session.submit(WhodunitAction.RequestReroll) }
                },
            )
        }
    }
}

/**
 * Multi-device character-reveal flow.
 *
 * Renders only for the local device's own [selfPlayerId]. [state] must be the
 * complete own-player projection from one revision; the public assignment
 * generation and private dossier are deliberately read from that one object.
 *
 * Stages:
 *  - self in `rolesViewed`  → waiting screen (others are still viewing).
 *  - self pending           → Handoff → Gate → Dossier → Hide, then submit
 *                             `CompleteCharacterReveal(self)`.
 */
@Composable
private fun SessionBackedSelfCharacterRevealSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    selfPlayerId: PlayerId,
    isHost: Boolean,
    payload: WhodunitCase,
    modifier: Modifier = Modifier,
) {
    val projection by session.privateStateFor(selfPlayerId).collectAsState()
    require(projection.playerId == selfPlayerId) {
        "Self reveal received another player's projection"
    }
    SelfCharacterRevealSegment(
        session = session,
        state = projection.state,
        selfPlayerId = selfPlayerId,
        isHost = isHost,
        payload = payload,
        modifier = modifier,
    )
}

@Composable
private fun SelfCharacterRevealSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    state: WhodunitState,
    selfPlayerId: PlayerId,
    isHost: Boolean,
    payload: WhodunitCase,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val roster = state.players
    val rolesViewed = state.public.rolesViewed
    val droppedPlayers = state.public.droppedPlayers
    val roleAssignmentGeneration = state.public.roleAssignmentGeneration
    val selfPlayer = roster.firstOrNull { it.id == selfPlayerId } ?: return
    val pending = roster.filter { it.id !in droppedPlayers && it.id !in rolesViewed }

    if (selfPlayer.id in rolesViewed) {
        if (isHost && pending.isEmpty()) {
            LaunchedEffect(session, rolesViewed) {
                session.submit(WhodunitAction.AdvanceFromCharacterReveal)
            }
        }
        // Self has confirmed; wait while the rest of the table catches up.
        val stillPending = pending.firstOrNull { it.id != selfPlayer.id }
        com.parlor.games.whodunit.ui.screens.reveal.CharacterRevealWaitingScreen(
            activePlayerName = stillPending?.displayName ?: selfPlayer.displayName,
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(selfPlayer.id) {
        session.setActiveViewer(ViewerContext.Player(selfPlayer.id))
    }

    val privateData: WhodunitPrivate? = state.privatePerPlayer[selfPlayer.id]
    val role = privateData?.role
    val characterId = privateData?.characterId?.raw
    val character = characterId?.let { id -> payload.characters.firstOrNull { it.id == id } }

    var stage by remember(selfPlayer.id, roleAssignmentGeneration) {
        mutableStateOf(RevealStage.Handoff)
    }
    var privacyOpen by remember(selfPlayer.id, roleAssignmentGeneration) {
        mutableStateOf(false)
    }
    val dossierUnlocked = privateData?.dossierUnlocked == true
    val privacyPolicy = privacyConcernUiPolicy(isHost)

    LaunchedEffect(selfPlayer.id, roleAssignmentGeneration, dossierUnlocked, stage) {
        when {
            dossierUnlocked && stage == RevealStage.Gate -> stage = RevealStage.Dossier
            !dossierUnlocked && (stage == RevealStage.Dossier || stage == RevealStage.Hide) -> {
                stage = RevealStage.Gate
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (stage) {
            RevealStage.Handoff -> CharacterRevealHandoffScreen(
                playerName = selfPlayer.displayName,
                onContinue = { stage = RevealStage.Gate },
                modifier = Modifier.fillMaxSize(),
            )
            RevealStage.Gate -> CharacterRevealGateScreen(
                playerName = selfPlayer.displayName,
                onRevealed = {
                    scope.launch {
                        session.submit(
                            WhodunitAction.StartCharacterReveal(
                                selfPlayer.id,
                                roleAssignmentGeneration,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            RevealStage.Dossier -> {
                if (!dossierUnlocked || character == null || role == null) {
                    LoadingScreen(Modifier.fillMaxSize())
                } else {
                    DossierRevealScreen(
                        character = character,
                        role = role,
                        onDone = { stage = RevealStage.Hide },
                        modifier = Modifier.fillMaxSize(),
                        allCharacters = payload.characters,
                        deflectionTargets = privateData.deflectionTargets,
                    )
                }
            }
            RevealStage.Hide -> HideAndPassScreen(
                nextPlayerName = null,
                onTap = {
                    scope.launch {
                        session.setActiveViewer(ViewerContext.Public)
                        session.submit(
                            WhodunitAction.CompleteCharacterReveal(
                                selfPlayer.id,
                                roleAssignmentGeneration,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                policy = privacyPolicy,
                onContinue = { privacyOpen = false },
                onReroll = if (privacyPolicy == PrivacyConcernUiPolicy.HostMayReroll) {
                    {
                        privacyOpen = false
                        scope.launch { session.submit(WhodunitAction.RequestReroll) }
                    }
                } else {
                    null
                },
            )
        }
    }
}

// ============================================================================== Rounds ==

@Composable
private fun RoundSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    roundIndex: Int,
    state: WhodunitState,
    payload: WhodunitCase,
    playMode: PlayMode,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clueThisRound = state.public.revealedClues.firstOrNull { it.roundIndex == roundIndex }
    val timer = state.public.timer
    val display = resolveRoundDisplayConfig(
        payload = payload,
        roundIndex = roundIndex,
        playerCount = state.players.size,
    )
    val title = display.title
    val tagline = display.tagline
    val discussionSeconds = display.discussionSeconds

    // Real-time discussion ticker. Keyed on the timer's stable id so:
    //  - a new round starting its own timer spawns a fresh loop,
    //  - composition rebuilds within the same round reuse the existing loop
    //    (no duplicate `TimerTicked` dispatches),
    //  - clearing the timer (TimerExpired / AdvanceFromDiscussion) cancels
    //    the LaunchedEffect.
    val timerId = timer?.timerId
    LaunchedEffect(timerId, session) {
        // Only the AUTHORITATIVE controller (host / pass-and-play, hostState != null)
        // drives the timer reducer. A peer's ShadowSessionController has
        // hostState == null and forwards every submit to the host — running the
        // ticker there would spam the host with one TimerTicked per second that
        // its authority gate rejects (TimerTicked is HostOnly), pure wasted
        // chatter competing with real actions. Peers render the countdown from
        // the host's snapshots instead. Perf/UX: cuts peer→host network noise.
        if (timerId != null && session.hostState != null) {
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
                    session.submit(WhodunitAction.StartDiscussionTimer(discussionSeconds))
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
            playMode = playMode,
            modifier = modifier,
        )
        // Elimination mode: the room voted off a non-killer and the game
        // continues. The reducer holds on `Resolved(wasKiller = false)` so
        // we can surface "[Name] was innocent. The killer is still among
        // you." (design doc §13). The host's Continue submits
        // AcknowledgeRevealCard, which clears the announcement and advances
        // the round.
        state.public.voteState.let { it is VoteState.Resolved && !it.wasKiller } -> {
            val resolved = state.public.voteState as VoteState.Resolved
            val accusedName = state.players
                .firstOrNull { it.id == resolved.accusedPlayerId }
                ?.displayName ?: ""
            EliminationInnocentOutcomeScreen(
                eliminatedPlayerName = accusedName,
                onContinue = {
                    scope.launch { session.submit(WhodunitAction.AcknowledgeRevealCard) }
                },
                modifier = modifier,
            )
        }
        else -> RoundTitleCardScreen(
            roundIndex = roundIndex,
            title = title,
            tagline = tagline,
            onContinue = { scope.launch { session.submit(WhodunitAction.RevealNextClue) } },
            modifier = modifier,
        )
    }
}

private data class RoundDisplayConfig(
    val title: String,
    val tagline: String,
    val discussionSeconds: Int,
)

/**
 * Resolve round title/tagline/discussionSeconds for a given round and player
 * count, following this fallback order:
 *  1. Exact player-count bucket in `roundConfigByPlayerCount`.
 *  2. Nearest available bucket (by absolute distance to the player count).
 *  3. On a tie, prefer the lower bucket.
 *  4. If no bucket has the requested round (or no buckets are authored),
 *     fall back to the hardcoded defaults.
 *
 * `roundIndex` is 1-based to match the router; the JSON `rounds` list is
 * indexed `roundIndex - 1`.
 */
private fun resolveRoundDisplayConfig(
    payload: WhodunitCase,
    roundIndex: Int,
    playerCount: Int,
): RoundDisplayConfig {
    val round = WhodunitRoundPolicy.authoredRound(payload, roundIndex, playerCount)
    val (defaultTitle, defaultTagline) = defaultRoundTitleAndTagline(roundIndex, playerCount)
    return RoundDisplayConfig(
        title = round?.titleCardText?.takeIf { it.isNotBlank() } ?: defaultTitle,
        tagline = round?.taglineText?.takeIf { it.isNotBlank() } ?: defaultTagline,
        discussionSeconds = WhodunitRoundPolicy.discussionSeconds(
            payload,
            roundIndex,
            playerCount,
        ),
    )
}

private fun defaultRoundTitleAndTagline(roundIndex: Int, playerCount: Int): Pair<String, String> {
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

private const val LAST_BRIEFING_CARD_INDEX = 3
private const val BRIEFING_COMPLETION_INDEX = 4

// ============================================================================== Voting ==

@Composable
private fun VoteSegment(
    session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    state: WhodunitState,
    players: List<Player>,
    playMode: PlayMode,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vote = state.public.voteState as? VoteState.Collecting

    if (vote == null) {
        // Not in a collecting state — could be Idle (vote not yet opened) or
        // Resolved/Tied/NoResolution (handled by phase router). Render nothing.
        return
    }

    val presentation = voteTurnPresentation(playMode, vote)
    val nextVoter = when (presentation) {
        is VoteTurnPresentation.LocalBallot -> presentation.voterId
        is VoteTurnPresentation.WaitingForVoter -> presentation.voterId
        VoteTurnPresentation.CloseByHost,
        VoteTurnPresentation.WaitingForHostTally,
        VoteTurnPresentation.Unsupported -> null
    }
    val nextVoterName = nextVoter?.let { id -> players.firstOrNull { it.id == id }?.displayName }

    when (presentation) {
        VoteTurnPresentation.CloseByHost -> {
            // Everyone has voted or abstained. Only the authoritative device
            // closes; peers wait for the resulting snapshot.
            LaunchedEffect(Unit) {
                session.submit(WhodunitAction.CloseVote)
            }
            HideScreen(
                line = stringResource(Res.string.whodunit_vote_counting),
                onTap = {},
                modifier = modifier,
            )
            return
        }
        VoteTurnPresentation.WaitingForHostTally -> {
            HideScreen(
                line = stringResource(Res.string.whodunit_vote_counting),
                onTap = {},
                modifier = modifier,
            )
            return
        }
        is VoteTurnPresentation.WaitingForVoter -> {
            VoteHandoffScreen(
                nextVoterName = nextVoterName ?: "the next voter",
                onContinue = { /* only the named player's device may open this ballot */ },
                modifier = modifier,
            )
            return
        }
        VoteTurnPresentation.Unsupported -> return
        is VoteTurnPresentation.LocalBallot -> Unit
    }

    val localVoter = presentation.voterId
    // Keying by the policy-owned voter identity closes any ballot as soon as
    // the authoritative turn moves to another player.
    var ballotOpen by remember(localVoter) { mutableStateOf(false) }
    if (!ballotOpen) {
        VoteHandoffScreen(
            nextVoterName = nextVoterName ?: "the next voter",
            onContinue = { ballotOpen = true },
            modifier = modifier,
        )
    } else {
        val candidates = players
            .filter { it.id in vote.candidatePlayerIds }
            .filter { it.id != localVoter }   // can't vote for yourself
            .map { it.id to it.displayName }
        VoteBallotScreen(
            currentVoterName = nextVoterName ?: "Voter",
            candidates = candidates,
            onVote = { target ->
                scope.launch {
                    session.submit(WhodunitAction.CastVote(localVoter, target))
                    ballotOpen = false
                }
            },
            onRefuse = {
                scope.launch {
                    session.submit(WhodunitAction.RefuseToVote(localVoter))
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
        onBeginRevote = { scope.launch { session.submit(WhodunitAction.OpenVote) } },
        modifier = modifier,
    )
}

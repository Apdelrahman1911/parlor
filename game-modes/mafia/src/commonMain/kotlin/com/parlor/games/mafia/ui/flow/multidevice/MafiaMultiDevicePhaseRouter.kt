package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.VoteOutcome
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_resolve_night
import com.parlor.games.mafia.resources.md_resolve_night_description
import com.parlor.games.mafia.resources.vote_outcome_all_abstained
import com.parlor.games.mafia.resources.vote_outcome_eliminated
import com.parlor.games.mafia.resources.vote_outcome_max_revotes
import com.parlor.games.mafia.resources.vote_outcome_tied
import com.parlor.games.mafia.resources.waiting_day_eyebrow_format
import com.parlor.games.mafia.resources.waiting_day_others_body
import com.parlor.games.mafia.resources.waiting_day_vote_eyebrow_format
import com.parlor.games.mafia.resources.waiting_eliminated_body
import com.parlor.games.mafia.resources.waiting_eliminated_headline
import com.parlor.games.mafia.resources.waiting_host_configuring_body
import com.parlor.games.mafia.resources.waiting_host_configuring_headline
import com.parlor.games.mafia.resources.waiting_night_eyebrow
import com.parlor.games.mafia.resources.waiting_night_pending_body
import com.parlor.games.mafia.resources.waiting_night_pending_headline
import com.parlor.games.mafia.resources.waiting_night_submitted_body
import com.parlor.games.mafia.resources.waiting_night_submitted_headline
import com.parlor.games.mafia.resources.waiting_not_voting_headline
import com.parlor.games.mafia.resources.waiting_role_others_body
import com.parlor.games.mafia.resources.waiting_role_others_headline
import com.parlor.games.mafia.resources.waiting_role_pending_headline
import com.parlor.games.mafia.resources.waiting_roles_eyebrow
import com.parlor.games.mafia.resources.waiting_setup_eyebrow
import com.parlor.games.mafia.resources.waiting_vote_cast_body
import com.parlor.games.mafia.resources.waiting_vote_cast_headline
import com.parlor.games.mafia.resources.waiting_vote_eyebrow
import com.parlor.games.mafia.resources.waiting_vote_pending_headline
import com.parlor.games.mafia.ui.screens.announce.NightAnnouncementScreen
import com.parlor.games.mafia.ui.screens.discussion.DiscussionScreen
import com.parlor.games.mafia.ui.screens.night.CivilianSuspectScreen
import com.parlor.games.mafia.ui.screens.night.DetectiveInspectScreen
import com.parlor.games.mafia.ui.screens.night.DetectiveResultScreen
import com.parlor.games.mafia.ui.screens.night.DoctorProtectScreen
import com.parlor.games.mafia.ui.screens.night.MafiaKillVoteScreen
import com.parlor.games.mafia.ui.screens.night.PickableTarget
import com.parlor.games.mafia.ui.screens.postgame.PostGameScreen
import com.parlor.games.mafia.ui.screens.reveal.PrivateRoleCardScreen
import com.parlor.games.mafia.ui.screens.reveal.roleDisplayName
import com.parlor.games.mafia.ui.screens.setup.MafiaSetupScreen
import com.parlor.games.mafia.ui.screens.vote.VoteAnnouncementScreen
import com.parlor.games.mafia.ui.screens.vote.VoteCastScreen
import com.parlor.session.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Multi-device phase router. Renders the right screen for [selfPlayerId]
 * given the current public phase. Both the host (with full state via the
 * canonical controller) and peers (with only their own private slice via
 * the shadow controller) use this router — the projection policy already
 * stripped state correctly per device, so the router can treat them
 * identically.
 *
 * The router is **structurally** privacy-safe: a peer never sees another
 * peer's MafiaPrivate because the host bridge never sent it. Even if a
 * malicious peer tried to ask for someone else's screen, the data simply
 * isn't there to render.
 *
 * Phases:
 *  - Setup → host renders [MafiaSetupScreen]; peers see "host configuring".
 *  - RoleAssignment → render self's role card; tap acks. Host advances when
 *    all alive players have acked (PartyAwareSession + MafiaReadinessGate
 *    handle that auto-fire on the host side).
 *  - Night → render the right action screen for self's role (Mafia kill,
 *    Doctor protect, Detective inspect, Civilian suspect, or Detective
 *    result if a pending result is present). Host explicit-advances via
 *    ResolveNight when everyone has submitted.
 *  - NightAnnouncement / VoteAnnouncement → ack on tap.
 *  - Discussion → host can OpenVote.
 *  - Voting → render VoteCastScreen for self.
 *  - PostGame → standard PostGameScreen.
 */
@Composable
internal fun MafiaMultiDevicePhaseRouter(
    state: MafiaState,
    selfPlayerId: PlayerId,
    isHost: Boolean,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val phase = state.phase
        val self = state.players.firstOrNull { it.id == selfPlayerId }
        val selfPrivate = state.privatePerPlayer[selfPlayerId]
        val selfSlot = state.public.roster.firstOrNull { it.playerId == selfPlayerId }

        // The host is the sole authority for the gated phase advances. Peers
        // ack from their own devices but cannot submit the advance (the
        // authority gate rejects it), and PartyAwareSession only auto-fills
        // acks in LOCAL play — so in multi-device nothing drove
        // AdvanceFromRoleAssignment / ResolveNight / OpenDiscussion / CloseVote /
        // AdvanceFromVoteAnnouncement and the game deadlocked at the very first
        // transition. The host drives them here once each readiness gate holds.
        // See PROBLEMS_PARLOR.md → mafia-ui-001 (advance-trigger half).
        if (isHost) {
            HostPhaseProgressionDriver(state = state, session = session)
        }

        when (phase) {
            MafiaPhase.Setup -> SetupSegment(
                state = state,
                isHost = isHost,
                session = session,
                scope = scope,
            )

            MafiaPhase.RoleAssignment -> RoleAssignmentSegment(
                self = self,
                priv = selfPrivate,
                state = state,
                session = session,
                scope = scope,
            )

            is MafiaPhase.Night -> NightSegment(
                phase = phase,
                state = state,
                self = self,
                selfSlot = selfSlot,
                priv = selfPrivate,
                isHost = isHost,
                session = session,
                scope = scope,
            )

            is MafiaPhase.NightAnnouncement -> NightAnnouncementSegment(
                day = phase.day,
                state = state,
                priv = selfPrivate,
                session = session,
                scope = scope,
            )

            is MafiaPhase.Discussion -> DiscussionScreen(
                day = phase.day,
                aliveNames = state.public.roster
                    .filter { it.alive && it.playerId !in state.public.droppedPlayers }
                    .map { it.displayName },
                deadNames = state.public.roster
                    .filter { !it.alive && it.playerId !in state.public.droppedPlayers }
                    .map { it.displayName },
                timerLabel = null,
                isHost = isHost,
                onOpenVote = {
                    scope.launch { session.submit(MafiaAction.OpenVote) }
                },
                modifier = Modifier.fillMaxSize(),
            )

            is MafiaPhase.Voting -> VotingSegment(
                phase = phase,
                state = state,
                self = self,
                selfSlot = selfSlot,
                session = session,
                scope = scope,
            )

            is MafiaPhase.VoteAnnouncement -> VoteAnnouncementSegment(
                day = phase.day,
                state = state,
                priv = selfPrivate,
                session = session,
                scope = scope,
            )

            MafiaPhase.PostGame -> PostGameScreen(
                winner = state.public.winner,
                finalRoles = finalRoles(state),
                onExit = onBackToHome,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ==================================================================== Host progression ==

/**
 * Host-only driver for the gated phase advances. In multi-device the host owns
 * every advance; peers only ack from their own devices, so without this the
 * game deadlocked at RoleAssignment (and every later gate). Each advance is
 * submitted once its readiness gate holds; the reducer re-checks the same gate
 * and no-ops if not yet ready, so an early/duplicate fire is harmless. This
 * mirrors the pass-and-play router's `LaunchedEffect` auto-fires — there
 * `PartyAwareSession` auto-fills the acks; here the real acks arrive over the
 * wire and these effects re-evaluate as each lands.
 *
 * `ResolveNight` is included so a host who has been eliminated can still resolve
 * the night — the manual Resolve button lives only in the alive-host branch, so
 * a dead host would otherwise be stuck forever (the night-resolution deadlock).
 */
@Composable
private fun HostPhaseProgressionDriver(
    state: MafiaState,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
) {
    val advance = nextHostAdvance(state)
    // Key on (phase, advance) so the effect re-evaluates as acks land: it stays
    // dormant while `advance` is null and fires once the gate flips it non-null,
    // and re-fires on a genuinely new phase (e.g. Night round 1 → round 2).
    LaunchedEffect(state.phase, advance) {
        if (advance != null) session.submit(advance)
    }
}

/**
 * Pure decision for [HostPhaseProgressionDriver]: the gated host advance that is
 * ready to submit for [state] in multi-device, or `null` when none is (gate not
 * yet satisfied, or the phase has no auto-advance — Setup/Discussion/PostGame).
 * Extracted so the multi-device progression gating is unit-testable without a
 * Compose harness. The reducer remains the canonical gate; this only decides
 * *when the host offers* the advance.
 */
internal fun nextHostAdvance(state: MafiaState): MafiaAction? {
    val active = state.players.map { it.id }.filterNot { it in state.public.droppedPlayers }
    val aliveActive = active.filter { id ->
        state.public.roster.firstOrNull { it.playerId == id }?.alive == true
    }
    return when (state.phase) {
        MafiaPhase.RoleAssignment ->
            MafiaAction.AdvanceFromRoleAssignment.takeIf {
                active.isNotEmpty() &&
                    active.all { state.privatePerPlayer[it]?.roleAcknowledged == true }
            }
        is MafiaPhase.Night ->
            MafiaAction.ResolveNight.takeIf {
                aliveActive.isNotEmpty() &&
                    aliveActive.all { id ->
                        val private = state.privatePerPlayer[id]
                        private?.nightChoiceSubmitted == true &&
                            (
                                private.pendingDetectiveResult == null ||
                                    private.detectiveResultAcknowledged
                            )
                    }
            }
        is MafiaPhase.NightAnnouncement ->
            MafiaAction.OpenDiscussion.takeIf {
                aliveActive.isNotEmpty() &&
                    aliveActive.all { state.privatePerPlayer[it]?.nightAcknowledged == true }
            }
        is MafiaPhase.Voting -> {
            val vote = state.public.activeVote
            MafiaAction.CloseVote.takeIf {
                vote != null && vote.ballot.isNotEmpty() &&
                    vote.ballot.all { it in vote.castSoFar.keys || it in vote.abstained }
            }
        }
        is MafiaPhase.VoteAnnouncement ->
            MafiaAction.AdvanceFromVoteAnnouncement.takeIf {
                aliveActive.isNotEmpty() &&
                    aliveActive.all { state.privatePerPlayer[it]?.voteAcknowledged == true }
            }
        else -> null
    }
}

// =============================================================================== Setup ==

@Composable
private fun SetupSegment(
    state: MafiaState,
    isHost: Boolean,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    if (isHost) {
        MafiaSetupScreen(
            playerCount = state.players.size,
            initialSettings = state.public.settings,
            onStart = { chosen ->
                scope.launch {
                    session.submit(MafiaAction.ApplySettings(chosen))
                    session.submit(MafiaAction.StartGame)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_setup_eyebrow),
            headline = stringResource(Res.string.waiting_host_configuring_headline),
            body = stringResource(Res.string.waiting_host_configuring_body),
        )
    }
}

// ====================================================================== Role assignment ==

@Composable
private fun RoleAssignmentSegment(
    self: Player?,
    priv: MafiaPrivate?,
    state: MafiaState,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    if (self == null || priv == null) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_roles_eyebrow),
            headline = stringResource(Res.string.waiting_role_pending_headline),
        )
        return
    }
    if (priv.roleAcknowledged) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_roles_eyebrow),
            headline = stringResource(Res.string.waiting_role_others_headline),
            body = stringResource(Res.string.waiting_role_others_body),
        )
        return
    }
    PrivateRoleCardScreen(
        playerName = self.displayName,
        role = priv.role,
        team = priv.team,
        knownTeammateNames = priv.knownTeammates.mapNotNull { id -> displayNameOf(state, id) },
        onAcknowledged = {
            scope.launch { session.submit(MafiaAction.AcknowledgeRoleViewed(self.id)) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// =============================================================================== Night ==

@Composable
private fun NightSegment(
    phase: MafiaPhase.Night,
    state: MafiaState,
    self: Player?,
    selfSlot: PublicPlayerSlot?,
    priv: MafiaPrivate?,
    isHost: Boolean,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    if (self == null || priv == null) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_night_eyebrow),
            headline = stringResource(Res.string.waiting_night_pending_headline),
            body = stringResource(Res.string.waiting_night_pending_body),
        )
        return
    }
    val alive = selfSlot?.alive == true
    if (!alive) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_night_eyebrow),
            headline = stringResource(Res.string.waiting_eliminated_headline),
            body = stringResource(Res.string.waiting_eliminated_body),
        )
        return
    }

    // Detective who already inspected this night: show the private result first.
    val detectiveResult = priv.pendingDetectiveResult
    if (detectiveResult != null && !priv.detectiveResultAcknowledged) {
        DetectiveResultScreen(
            detectiveName = self.displayName,
            inspectedName = displayNameOf(state, detectiveResult.target) ?: detectiveResult.target.raw,
            seesAs = detectiveResult.seesAs,
            onAcknowledged = {
                scope.launch { session.submit(MafiaAction.AcknowledgeDetectiveResult(self.id)) }
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // If self has already submitted their night action for this round, wait.
    // Gate on nightChoiceSubmitted (not pendingNightChoice): Civilians and any
    // role that Skips leave pendingNightChoice null yet HAVE submitted, so the
    // old gate left them on the action screen forever — able to resubmit and
    // blocking the host's ResolveNight. See PROBLEMS_PARLOR.md → mafia-ui-002.
    if (priv.nightChoiceSubmitted) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_night_eyebrow),
            headline = stringResource(Res.string.waiting_night_submitted_headline),
            body = stringResource(Res.string.waiting_night_submitted_body),
            footer = if (isHost) {
                { ResolveNightButton(session = session, scope = scope) }
            } else null,
        )
        return
    }

    val targets = buildTargets(state, selfPlayerId = self.id, role = priv.role, settings = state.public.settings, coordinationRound = phase.mafiaCoordinationRound)
    when (priv.role) {
        Role.Mafia -> MafiaKillVoteScreen(
            voterName = self.displayName,
            targets = targets,
            coordinationRound = phase.mafiaCoordinationRound,
            previousRoundTally = priv.mafiaCoordination?.previousRoundTally,
            targetNameLookup = { id -> displayNameOf(state, id) ?: id.raw },
            onSubmit = { picked ->
                scope.launch { session.submit(MafiaAction.SubmitMafiaKillVote(self.id, picked)) }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Role.Doctor -> DoctorProtectScreen(
            doctorName = self.displayName,
            targets = targets,
            onSubmit = { picked ->
                scope.launch { session.submit(MafiaAction.SubmitDoctorProtect(self.id, picked)) }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Role.Detective -> DetectiveInspectScreen(
            detectiveName = self.displayName,
            targets = targets,
            onSubmit = { picked ->
                scope.launch { session.submit(MafiaAction.SubmitDetectiveInspect(self.id, picked)) }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Role.Civilian -> CivilianSuspectScreen(
            civilianName = self.displayName,
            targets = targets,
            onSubmit = { picked ->
                scope.launch { session.submit(MafiaAction.SubmitCivilianSuspicion(self.id, picked)) }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ResolveNightButton(
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    ParlorButton(
        label = stringResource(Res.string.md_resolve_night),
        contentDescription = stringResource(Res.string.md_resolve_night_description),
        onClick = { scope.launch { session.submit(MafiaAction.ResolveNight) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ===================================================================== Night announce ==

@Composable
private fun NightAnnouncementSegment(
    day: Int,
    state: MafiaState,
    priv: MafiaPrivate?,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    val announcement = state.public.lastNight
    val killedName = announcement?.killedPlayerId?.let { displayNameOf(state, it) }
    val killedSlot = announcement?.killedPlayerId?.let { id ->
        state.public.roster.firstOrNull { it.playerId == id }
    }
    if (priv?.nightAcknowledged == true) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_day_eyebrow_format, day),
            headline = stringResource(Res.string.waiting_role_others_headline),
            body = stringResource(Res.string.waiting_day_others_body),
        )
        return
    }
    NightAnnouncementScreen(
        day = day,
        killedPlayerName = killedName,
        revealedRole = killedSlot?.revealedRole,
        wasSaved = announcement?.wasSaved == true,
        onAcknowledged = {
            val selfId = priv?.let { state.privatePerPlayer.entries.firstOrNull { e -> e.value === it }?.key }
            if (selfId != null) {
                scope.launch { session.submit(MafiaAction.AcknowledgeNightAnnouncement(selfId)) }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// =============================================================================== Voting ==

@Composable
private fun VotingSegment(
    phase: MafiaPhase.Voting,
    state: MafiaState,
    self: Player?,
    selfSlot: PublicPlayerSlot?,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    val activeVote = state.public.activeVote
    if (self == null || activeVote == null || selfSlot?.alive != true) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_vote_eyebrow),
            headline = stringResource(Res.string.waiting_vote_pending_headline),
        )
        return
    }
    if (self.id !in activeVote.ballot) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_day_vote_eyebrow_format, phase.day),
            headline = stringResource(Res.string.waiting_not_voting_headline),
        )
        return
    }
    if (self.id in activeVote.castSoFar || self.id in activeVote.abstained) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_day_vote_eyebrow_format, phase.day),
            headline = stringResource(Res.string.waiting_vote_cast_headline),
            body = stringResource(Res.string.waiting_vote_cast_body),
        )
        return
    }
    val candidates = activeVote.candidates
        .filter { state.public.settings.allowSelfVote || it != self.id }
        .map { id ->
            val slot = state.public.roster.firstOrNull { it.playerId == id }
            PickableTarget(
                id = id,
                name = slot?.displayName ?: id.raw,
            )
        }
    VoteCastScreen(
        voterName = self.displayName,
        candidates = candidates,
        revoteRound = phase.revoteRound,
        onCast = { target ->
            scope.launch { session.submit(MafiaAction.CastVote(self.id, target)) }
        },
        onAbstain = {
            scope.launch { session.submit(MafiaAction.AbstainVote(self.id)) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ===================================================================== Vote announce ==

@Composable
private fun VoteAnnouncementSegment(
    day: Int,
    state: MafiaState,
    priv: MafiaPrivate?,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
) {
    val announcement = state.public.lastVote
    val eliminatedSlot = announcement?.eliminatedPlayerId?.let { id ->
        state.public.roster.firstOrNull { it.playerId == id }
    }
    if (priv?.voteAcknowledged == true) {
        WaitingScreen(
            eyebrow = stringResource(Res.string.waiting_day_eyebrow_format, day),
            headline = stringResource(Res.string.waiting_role_others_headline),
        )
        return
    }
    val eliminatedRoleLabel = eliminatedSlot?.revealedRole?.let { roleDisplayName(it) }
    val outcomeText = announcement?.outcome?.let { outcomeLine(it) } ?: ""
    VoteAnnouncementScreen(
        day = day,
        tally = announcement?.tally.orEmpty(),
        nameLookup = { id -> displayNameOf(state, id) ?: id.raw },
        eliminatedName = eliminatedSlot?.displayName,
        eliminatedRole = eliminatedRoleLabel,
        outcomeLine = outcomeText,
        onAcknowledged = {
            val selfId = priv?.let { p -> state.privatePerPlayer.entries.firstOrNull { it.value === p }?.key }
            if (selfId != null) {
                scope.launch { session.submit(MafiaAction.AcknowledgeVoteAnnouncement(selfId)) }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ============================================================================ Helpers ==

@Composable
private fun WaitingScreen(
    eyebrow: String,
    headline: String,
    body: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    HeroBackdrop(modifier = Modifier.fillMaxSize()) {
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
            EyebrowLabel(text = eyebrow, textAlign = TextAlign.Center)
            Text(
                text = headline,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (footer != null) footer()
        }
    }
}

private fun buildTargets(
    state: MafiaState,
    selfPlayerId: PlayerId,
    role: Role,
    settings: com.parlor.games.mafia.domain.settings.MafiaSettings,
    coordinationRound: Int,
): List<PickableTarget> {
    val alive = state.public.roster.filter {
        it.alive && it.playerId !in state.public.droppedPlayers
    }
    return alive
        .filter { slot ->
            when (role) {
                Role.Mafia -> {
                    if (slot.playerId == selfPlayerId) {
                        false
                    } else if (settings.mafiaCanTargetMafia) {
                        true
                    } else {
                        slot.playerId !in
                            state.privatePerPlayer[selfPlayerId]?.knownTeammates.orEmpty()
                    }
                }
                Role.Doctor -> {
                    val canTargetSelf = settings.doctorCanSelfHeal || slot.playerId != selfPlayerId
                    val previousTarget = state.privatePerPlayer[selfPlayerId]?.previousDoctorProtect
                    val notConsecutive = settings.doctorCanProtectSamePlayerConsecutively ||
                        slot.playerId != previousTarget
                    canTargetSelf && notConsecutive
                }
                Role.Detective -> if (settings.detectiveCanInspectSelf) true else slot.playerId != selfPlayerId
                Role.Civilian -> slot.playerId != selfPlayerId
            }
        }
        .map { slot ->
            PickableTarget(
                id = slot.playerId,
                name = slot.displayName,
            )
        }
}

@Composable
private fun outcomeLine(outcome: VoteOutcome): String = when (outcome) {
    VoteOutcome.Eliminated -> stringResource(Res.string.vote_outcome_eliminated)
    VoteOutcome.SkippedDueToTie -> stringResource(Res.string.vote_outcome_tied)
    VoteOutcome.MaxRevotesReached -> stringResource(Res.string.vote_outcome_max_revotes)
    VoteOutcome.AllAbstained -> stringResource(Res.string.vote_outcome_all_abstained)
}

internal fun displayNameOf(state: MafiaState, id: PlayerId): String? =
    state.public.roster.firstOrNull { it.playerId == id }?.displayName
        ?: state.players.firstOrNull { it.id == id }?.displayName

private fun finalRoles(state: MafiaState): List<Pair<String, Role>> {
    val map = state.hostOnly.fullRoleMap
    // hostOnly is stripped on peer; fall back to revealed roster roles where present.
    val source: Map<PlayerId, Role> = if (map.isNotEmpty()) {
        map
    } else {
        state.public.roster.mapNotNull { slot ->
            slot.revealedRole?.let { slot.playerId to it }
        }.toMap()
    }
    return state.public.roster.mapNotNull { slot ->
        val role = source[slot.playerId] ?: return@mapNotNull null
        slot.displayName to role
    }
}

package com.parlor.games.mafia.ui.flow.passandplay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_detective_reveal_eyebrow
import com.parlor.games.mafia.resources.night_eyebrow_civilian
import com.parlor.games.mafia.resources.night_eyebrow_detective
import com.parlor.games.mafia.resources.night_eyebrow_doctor
import com.parlor.games.mafia.resources.night_eyebrow_mafia
import com.parlor.games.mafia.resources.night_eyebrow_mafia_revote
import com.parlor.games.mafia.resources.reveal_eyebrow_your_role
import com.parlor.games.mafia.resources.vote_eyebrow
import com.parlor.games.mafia.resources.vote_outcome_all_abstained
import com.parlor.games.mafia.resources.vote_outcome_eliminated
import com.parlor.games.mafia.resources.vote_outcome_max_revotes
import com.parlor.games.mafia.resources.vote_outcome_tied
import com.parlor.games.mafia.resources.vote_revote_eyebrow_format
import com.parlor.games.mafia.ui.screens.announce.NightAnnouncementScreen
import com.parlor.games.mafia.ui.screens.discussion.DiscussionScreen
import com.parlor.games.mafia.ui.screens.handoff.MafiaHideAndPassScreen
import com.parlor.games.mafia.ui.screens.handoff.MafiaRoleRevealGateScreen
import com.parlor.games.mafia.ui.screens.handoff.MafiaRoleRevealHandoffScreen
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
 * Routes the pass-and-play Mafia phase tree.
 *
 * Privacy is enforced by the state model: this router runs on the host
 * device and inspects [MafiaState.privatePerPlayer] directly only to drive
 * the per-player cycle (who plays next, what their role is). Every
 * private screen is gated by a Handoff → Gate → Screen → Hide ceremony,
 * mandatory between any two consecutive private renderings — including
 * between Mafia members during the coordination pass.
 *
 * Multi-device flows live in `ui/flow/multidevice/` and do not use this
 * host-private pass-and-play router.
 */
@Composable
internal fun MafiaPassAndPlayPhaseRouter(
    state: MafiaState,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val phase = state.phase) {
            MafiaPhase.Setup -> SetupSegment(
                state = state,
                session = session,
                scope = scope,
                modifier = Modifier.fillMaxSize(),
            )
            MafiaPhase.RoleAssignment -> RoleAssignmentSegment(
                state = state,
                session = session,
                scope = scope,
                modifier = Modifier.fillMaxSize(),
            )
            is MafiaPhase.Night -> NightSegment(
                state = state,
                phase = phase,
                session = session,
                scope = scope,
                modifier = Modifier.fillMaxSize(),
            )
            is MafiaPhase.NightAnnouncement -> NightAnnouncementSegment(
                state = state,
                phase = phase,
                session = session,
                scope = scope,
                modifier = Modifier.fillMaxSize(),
            )
            is MafiaPhase.Discussion -> DiscussionSegment(
                state = state,
                phase = phase,
                session = session,
                scope = scope,
                isHost = true,
                modifier = Modifier.fillMaxSize(),
            )
            is MafiaPhase.Voting -> VotingSegment(
                state = state,
                phase = phase,
                session = session,
                scope = scope,
                modifier = Modifier.fillMaxSize(),
            )
            is MafiaPhase.VoteAnnouncement -> VoteAnnouncementSegment(
                state = state,
                phase = phase,
                session = session,
                scope = scope,
                modifier = Modifier.fillMaxSize(),
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

// =============================================================================== Setup ==

@Composable
private fun SetupSegment(
    state: MafiaState,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    MafiaSetupScreen(
        playerCount = state.players.size,
        initialSettings = state.public.settings,
        onStart = { chosen ->
            scope.launch {
                session.submit(MafiaAction.ConfigureAndStart(chosen))
            }
        },
        modifier = modifier,
    )
}

// ====================================================================== Role assignment ==

private enum class RevealStage { Handoff, Gate, Reveal, Hide }

@Composable
private fun RoleAssignmentSegment(
    state: MafiaState,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val pending = state.players
        .filter { it.id !in state.public.droppedPlayers }
        .filter { state.privatePerPlayer[it.id]?.roleAcknowledged != true }

    val current = pending.firstOrNull()
    if (current == null) {
        LaunchedEffect(Unit) { session.submit(MafiaAction.AdvanceFromRoleAssignment) }
        MafiaLoadingScreen(modifier)
        return
    }

    val next = pending.getOrNull(1)
    val priv = state.privatePerPlayer[current.id]
    if (priv == null) {
        MafiaLoadingScreen(modifier)
        return
    }

    var stage by remember(current.id) { mutableStateOf(RevealStage.Handoff) }

    when (stage) {
        RevealStage.Handoff -> MafiaRoleRevealHandoffScreen(
            playerName = current.displayName,
            onContinue = { stage = RevealStage.Gate },
            modifier = modifier,
        )
        RevealStage.Gate -> MafiaRoleRevealGateScreen(
            playerName = current.displayName,
            eyebrow = stringResource(Res.string.reveal_eyebrow_your_role),
            onRevealed = { stage = RevealStage.Reveal },
            modifier = modifier,
        )
        RevealStage.Reveal -> PrivateRoleCardScreen(
            playerName = current.displayName,
            role = priv.role,
            team = priv.team,
            knownTeammateNames = priv.knownTeammates.mapNotNull { id -> displayNameOf(state, id) },
            onAcknowledged = { stage = RevealStage.Hide },
            modifier = modifier,
        )
        RevealStage.Hide -> MafiaHideAndPassScreen(
            nextPlayerName = next?.displayName,
            onTap = {
                scope.launch { session.submit(MafiaAction.AcknowledgeRoleViewed(current.id)) }
            },
            modifier = modifier,
        )
    }
}

// =============================================================================== Night ==
//
// Round 1: cycle every alive active player in seat order — Mafia/Doctor/
// Detective each pick a target, Civilian picks an optional suspicion.
// Round 2 (Mafia revote): cycle only Mafia members who haven't re-submitted
// for this round. After the last player in the round confirms, host submits
// ResolveNight; the reducer either resolves or moves to round=2 / next phase.

private enum class NightStage { Handoff, Gate, Choose, Hide }

@Composable
private fun NightSegment(
    state: MafiaState,
    phase: MafiaPhase.Night,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val aliveActive = aliveActivePlayers(state)

    // Inspection results are available as soon as the Detective submits, and
    // must be privately viewed before the host may resolve the night. This
    // prevents a same-night kill or a conflated remote snapshot from losing the
    // result before its owner can see it.
    val pendingDetective = aliveActive.firstOrNull { player ->
        val private = state.privatePerPlayer[player.id]
        private?.role == Role.Detective &&
            private.pendingDetectiveResult != null &&
            !private.detectiveResultAcknowledged
    }
    if (pendingDetective != null) {
        DetectivePrivateResultSegment(
            detective = pendingDetective,
            state = state,
            session = session,
            scope = scope,
            modifier = modifier,
        )
        return
    }

    // Completion must come from the reducer-owned flag. Advancing a local UI
    // tracker before submit() was authoritatively accepted could skip a player
    // after a rejected/cancelled submission and leave ResolveNight permanently
    // blocked. The reducer clears Mafia flags when it opens round two, so the
    // same canonical predicate covers both the initial pass and the revote.
    val pending = pendingNightPlayers(state, phase)

    val current = pending.firstOrNull()
    if (current == null) {
        LaunchedEffect(phase) { session.submit(MafiaAction.ResolveNight) }
        MafiaLoadingScreen(modifier)
        return
    }

    val next = pending.getOrNull(1)
    val priv = state.privatePerPlayer[current.id]
    if (priv == null) {
        MafiaLoadingScreen(modifier)
        return
    }

    var stage by remember(current.id, phase.mafiaCoordinationRound) {
        mutableStateOf(NightStage.Handoff)
    }
    // The Choose screen sets `pendingChoice`; the Hide tap submits it. This
    // ensures the hide screen actually renders between players — submitting
    // on Choose would advance state immediately and skip the cover screen.
    var pendingChoice by remember(current.id, phase.mafiaCoordinationRound) {
        mutableStateOf<NightChoice?>(null)
    }

    when (stage) {
        NightStage.Handoff -> MafiaRoleRevealHandoffScreen(
            playerName = current.displayName,
            onContinue = { stage = NightStage.Gate },
            modifier = modifier,
        )
        NightStage.Gate -> MafiaRoleRevealGateScreen(
            playerName = current.displayName,
            eyebrow = nightEyebrow(priv.role, phase.mafiaCoordinationRound),
            onRevealed = { stage = NightStage.Choose },
            modifier = modifier,
        )
        NightStage.Choose -> NightChooseScreen(
            current = current,
            currentRole = priv.role,
            state = state,
            phase = phase,
            onSubmit = { target ->
                pendingChoice = NightChoice(role = priv.role, target = target)
                stage = NightStage.Hide
            },
            modifier = modifier,
        )
        NightStage.Hide -> MafiaHideAndPassScreen(
            nextPlayerName = next?.displayName,
            onTap = {
                val choice = pendingChoice ?: return@MafiaHideAndPassScreen
                val voterId = current.id
                scope.launch {
                    session.submit(actionFor(choice.role, voterId, choice.target))
                }
            },
            modifier = modifier,
        )
    }
}

private data class NightChoice(val role: Role, val target: PlayerId?)

@Composable
private fun nightEyebrow(role: Role, round: Int): String = when (role) {
    Role.Mafia -> if (round >= 2) {
        stringResource(Res.string.night_eyebrow_mafia_revote)
    } else {
        stringResource(Res.string.night_eyebrow_mafia)
    }
    Role.Detective -> stringResource(Res.string.night_eyebrow_detective)
    Role.Doctor -> stringResource(Res.string.night_eyebrow_doctor)
    Role.Civilian -> stringResource(Res.string.night_eyebrow_civilian)
}

private fun actionFor(role: Role, by: PlayerId, target: PlayerId?): MafiaAction = when (role) {
    Role.Mafia -> MafiaAction.SubmitMafiaKillVote(by, target)
    Role.Detective -> MafiaAction.SubmitDetectiveInspect(by, target)
    Role.Doctor -> MafiaAction.SubmitDoctorProtect(by, target)
    Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(by, target)
}

@Composable
private fun NightChooseScreen(
    current: Player,
    currentRole: Role,
    state: MafiaState,
    phase: MafiaPhase.Night,
    onSubmit: (PlayerId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.public.settings
    val aliveIds = aliveActiveIds(state)

    when (currentRole) {
        Role.Mafia -> {
            val targets = targetsForMafia(state, current.id, settings.mafiaCanTargetMafia)
            val priv = state.privatePerPlayer[current.id]
            val coordination = priv?.mafiaCoordination
            MafiaKillVoteScreen(
                voterName = current.displayName,
                targets = targets,
                coordinationRound = phase.mafiaCoordinationRound,
                previousRoundTally = coordination?.previousRoundTally,
                targetNameLookup = { id -> displayNameOf(state, id) ?: id.raw },
                onSubmit = onSubmit,
                modifier = modifier,
            )
        }
        Role.Detective -> {
            val targets = aliveIds
                .filter { settings.detectiveCanInspectSelf || it != current.id }
                .map { id ->
                    PickableTarget(id = id, name = displayNameOf(state, id) ?: id.raw)
                }
            DetectiveInspectScreen(
                detectiveName = current.displayName,
                targets = targets,
                onSubmit = onSubmit,
                modifier = modifier,
            )
        }
        Role.Doctor -> {
            val previousTarget = state.privatePerPlayer[current.id]?.previousDoctorProtect
            val targets = aliveIds
                .filter { settings.doctorCanSelfHeal || it != current.id }
                .filter {
                    settings.doctorCanProtectSamePlayerConsecutively ||
                        it != previousTarget
                }
                .map { id -> PickableTarget(id = id, name = displayNameOf(state, id) ?: id.raw) }
            DoctorProtectScreen(
                doctorName = current.displayName,
                targets = targets,
                onSubmit = onSubmit,
                modifier = modifier,
            )
        }
        Role.Civilian -> {
            val targets = aliveIds
                .filter { it != current.id }
                .map { id -> PickableTarget(id = id, name = displayNameOf(state, id) ?: id.raw) }
            CivilianSuspectScreen(
                civilianName = current.displayName,
                targets = targets,
                onSubmit = onSubmit,
                modifier = modifier,
            )
        }
    }
}

private fun targetsForMafia(
    state: MafiaState,
    voterId: PlayerId,
    mafiaCanTargetMafia: Boolean,
): List<PickableTarget> {
    val aliveIds = aliveActiveIds(state)
    val mafiaIds = state.privatePerPlayer
        .filterValues { it.role == Role.Mafia }
        .keys
    return aliveIds
        .filter { id -> id != voterId && (mafiaCanTargetMafia || id !in mafiaIds) }
        .map { id -> PickableTarget(id = id, name = displayNameOf(state, id) ?: id.raw) }
}

// ==================================================================== Night announcement ==
//
// The Detective's private inspection result is delivered BEFORE the public
// announcement, on its own private handoff segment. Once the detective acks,
// the public NightAnnouncementScreen is shown; tapping Continue submits
// OpenDiscussion, and PartyAwareSession auto-fills AcknowledgeNightAnnouncement
// for every alive player via MafiaReadinessGate.

@Composable
private fun NightAnnouncementSegment(
    state: MafiaState,
    phase: MafiaPhase.NightAnnouncement,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val pendingDetective = state.players.firstOrNull { p ->
        val priv = state.privatePerPlayer[p.id] ?: return@firstOrNull false
        priv.role == Role.Detective &&
            priv.pendingDetectiveResult != null &&
            !priv.detectiveResultAcknowledged
    }
    if (pendingDetective != null) {
        DetectivePrivateResultSegment(
            detective = pendingDetective,
            state = state,
            session = session,
            scope = scope,
            modifier = modifier,
        )
        return
    }

    val announcement = state.public.lastNight
    if (announcement == null || announcement.day != phase.day) {
        MafiaLoadingScreen(modifier)
        return
    }
    val killedName = announcement.killedPlayerId?.let { displayNameOf(state, it) }
    val revealedRole = announcement.killedPlayerId
        ?.let { id -> state.public.roster.firstOrNull { it.playerId == id }?.revealedRole }

    NightAnnouncementScreen(
        day = phase.day,
        killedPlayerName = killedName,
        revealedRole = revealedRole,
        wasSaved = announcement.wasSaved,
        onAcknowledged = {
            scope.launch { session.submit(MafiaAction.OpenDiscussion) }
        },
        modifier = modifier,
    )
}

@Composable
private fun DetectivePrivateResultSegment(
    detective: Player,
    state: MafiaState,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val priv = state.privatePerPlayer[detective.id]
    val result = priv?.pendingDetectiveResult
    if (result == null) {
        MafiaLoadingScreen(modifier)
        return
    }

    var stage by remember(detective.id, result.day) { mutableStateOf(RevealStage.Handoff) }
    val inspectedName = displayNameOf(state, result.target) ?: result.target.raw

    when (stage) {
        RevealStage.Handoff -> MafiaRoleRevealHandoffScreen(
            playerName = detective.displayName,
            onContinue = { stage = RevealStage.Gate },
            modifier = modifier,
        )
        RevealStage.Gate -> MafiaRoleRevealGateScreen(
            playerName = detective.displayName,
            eyebrow = stringResource(Res.string.night_detective_reveal_eyebrow),
            onRevealed = { stage = RevealStage.Reveal },
            modifier = modifier,
        )
        RevealStage.Reveal -> DetectiveResultScreen(
            detectiveName = detective.displayName,
            inspectedName = inspectedName,
            seesAs = result.seesAs,
            onAcknowledged = { stage = RevealStage.Hide },
            modifier = modifier,
        )
        RevealStage.Hide -> MafiaHideAndPassScreen(
            nextPlayerName = null,
            onTap = {
                scope.launch { session.submit(MafiaAction.AcknowledgeDetectiveResult(detective.id)) }
            },
            modifier = modifier,
        )
    }
}

// ========================================================================== Discussion ==

@Composable
private fun DiscussionSegment(
    state: MafiaState,
    phase: MafiaPhase.Discussion,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    isHost: Boolean,
    modifier: Modifier = Modifier,
) {
    val (alive, dead) = state.public.roster
        .filter { it.playerId !in state.public.droppedPlayers }
        .partition { it.alive }

    DiscussionScreen(
        day = phase.day,
        aliveNames = alive.map { it.displayName },
        deadNames = dead.map { it.displayName },
        timerLabel = null,
        isHost = isHost,
        onOpenVote = { scope.launch { session.submit(MafiaAction.OpenVote) } },
        modifier = modifier,
    )
}

// ============================================================================== Voting ==
//
// Open vote per player: each ballot member is handed the device in turn,
// taps through Handoff → Gate → cast/abstain → Hide. After every ballot
// member has cast or abstained, the host submits CloseVote and the reducer
// transitions to VoteAnnouncement (or to a tied-revote round).

private enum class VoteStage { Handoff, Gate, Cast, Hide }

@Composable
private fun VotingSegment(
    state: MafiaState,
    phase: MafiaPhase.Voting,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val vote = state.public.activeVote
    if (vote == null || vote.day != phase.day) {
        MafiaLoadingScreen(modifier)
        return
    }
    val nextVoter = vote.ballot.firstOrNull { id ->
        id !in vote.castSoFar.keys && id !in vote.abstained
    }
    if (nextVoter == null) {
        LaunchedEffect(phase) { session.submit(MafiaAction.CloseVote) }
        MafiaLoadingScreen(modifier)
        return
    }
    val voterName = displayNameOf(state, nextVoter) ?: nextVoter.raw

    var stage by remember(nextVoter) { mutableStateOf(VoteStage.Handoff) }
    // We capture the cast intent and defer submission until Hide so the
    // hide screen actually renders before the next voter is prompted.
    var pendingBallot by remember(nextVoter) { mutableStateOf<PendingBallot?>(null) }

    val nextAfter = vote.ballot.firstOrNull { id ->
        id != nextVoter && id !in vote.castSoFar.keys && id !in vote.abstained
    }

    when (stage) {
        VoteStage.Handoff -> MafiaRoleRevealHandoffScreen(
            playerName = voterName,
            onContinue = { stage = VoteStage.Gate },
            modifier = modifier,
        )
        VoteStage.Gate -> MafiaRoleRevealGateScreen(
            playerName = voterName,
            eyebrow = if (phase.revoteRound > 0) {
                stringResource(Res.string.vote_revote_eyebrow_format, phase.revoteRound)
            } else {
                stringResource(Res.string.vote_eyebrow)
            },
            onRevealed = { stage = VoteStage.Cast },
            modifier = modifier,
        )
        VoteStage.Cast -> VoteCastScreen(
            voterName = voterName,
            candidates = candidatesFor(state, vote.candidates, nextVoter),
            revoteRound = phase.revoteRound,
            onCast = { target ->
                pendingBallot = PendingBallot(target = target, abstain = false)
                stage = VoteStage.Hide
            },
            onAbstain = {
                pendingBallot = PendingBallot(target = null, abstain = true)
                stage = VoteStage.Hide
            },
            modifier = modifier,
        )
        VoteStage.Hide -> MafiaHideAndPassScreen(
            nextPlayerName = nextAfter?.let { displayNameOf(state, it) },
            onTap = {
                val ballot = pendingBallot ?: return@MafiaHideAndPassScreen
                scope.launch {
                    if (ballot.abstain || ballot.target == null) {
                        session.submit(MafiaAction.AbstainVote(nextVoter))
                    } else {
                        session.submit(MafiaAction.CastVote(nextVoter, ballot.target))
                    }
                }
            },
            modifier = modifier,
        )
    }
}

private data class PendingBallot(val target: PlayerId?, val abstain: Boolean)

private fun candidatesFor(
    state: MafiaState,
    candidates: List<PlayerId>,
    voter: PlayerId,
): List<PickableTarget> {
    val allowSelfVote = state.public.settings.allowSelfVote
    return candidates
        .filter { allowSelfVote || it != voter }
        .map { id -> PickableTarget(id = id, name = displayNameOf(state, id) ?: id.raw) }
}

// ===================================================================== Vote announcement ==

@Composable
private fun VoteAnnouncementSegment(
    state: MafiaState,
    phase: MafiaPhase.VoteAnnouncement,
    session: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val announcement = state.public.lastVote
    if (announcement == null || announcement.day != phase.day) {
        MafiaLoadingScreen(modifier)
        return
    }
    val eliminated = announcement.eliminatedPlayerId
    val eliminatedName = eliminated?.let { displayNameOf(state, it) }
    val eliminatedRole = eliminated
        ?.let { id -> state.public.roster.firstOrNull { it.playerId == id }?.revealedRole }
        ?.let { roleDisplayName(it) }
    val outcomeLine = outcomeLine(announcement.outcome)

    VoteAnnouncementScreen(
        day = phase.day,
        tally = announcement.tally,
        nameLookup = { id -> displayNameOf(state, id) ?: id.raw },
        eliminatedName = eliminatedName,
        eliminatedRole = eliminatedRole,
        outcomeLine = outcomeLine,
        onAcknowledged = {
            scope.launch { session.submit(MafiaAction.AdvanceFromVoteAnnouncement) }
        },
        modifier = modifier,
    )
}

@Composable
private fun outcomeLine(
    outcome: com.parlor.games.mafia.domain.state.VoteOutcome,
): String = when (outcome) {
    com.parlor.games.mafia.domain.state.VoteOutcome.Eliminated ->
        stringResource(Res.string.vote_outcome_eliminated)
    com.parlor.games.mafia.domain.state.VoteOutcome.SkippedDueToTie ->
        stringResource(Res.string.vote_outcome_tied)
    com.parlor.games.mafia.domain.state.VoteOutcome.MaxRevotesReached ->
        stringResource(Res.string.vote_outcome_max_revotes)
    com.parlor.games.mafia.domain.state.VoteOutcome.AllAbstained ->
        stringResource(Res.string.vote_outcome_all_abstained)
}

// ============================================================================== Helpers ==

private fun aliveActivePlayers(state: MafiaState): List<Player> {
    val aliveSet = state.public.roster
        .filter { it.alive && it.playerId !in state.public.droppedPlayers }
        .map { it.playerId }
        .toSet()
    return state.players
        .filter { it.id in aliveSet }
        .sortedBy { it.seat }
}

/**
 * Canonical pass-and-play night queue.
 *
 * A player disappears from this list only after the reducer commits their
 * action. UI-local click state is deliberately excluded so cancellation,
 * validation failure, or a closed session cannot silently advance the handoff.
 */
internal fun pendingNightPlayers(
    state: MafiaState,
    phase: MafiaPhase.Night,
): List<Player> {
    val eligible = aliveActivePlayers(state)
    return eligible.filter { player ->
        val private = state.privatePerPlayer[player.id] ?: return@filter true
        val participates = phase.mafiaCoordinationRound < 2 || private.role == Role.Mafia
        participates && !private.nightChoiceSubmitted
    }
}

private fun aliveActiveIds(state: MafiaState): List<PlayerId> =
    aliveActivePlayers(state).map { it.id }

private fun displayNameOf(state: MafiaState, id: PlayerId): String? =
    state.public.roster.firstOrNull { it.playerId == id }?.displayName
        ?: state.players.firstOrNull { it.id == id }?.displayName

private fun finalRoles(state: MafiaState): List<Pair<String, Role>> =
    state.players.mapNotNull { p ->
        val priv = state.privatePerPlayer[p.id] ?: return@mapNotNull null
        p.displayName to priv.role
    }

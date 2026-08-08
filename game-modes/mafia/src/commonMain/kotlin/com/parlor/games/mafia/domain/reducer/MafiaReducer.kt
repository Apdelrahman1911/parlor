package com.parlor.games.mafia.domain.reducer

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.NightResolution
import com.parlor.games.mafia.domain.rules.RoleAssignment
import com.parlor.games.mafia.domain.rules.VoteResolution
import com.parlor.games.mafia.domain.rules.WinCheck
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaSettingsValidation
import com.parlor.games.mafia.domain.state.ActiveVote
import com.parlor.games.mafia.domain.state.DetectiveResult
import com.parlor.games.mafia.domain.state.MafiaCoordinationSnapshot
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.NightAnnouncement
import com.parlor.games.mafia.domain.state.NightResolutionRecord
import com.parlor.games.mafia.domain.state.PublicPlayerSlot
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.VoteAnnouncement
import com.parlor.games.mafia.domain.state.VoteOutcome
import com.parlor.games.mafia.domain.state.VoteRoundRecord
import com.parlor.games.mafia.domain.state.detectiveSeesAs
import com.parlor.games.mafia.domain.state.team

/**
 * Pure reducer for Mafia. Topology-agnostic — Pass-and-Play and Local/P2P
 * both feed the same reducer. Time, randomness arrive via [ReducerContext].
 *
 * Privacy invariants enforced here (the projection policy is the second
 * line of defense — these writes must already keep secrets out of the
 * wrong buckets):
 *   - Mafia coordination snapshot is replicated identically into every
 *     living Mafia member's MafiaPrivate, and NEVER set on Town members.
 *   - Detective inspection results live only in the detective's MafiaPrivate.
 *   - Doctor / Civilian / own night picks live only in the submitter's MafiaPrivate.
 *   - Full role map lives only in MafiaHostOnly.
 *   - PublicPlayerSlot.revealedRole is set only for an enabled death reveal
 *     during play; every role is deliberately revealed in PostGame.
 */
object MafiaReducer : GameReducer<MafiaState, MafiaAction, MafiaEvent>() {

    override fun reduce(
        state: MafiaState,
        action: MafiaAction,
        ctx: ReducerContext,
    ): Reduction<MafiaState, MafiaEvent> = when (action) {
        // Setup
        is MafiaAction.ApplySettings -> applySettings(state, action)
        MafiaAction.StartGame -> startGame(state, ctx)
        MafiaAction.AdvanceFromRoleAssignment -> advanceFromRoleAssignment(state)

        // Acks and self-actor submissions
        is MafiaAction.AcknowledgeRoleViewed -> ackRole(state, action.by)
        is MafiaAction.SubmitMafiaKillVote -> submitMafiaKillVote(state, action.by, action.target)
        is MafiaAction.SubmitDoctorProtect -> submitDoctorProtect(state, action.by, action.target)
        is MafiaAction.SubmitDetectiveInspect -> submitDetectiveInspect(state, action.by, action.target)
        is MafiaAction.SubmitCivilianSuspicion -> submitCivilianSuspicion(state, action.by, action.target)
        is MafiaAction.AcknowledgeNightAnnouncement -> ackNight(state, action.by)
        is MafiaAction.AcknowledgeDetectiveResult -> ackDetectiveResult(state, action.by)
        is MafiaAction.CastVote -> castVote(state, action.by, action.target)
        is MafiaAction.AbstainVote -> abstainVote(state, action.by)
        is MafiaAction.AcknowledgeVoteAnnouncement -> ackVote(state, action.by)
        is MafiaAction.AcknowledgePostGame -> Reduction(state)

        // Host progression
        MafiaAction.ResolveNight -> resolveNight(state, ctx)
        MafiaAction.OpenDiscussion -> openDiscussion(state)
        MafiaAction.OpenVote -> openVote(state)
        MafiaAction.CloseVote -> closeVote(state)
        MafiaAction.AdvanceFromVoteAnnouncement -> advanceFromVoteAnnouncement(state)
        MafiaAction.EndGame -> endGame(state)

        // Connection chrome
        is MafiaAction.MarkPlayerDisconnected -> markDisconnected(state, action.playerId)
        is MafiaAction.MarkPlayerReconnected -> markReconnected(state, action.playerId)
        is MafiaAction.ContinueWithoutPlayer -> continueWithout(state, action.playerId)
        is MafiaAction.ReadmitPlayer -> readmit(state, action.playerId)
    }

    // ============================================================ Setup ==

    private fun applySettings(
        state: MafiaState,
        action: MafiaAction.ApplySettings,
    ): Reduction<MafiaState, MafiaEvent> {
        if (state.phase != MafiaPhase.Setup) return Reduction(state)
        val playerCount = state.players.size
        if (action.settings.validate(playerCount) !is MafiaSettingsValidation.Valid) {
            return Reduction(state)
        }
        return Reduction(
            state.copy(public = state.public.copy(settings = action.settings)),
            listOf(MafiaEvent.SettingsApplied),
        )
    }

    private fun startGame(
        state: MafiaState,
        ctx: ReducerContext,
    ): Reduction<MafiaState, MafiaEvent> {
        if (state.phase != MafiaPhase.Setup) return Reduction(state)
        if (state.public.droppedPlayers.isNotEmpty()) return Reduction(state)
        val playerCount = state.players.size
        if (state.public.settings.validate(playerCount) !is MafiaSettingsValidation.Valid) {
            return Reduction(state)
        }
        val random = RandomSource.seeded(state.hostOnly.randomSeed)
        val assignment = RoleAssignment.assign(
            players = state.players,
            counts = state.public.settings.roleCounts,
            random = random,
        )
        val privatePerPlayer = state.players.associate { p ->
            val role = assignment.roles.getValue(p.id)
            p.id to MafiaPrivate(
                role = role,
                team = role.team,
                knownTeammates = assignment.knownTeammates.getValue(p.id),
                mafiaCoordination = if (role.team == Team.Mafia) {
                    MafiaCoordinationSnapshot(round = 1)
                } else null,
            )
        }
        val nextPhase = MafiaPhase.RoleAssignment
        val newState = state.copy(
            privatePerPlayer = privatePerPlayer,
            hostOnly = state.hostOnly.copy(fullRoleMap = assignment.roles),
            phase = nextPhase,
        )
        return Reduction(
            newState,
            listOf(
                MafiaEvent.RolesAssigned,
                MafiaEvent.PhaseEntered(nextPhase),
            ),
        )
    }

    private fun advanceFromRoleAssignment(
        state: MafiaState,
    ): Reduction<MafiaState, MafiaEvent> {
        if (state.phase != MafiaPhase.RoleAssignment) return Reduction(state)
        val active = activeRoster(state)
        val allAcked = active.all { state.privatePerPlayer[it]?.roleAcknowledged == true }
        if (!allAcked) return Reduction(state)
        val nextDay = 1
        val nextPhase = MafiaPhase.Night(day = nextDay, mafiaCoordinationRound = 1)
        val cleared = state.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(roleAcknowledged = false)
        }
        return Reduction(
            state.copy(
                privatePerPlayer = cleared,
                phase = nextPhase,
                public = state.public.copy(day = nextDay),
            ),
            listOf(
                MafiaEvent.NightStarted(nextDay),
                MafiaEvent.PhaseEntered(nextPhase),
            ),
        )
    }

    // ============================================================ Acks ==

    private fun ackRole(state: MafiaState, by: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase != MafiaPhase.RoleAssignment) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (by in state.public.droppedPlayers) return Reduction(state)
        if (priv.roleAcknowledged) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(roleAcknowledged = true)),
            ),
        )
    }

    private fun ackNight(state: MafiaState, by: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.NightAnnouncement) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (by in state.public.droppedPlayers) return Reduction(state)
        if (priv.nightAcknowledged) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(nightAcknowledged = true)),
            ),
        )
    }

    private fun ackDetectiveResult(state: MafiaState, by: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.Night) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Detective) return Reduction(state)
        if (!isActiveAlive(state, by)) return Reduction(state)
        if (priv.pendingDetectiveResult == null) return Reduction(state)
        if (priv.detectiveResultAcknowledged) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(detectiveResultAcknowledged = true)),
            ),
        )
    }

    private fun ackVote(state: MafiaState, by: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.VoteAnnouncement) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (by in state.public.droppedPlayers) return Reduction(state)
        if (priv.voteAcknowledged) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(voteAcknowledged = true)),
            ),
        )
    }

    // ============================================================ Night submissions ==

    private fun submitMafiaKillVote(
        state: MafiaState,
        by: PlayerId,
        target: PlayerId?,
    ): Reduction<MafiaState, MafiaEvent> {
        val night = state.phase as? MafiaPhase.Night ?: return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Mafia) return Reduction(state)
        if (!isActiveAlive(state, by)) return Reduction(state)
        if (priv.nightChoiceSubmitted) return Reduction(state)
        if (target != null && !isActiveAlive(state, target)) return Reduction(state)
        if (target == by) return Reduction(state)
        if (target != null && !state.public.settings.mafiaCanTargetMafia) {
            // Can't target another Mafia (unless allowed by settings).
            if (state.privatePerPlayer[target]?.role == Role.Mafia) return Reduction(state)
        }
        // Update the coordinator snapshot — replicated across every living Mafia.
        val coord = priv.mafiaCoordination ?: MafiaCoordinationSnapshot(round = night.mafiaCoordinationRound)
        val submissionsForTarget = if (target == null) coord.submissions - by else coord.submissions + (by to target)
        val newSnapshot = coord.copy(round = night.mafiaCoordinationRound, submissions = submissionsForTarget)
        val updated = updateMafiaCoordination(state, newSnapshot)
        // Also stash the submitter's pending choice.
        val withChoice = updated.privatePerPlayer + (by to updated.privatePerPlayer.getValue(by).copy(
            pendingNightChoice = target,
            nightChoiceSubmitted = true,
        ))
        return Reduction(updated.copy(privatePerPlayer = withChoice))
    }

    private fun submitDoctorProtect(
        state: MafiaState,
        by: PlayerId,
        target: PlayerId?,
    ): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.Night) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Doctor) return Reduction(state)
        if (!isActiveAlive(state, by)) return Reduction(state)
        if (priv.nightChoiceSubmitted) return Reduction(state)
        if (target != null && !isActiveAlive(state, target)) return Reduction(state)
        if (target == by && !state.public.settings.doctorCanSelfHeal) return Reduction(state)
        if (
            target != null &&
            !state.public.settings.doctorCanProtectSamePlayerConsecutively &&
            target == priv.previousDoctorProtect
        ) {
            return Reduction(state)
        }
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer +
                    (by to priv.copy(pendingNightChoice = target, nightChoiceSubmitted = true)),
            ),
        )
    }

    private fun submitDetectiveInspect(
        state: MafiaState,
        by: PlayerId,
        target: PlayerId?,
    ): Reduction<MafiaState, MafiaEvent> {
        val night = state.phase as? MafiaPhase.Night ?: return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Detective) return Reduction(state)
        if (!isActiveAlive(state, by)) return Reduction(state)
        if (priv.nightChoiceSubmitted) return Reduction(state)
        if (target != null && !isActiveAlive(state, target)) return Reduction(state)
        if (target == by && !state.public.settings.detectiveCanInspectSelf) return Reduction(state)
        val result = target?.let { inspected ->
            state.hostOnly.fullRoleMap[inspected]?.let { role ->
                DetectiveResult(
                    day = night.day,
                    target = inspected,
                    seesAs = role.detectiveSeesAs(),
                )
            }
        }
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer +
                    (
                        by to priv.copy(
                            pendingNightChoice = target,
                            pendingDetectiveResult = result,
                            detectiveResultAcknowledged = result == null,
                            nightChoiceSubmitted = true,
                        )
                    ),
            ),
        )
    }

    private fun submitCivilianSuspicion(
        state: MafiaState,
        by: PlayerId,
        target: PlayerId?,
    ): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.Night) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Civilian) return Reduction(state)
        if (!isActiveAlive(state, by)) return Reduction(state)
        if (priv.nightChoiceSubmitted) return Reduction(state)
        if (target != null && (!isActiveAlive(state, target) || target == by)) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer +
                    (by to priv.copy(lastSuspicion = target, nightChoiceSubmitted = true)),
            ),
        )
    }

    // ============================================================ Night resolution ==

    private fun resolveNight(
        state: MafiaState,
        @Suppress("UNUSED_PARAMETER") ctx: ReducerContext,
    ): Reduction<MafiaState, MafiaEvent> {
        val night = state.phase as? MafiaPhase.Night ?: return Reduction(state)
        if (!isNightReadyToResolve(state)) return Reduction(state)

        // Tally the current coordination round's mafia kill votes.
        val mafiaSubmissions = aliveMafiaSubmissions(state)
        val tally = mafiaSubmissions.values.filterNotNull().groupingBy { it }.eachCount()

        // If round 1 is tied AND REVOTE is configured, route to round 2 instead of resolving.
        if (night.mafiaCoordinationRound == 1
            && state.public.settings.mafiaKillTieBehavior == MafiaKillTie.REVOTE
            && tally.isNotEmpty()
        ) {
            val maxCount = tally.values.max()
            val top = tally.filterValues { it == maxCount }
            if (top.size > 1) {
                return openMafiaCoordinationRevote(state, prevTally = tally)
            }
        }

        // Otherwise: actually resolve the night.
        val doctorPick = aliveDoctorChoice(state)
        val detectivePick = aliveDetectiveChoice(state)
        val doctorPrev = state.privatePerPlayer.values
            .firstOrNull { it.role == Role.Doctor }
            ?.previousDoctorProtect

        val resolution = NightResolution.resolve(
            inputs = NightResolution.Inputs(
                mafiaTargetTally = tally,
                mafiaCoordinationRound = night.mafiaCoordinationRound,
                doctorTarget = doctorPick,
                doctorProtectedPreviousNight = doctorPrev,
                detectiveTarget = detectivePick,
                rolesByPlayer = state.hostOnly.fullRoleMap,
                alive = activeAliveSet(state),
            ),
            settings = state.public.settings,
            // Deterministic per-night RNG derived from the seeded hostOnly seed
            // (NOT ctx.random, which is a shared, randomly-seeded, advancing
            // app-wide stream). Using ctx.random made resolveNight non-pure: the
            // RANDOM_TIED / revote tie-break drew from process entropy, so the
            // same (state, action) produced different kills and a restored
            // snapshot could diverge. See PROBLEMS_PARLOR.md → mafia-001.
            random = RandomSource.seeded(
                state.hostOnly.randomSeed xor
                    (night.day.toLong() shl 16) xor
                    night.mafiaCoordinationRound.toLong(),
            ),
        )

        val killed = resolution.killed
        // Apply death to public roster.
        val newRoster = state.public.roster.map { slot ->
            if (slot.playerId == killed) {
                slot.copy(
                    alive = false,
                    revealedRole = if (state.public.settings.revealRoleOnDeath) {
                        state.hostOnly.fullRoleMap[slot.playerId]
                    } else null,
                )
            } else slot
        }
        val announcement = NightAnnouncement(
            day = night.day,
            killedPlayerId = killed,
            wasSaved = resolution.wasSaved,
        )

        // The Detective result is derived and stored at submission time, before
        // the night can resolve. That guarantees the Detective can view it even
        // when they are tonight's victim. Resolution only records the outcome;
        // it must not re-open or overwrite an already acknowledged result.
        val detectiveResult = resolution.detectiveResult
        val detectivePlayer = detectiveByPlayer(state)

        // Reset all pending night choices + ack flags; reset mafia coordination snapshot.
        // Snapshot is rebuilt at the start of the next Night phase by updateMafiaCoordination,
        // so we always clear it here regardless of team — Town's was already null.
        val privatesReset = state.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(
                pendingNightChoice = null,
                nightChoiceSubmitted = false,
                nightAcknowledged = false,
                lastSuspicion = priv.lastSuspicion,
                previousDoctorProtect = if (priv.role == Role.Doctor) {
                    resolution.effectiveDoctorTarget
                } else {
                    priv.previousDoctorProtect
                },
                mafiaCoordination = null,
            )
        }

        // Win-check off the new alive set. A dropped player (ContinueWithoutPlayer)
        // has left the game and must NOT count toward parity — otherwise a
        // dropped-but-not-killed Mafia keeps tipping the Mafia-vs-Town math.
        // See PROBLEMS_PARLOR.md → mafia-002.
        val newAlive = activeAliveSet(state) - listOfNotNull(killed).toSet()
        val winner = WinCheck.evaluate(newAlive, state.hostOnly.fullRoleMap)

        val nightRecord = NightResolutionRecord(
            day = night.day,
            mafiaTarget = resolution.mafiaTarget,
            mafiaTargetTied = resolution.mafiaTargetTied,
            doctorProtect = resolution.effectiveDoctorTarget,
            detectiveInspect = detectivePick,
            detectiveResult = resolution.detectiveResult?.second,
            killedPlayerId = killed,
        )

        val nextPhase: MafiaPhase = if (winner != null) {
            MafiaPhase.PostGame
        } else {
            MafiaPhase.NightAnnouncement(night.day)
        }

        val resolvedState = state.copy(
            privatePerPlayer = privatesReset,
            public = state.public.copy(
                roster = newRoster,
                lastNight = announcement,
                winner = winner,
            ),
            hostOnly = state.hostOnly.copy(
                nightLog = state.hostOnly.nightLog + nightRecord,
            ),
            phase = nextPhase,
        )
        val newState = if (winner != null) {
            finishGame(resolvedState, winner)
        } else {
            resolvedState
        }

        val events = buildList {
            add(MafiaEvent.NightResolved(night.day, killed, resolution.wasSaved))
            if (detectivePlayer != null && detectiveResult != null) {
                add(MafiaEvent.DetectiveInspectionRecorded(night.day, detectivePlayer, detectiveResult.first))
            }
            if (winner != null) {
                add(MafiaEvent.WinnerDecided(winner))
            }
            add(MafiaEvent.PhaseEntered(nextPhase))
        }
        return Reduction(newState, events)
    }

    /**
     * Open the Mafia-only coordination revote. The previous round's tally is
     * shown anonymized to every Mafia member; submissions for round 2 are
     * fresh.
     */
    private fun openMafiaCoordinationRevote(
        state: MafiaState,
        prevTally: Map<PlayerId, Int>,
    ): Reduction<MafiaState, MafiaEvent> {
        val night = state.phase as MafiaPhase.Night
        val newRound = night.mafiaCoordinationRound + 1
        val snapshot = MafiaCoordinationSnapshot(
            round = newRound,
            submissions = emptyMap(),
            previousRoundTally = prevTally,
        )
        val withSnapshot = updateMafiaCoordination(state, snapshot)
        // Clear pending kill-vote choices on Mafia members so the UI resets.
        val cleared = withSnapshot.privatePerPlayer.mapValues { (_, priv) ->
            // Mafia re-submit in round 2, so clear both their pending pick and
            // the submitted flag so the night-action UI reopens for them.
            if (priv.team == Team.Mafia) priv.copy(pendingNightChoice = null, nightChoiceSubmitted = false) else priv
        }
        val nextPhase = night.copy(mafiaCoordinationRound = newRound)
        return Reduction(
            withSnapshot.copy(
                privatePerPlayer = cleared,
                phase = nextPhase,
            ),
            listOf(
                MafiaEvent.MafiaCoordinationRevoteOpened(night.day),
                MafiaEvent.PhaseEntered(nextPhase),
            ),
        )
    }

    private fun openDiscussion(state: MafiaState): Reduction<MafiaState, MafiaEvent> {
        val ann = state.phase as? MafiaPhase.NightAnnouncement ?: return Reduction(state)
        val active = activeRoster(state).filter { isAlive(state, it) }
        val acked = active.all { state.privatePerPlayer[it]?.nightAcknowledged == true }
        if (!acked) return Reduction(state)
        val nextPhase = MafiaPhase.Discussion(ann.day)
        val cleared = state.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(nightAcknowledged = false)
        }
        return Reduction(
            state.copy(phase = nextPhase, privatePerPlayer = cleared),
            listOf(MafiaEvent.PhaseEntered(nextPhase)),
        )
    }

    // ============================================================ Voting ==

    private fun openVote(state: MafiaState): Reduction<MafiaState, MafiaEvent> {
        val day = (state.phase as? MafiaPhase.Discussion)?.day ?: return Reduction(state)
        val alive = state.public.roster.filter { it.alive }.map { it.playerId }
        val active = alive - state.public.droppedPlayers
        // Candidates default to everyone active and alive. Tied revotes are
        // created directly by [applyVoteTied], never by reopening this phase.
        // allowSelfVote affects whether `target == voter` is a valid CastVote in
        // [castVote] below, not who appears on the candidate list. Candidates ==
        // active living players for all settings.
        val ballot = active
        val candidates = active
        val vote = ActiveVote(
            day = day,
            revoteRound = 0,
            candidates = candidates,
            ballot = ballot,
        )
        val nextPhase = MafiaPhase.Voting(day = day, revoteRound = 0)
        return Reduction(
            state.copy(
                phase = nextPhase,
                public = state.public.copy(activeVote = vote),
            ),
            listOf(MafiaEvent.VoteOpened(day, 0), MafiaEvent.PhaseEntered(nextPhase)),
        )
    }

    private fun castVote(state: MafiaState, by: PlayerId, target: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.Voting) return Reduction(state)
        val vote = state.public.activeVote ?: return Reduction(state)
        if (by !in vote.ballot) return Reduction(state)
        if (target !in vote.candidates) return Reduction(state)
        if (target == by && !state.public.settings.allowSelfVote) return Reduction(state)
        if (by in vote.castSoFar || by in vote.abstained) return Reduction(state)
        val updated = vote.copy(
            castSoFar = vote.castSoFar + (by to target),
            abstained = vote.abstained - by,
        )
        return Reduction(
            state.copy(public = state.public.copy(activeVote = updated)),
            listOf(MafiaEvent.VoteCast(by, target)),
        )
    }

    private fun abstainVote(state: MafiaState, by: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.Voting) return Reduction(state)
        val vote = state.public.activeVote ?: return Reduction(state)
        if (by !in vote.ballot) return Reduction(state)
        if (by in vote.castSoFar || by in vote.abstained) return Reduction(state)
        val updated = vote.copy(
            castSoFar = vote.castSoFar - by,
            abstained = vote.abstained + by,
        )
        return Reduction(
            state.copy(public = state.public.copy(activeVote = updated)),
            listOf(MafiaEvent.VoteAbstained(by)),
        )
    }

    private fun closeVote(state: MafiaState): Reduction<MafiaState, MafiaEvent> {
        val voting = state.phase as? MafiaPhase.Voting ?: return Reduction(state)
        val vote = state.public.activeVote ?: return Reduction(state)
        if (vote.ballot.isEmpty()) return Reduction(state)
        val submitted = vote.castSoFar.keys + vote.abstained
        if (!vote.ballot.all { it in submitted }) return Reduction(state)

        val outcome = VoteResolution.resolve(
            inputs = VoteResolution.Inputs(
                casts = vote.castSoFar,
                abstained = vote.abstained,
                ballot = vote.ballot,
                candidates = vote.candidates,
                revoteRound = vote.revoteRound,
            ),
            settings = state.public.settings,
        )

        val day = voting.day
        return when (outcome) {
            is VoteResolution.Outcome.Resolved -> applyVoteResolved(state, day, vote.revoteRound, outcome)
            is VoteResolution.Outcome.Tied -> applyVoteTied(state, day, vote, outcome)
            is VoteResolution.Outcome.Skipped -> applyVoteSkipped(state, day, vote.revoteRound, outcome)
        }
    }

    private fun applyVoteResolved(
        state: MafiaState,
        day: Int,
        revoteRound: Int,
        outcome: VoteResolution.Outcome.Resolved,
    ): Reduction<MafiaState, MafiaEvent> {
        val eliminated = outcome.eliminated
        val newRoster = state.public.roster.map { slot ->
            if (slot.playerId == eliminated) {
                slot.copy(
                    alive = false,
                    revealedRole = if (state.public.settings.revealRoleOnDeath) {
                        state.hostOnly.fullRoleMap[slot.playerId]
                    } else null,
                )
            } else slot
        }
        val announcement = VoteAnnouncement(
            day = day,
            tally = outcome.tally,
            eliminatedPlayerId = eliminated,
            outcome = VoteOutcome.Eliminated,
        )
        // Dropped players don't count toward win parity (see mafia-002).
        val newAlive = activeAliveSet(state) - eliminated
        val winner = WinCheck.evaluate(newAlive, state.hostOnly.fullRoleMap)
        val nextPhase = if (winner != null) MafiaPhase.PostGame else MafiaPhase.VoteAnnouncement(day)
        val privatesCleared = state.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(voteAcknowledged = false)
        }
        val resolvedState = state.copy(
            phase = nextPhase,
            public = state.public.copy(
                roster = newRoster,
                lastVote = announcement,
                activeVote = null,
                winner = winner,
            ),
            hostOnly = state.hostOnly.copy(
                voteLog = state.hostOnly.voteLog + VoteRoundRecord(
                    day = day,
                    revoteRound = revoteRound,
                    tally = outcome.tally,
                    eliminatedPlayerId = eliminated,
                ),
            ),
            privatePerPlayer = privatesCleared,
        )
        val newState = if (winner != null) finishGame(resolvedState, winner) else resolvedState
        return Reduction(
            newState,
            buildList {
                add(MafiaEvent.VoteResolved(day, eliminated))
                if (winner != null) add(MafiaEvent.WinnerDecided(winner))
                add(MafiaEvent.PhaseEntered(nextPhase))
            },
        )
    }

    private fun applyVoteTied(
        state: MafiaState,
        day: Int,
        prev: ActiveVote,
        outcome: VoteResolution.Outcome.Tied,
    ): Reduction<MafiaState, MafiaEvent> {
        val nextRound = prev.revoteRound + 1
        val newVote = ActiveVote(
            day = day,
            revoteRound = nextRound,
            candidates = outcome.nextRoundCandidates,
            ballot = prev.ballot,
        )
        val nextPhase = MafiaPhase.Voting(day = day, revoteRound = nextRound)
        return Reduction(
            state.copy(
                phase = nextPhase,
                public = state.public.copy(activeVote = newVote),
            ),
            listOf(
                MafiaEvent.VoteTied(day, outcome.tied),
                MafiaEvent.VoteOpened(day, nextRound),
                MafiaEvent.PhaseEntered(nextPhase),
            ),
        )
    }

    private fun applyVoteSkipped(
        state: MafiaState,
        day: Int,
        revoteRound: Int,
        outcome: VoteResolution.Outcome.Skipped,
    ): Reduction<MafiaState, MafiaEvent> {
        val announcement = VoteAnnouncement(
            day = day,
            tally = outcome.tally,
            eliminatedPlayerId = null,
            outcome = outcome.reason,
        )
        val nextPhase = MafiaPhase.VoteAnnouncement(day)
        val privatesCleared = state.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(voteAcknowledged = false)
        }
        return Reduction(
            state.copy(
                phase = nextPhase,
                public = state.public.copy(
                    lastVote = announcement,
                    activeVote = null,
                ),
                hostOnly = state.hostOnly.copy(
                    voteLog = state.hostOnly.voteLog + VoteRoundRecord(
                        day = day,
                        revoteRound = revoteRound,
                        tally = outcome.tally,
                        eliminatedPlayerId = null,
                    ),
                ),
                privatePerPlayer = privatesCleared,
            ),
            listOf(MafiaEvent.VoteResolved(day, null), MafiaEvent.PhaseEntered(nextPhase)),
        )
    }

    private fun advanceFromVoteAnnouncement(state: MafiaState): Reduction<MafiaState, MafiaEvent> {
        val ann = state.phase as? MafiaPhase.VoteAnnouncement ?: return Reduction(state)
        if (state.public.winner != null) {
            return Reduction(
                finishGame(state, state.public.winner),
                listOf(MafiaEvent.PhaseEntered(MafiaPhase.PostGame)),
            )
        }
        val active = activeRoster(state).filter { isAlive(state, it) }
        val acked = active.all { state.privatePerPlayer[it]?.voteAcknowledged == true }
        if (!acked) return Reduction(state)
        val nextDay = ann.day + 1
        // Re-initialise Mafia coordination snapshot for the new night.
        val withSnapshot = updateMafiaCoordination(
            state,
            MafiaCoordinationSnapshot(round = 1),
        )
        val cleared = withSnapshot.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(
                voteAcknowledged = false,
                pendingDetectiveResult = null,
                detectiveResultAcknowledged = false,
            )
        }
        val nextPhase = MafiaPhase.Night(day = nextDay, mafiaCoordinationRound = 1)
        return Reduction(
            withSnapshot.copy(
                phase = nextPhase,
                public = withSnapshot.public.copy(day = nextDay),
                privatePerPlayer = cleared,
            ),
            listOf(MafiaEvent.NightStarted(nextDay), MafiaEvent.PhaseEntered(nextPhase)),
        )
    }

    private fun endGame(state: MafiaState): Reduction<MafiaState, MafiaEvent> {
        if (state.phase == MafiaPhase.PostGame) return Reduction(state)
        val winner = state.public.winner ?: evaluateCurrentWinner(state)
        return Reduction(
            finishGame(state, winner),
            buildList {
                add(MafiaEvent.GameEnded)
                if (winner != null && state.public.winner == null) {
                    add(MafiaEvent.WinnerDecided(winner))
                }
                add(MafiaEvent.PhaseEntered(MafiaPhase.PostGame))
            },
        )
    }

    // ============================================================ Connection chrome ==

    private fun markDisconnected(state: MafiaState, id: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (id !in state.players.map { it.id }) return Reduction(state)
        if (id in state.public.disconnectedPlayers) return Reduction(state)
        return Reduction(
            state.copy(public = state.public.copy(
                disconnectedPlayers = state.public.disconnectedPlayers + id,
            )),
        )
    }

    private fun markReconnected(state: MafiaState, id: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (id !in state.public.disconnectedPlayers) return Reduction(state)
        return Reduction(
            state.copy(public = state.public.copy(
                disconnectedPlayers = state.public.disconnectedPlayers - id,
            )),
        )
    }

    private fun continueWithout(state: MafiaState, id: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (state.phase == MafiaPhase.PostGame) return Reduction(state)
        if (id !in state.players.map { it.id }) return Reduction(state)
        if (id in state.public.droppedPlayers) return Reduction(state)
        val activeVote = state.public.activeVote?.let { vote ->
            vote.copy(
                candidates = vote.candidates - id,
                ballot = vote.ballot - id,
                castSoFar = vote.castSoFar - id,
                abstained = vote.abstained - id,
            )
        }
        val coordinationCleaned = state.privatePerPlayer.mapValues { (playerId, private) ->
            val cleanedSnapshot = private.mafiaCoordination?.copy(
                submissions = private.mafiaCoordination.submissions
                    .filterKeys { it != id }
                    .filterValues { it != id },
            )
            if (playerId == id) {
                private.copy(
                    pendingNightChoice = null,
                    nightChoiceSubmitted = false,
                    mafiaCoordination = cleanedSnapshot,
                )
            } else {
                private.copy(mafiaCoordination = cleanedSnapshot)
            }
        }
        val dropped = state.copy(
            privatePerPlayer = coordinationCleaned,
            public = state.public.copy(
                droppedPlayers = state.public.droppedPlayers + id,
                disconnectedPlayers = state.public.disconnectedPlayers - id,
                activeVote = activeVote,
            ),
        )
        if (state.phase == MafiaPhase.Setup) return Reduction(dropped)

        // Session orchestration owns the 120-second grace period. Once it
        // dispatches ContinueWithoutPlayer during an active game, Mafia cannot
        // preserve hidden-role fairness with an absent seat, so the game ends.
        val winner = evaluateCurrentWinner(dropped)
        return Reduction(
            finishGame(dropped, winner),
            buildList {
                add(MafiaEvent.GameEnded)
                if (winner != null && state.public.winner == null) {
                    add(MafiaEvent.WinnerDecided(winner))
                }
                add(MafiaEvent.PhaseEntered(MafiaPhase.PostGame))
            },
        )
    }

    private fun readmit(state: MafiaState, id: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (id !in state.public.droppedPlayers) return Reduction(state)
        // A dropped Setup seat must be restored before StartGame can proceed.
        val canReadmit = state.phase == MafiaPhase.Setup
        if (!canReadmit) return Reduction(state)
        return Reduction(
            state.copy(public = state.public.copy(
                droppedPlayers = state.public.droppedPlayers - id,
            )),
        )
    }

    // ============================================================ Helpers ==

    private fun isAlive(state: MafiaState, id: PlayerId): Boolean =
        state.public.roster.firstOrNull { it.playerId == id }?.alive == true

    private fun aliveSet(state: MafiaState): Set<PlayerId> =
        state.public.roster.filter { it.alive }.map { it.playerId }.toSet()

    private fun activeAliveSet(state: MafiaState): Set<PlayerId> =
        aliveSet(state) - state.public.droppedPlayers

    private fun activeRoster(state: MafiaState): List<PlayerId> =
        state.players.map { it.id }.filterNot { it in state.public.droppedPlayers }

    // A dropped player (ContinueWithoutPlayer) has left the game and must not
    // still cast a night action, even if their roster slot is still `alive`.
    // See PROBLEMS_PARLOR.md → mafia-002.
    private fun isActiveAlive(state: MafiaState, id: PlayerId): Boolean =
        isAlive(state, id) && id !in state.public.droppedPlayers

    /**
     * Reducer-owned night close gate. UI readiness is advisory; a duplicated,
     * stale, or malicious host command still cannot resolve until every active
     * living seat has submitted exactly once and any Detective result has been
     * viewed. The round-two Mafia revote clears only Mafia submission flags, so
     * this same predicate remains valid for both rounds.
     */
    private fun isNightReadyToResolve(state: MafiaState): Boolean {
        val activeAlive = activeRoster(state).filter { isAlive(state, it) }
        if (activeAlive.isEmpty()) return false
        return activeAlive.all { id ->
            val private = state.privatePerPlayer[id] ?: return@all false
            private.nightChoiceSubmitted &&
                (private.pendingDetectiveResult == null || private.detectiveResultAcknowledged)
        }
    }

    private fun aliveMafiaSubmissions(state: MafiaState): Map<PlayerId, PlayerId?> =
        state.privatePerPlayer
            .filter { (id, priv) -> priv.role == Role.Mafia && isActiveAlive(state, id) }
            .mapValues { (_, priv) -> priv.pendingNightChoice }

    private fun aliveDoctorChoice(state: MafiaState): PlayerId? =
        state.privatePerPlayer.entries.firstOrNull { (id, priv) ->
            priv.role == Role.Doctor && isActiveAlive(state, id)
        }?.value?.pendingNightChoice

    private fun aliveDetectiveChoice(state: MafiaState): PlayerId? =
        state.privatePerPlayer.entries.firstOrNull { (id, priv) ->
            priv.role == Role.Detective && isActiveAlive(state, id)
        }?.value?.pendingNightChoice

    private fun detectiveByPlayer(state: MafiaState): PlayerId? =
        state.privatePerPlayer.entries.firstOrNull { (id, priv) ->
            priv.role == Role.Detective && isActiveAlive(state, id)
        }?.key

    /**
     * Evaluate a winner only when a complete role map exists. In Setup (or a
     * malformed/restored state) treating an empty map as "zero Mafia" would
     * incorrectly award Town an early host-ended game.
     */
    private fun evaluateCurrentWinner(state: MafiaState): Team? {
        val alive = activeAliveSet(state)
        if (alive.isEmpty() || !state.hostOnly.fullRoleMap.keys.containsAll(alive)) return null
        return WinCheck.evaluate(alive, state.hostOnly.fullRoleMap)
    }

    /**
     * Terminal-state invariant: every role becomes public at game end,
     * independent of revealRoleOnDeath. This lets every peer render the same
     * complete post-game result without receiving host-only/private buckets.
     */
    private fun finishGame(state: MafiaState, winner: Team?): MafiaState =
        state.copy(
            phase = MafiaPhase.PostGame,
            public = state.public.copy(
                winner = winner,
                activeVote = null,
                roster = state.public.roster.map { slot ->
                    slot.copy(
                        revealedRole = state.hostOnly.fullRoleMap[slot.playerId]
                            ?: slot.revealedRole,
                    )
                },
            ),
        )

    /**
     * Replicate the Mafia coordination snapshot into every living Mafia
     * member's MafiaPrivate. Non-Mafia members get `null`. This is the
     * critical invariant: snapshot lives in PrivatePerPlayer, never in
     * MafiaPublic, and the standard `toPlayer(id)` projection delivers
     * it to the right viewers automatically.
     */
    private fun updateMafiaCoordination(
        state: MafiaState,
        snapshot: MafiaCoordinationSnapshot,
    ): MafiaState {
        val updated = state.privatePerPlayer.mapValues { (id, priv) ->
            if (priv.team == Team.Mafia && isActiveAlive(state, id)) {
                priv.copy(mafiaCoordination = snapshot)
            } else {
                priv.copy(mafiaCoordination = null)
            }
        }
        return state.copy(privatePerPlayer = updated)
    }
}

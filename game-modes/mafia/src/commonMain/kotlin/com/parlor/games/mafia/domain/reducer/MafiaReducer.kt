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
 *   - PublicPlayerSlot.revealedRole is set only when the player dies AND
 *     settings.revealRoleOnDeath is true.
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
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Detective) return Reduction(state)
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
        if (!isAlive(state, by)) return Reduction(state)
        if (target != null && !isAlive(state, target)) return Reduction(state)
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
        if (!isAlive(state, by)) return Reduction(state)
        if (target != null && !isAlive(state, target)) return Reduction(state)
        if (target == by && !state.public.settings.doctorCanSelfHeal) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(pendingNightChoice = target)),
            ),
        )
    }

    private fun submitDetectiveInspect(
        state: MafiaState,
        by: PlayerId,
        target: PlayerId?,
    ): Reduction<MafiaState, MafiaEvent> {
        if (state.phase !is MafiaPhase.Night) return Reduction(state)
        val priv = state.privatePerPlayer[by] ?: return Reduction(state)
        if (priv.role != Role.Detective) return Reduction(state)
        if (!isAlive(state, by)) return Reduction(state)
        if (target != null && !isAlive(state, target)) return Reduction(state)
        if (target == by && !state.public.settings.detectiveCanInspectSelf) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(pendingNightChoice = target)),
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
        if (!isAlive(state, by)) return Reduction(state)
        return Reduction(
            state.copy(
                privatePerPlayer = state.privatePerPlayer + (by to priv.copy(lastSuspicion = target)),
            ),
        )
    }

    // ============================================================ Night resolution ==

    private fun resolveNight(
        state: MafiaState,
        ctx: ReducerContext,
    ): Reduction<MafiaState, MafiaEvent> {
        val night = state.phase as? MafiaPhase.Night ?: return Reduction(state)

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
        val doctorPrev = previousNightDoctorProtect(state)

        val resolution = NightResolution.resolve(
            inputs = NightResolution.Inputs(
                mafiaTargetTally = tally,
                mafiaCoordinationRound = night.mafiaCoordinationRound,
                doctorTarget = doctorPick,
                doctorProtectedPreviousNight = doctorPrev,
                detectiveTarget = detectivePick,
                rolesByPlayer = state.hostOnly.fullRoleMap,
                alive = aliveSet(state),
            ),
            settings = state.public.settings,
            random = ctx.random,
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

        // Detective gets their private result.
        val detectiveResult = resolution.detectiveResult
        val detectivePlayer = detectiveByPlayer(state)
        val privatesAfterDetective = if (detectivePlayer != null && detectiveResult != null) {
            val priv = state.privatePerPlayer.getValue(detectivePlayer)
            state.privatePerPlayer + (detectivePlayer to priv.copy(
                pendingDetectiveResult = DetectiveResult(
                    day = night.day,
                    target = detectiveResult.first,
                    seesAs = detectiveResult.second,
                ),
                detectiveResultAcknowledged = false,
            ))
        } else state.privatePerPlayer

        // Reset all pending night choices + ack flags; reset mafia coordination snapshot.
        val privatesReset = privatesAfterDetective.mapValues { (_, priv) ->
            priv.copy(
                pendingNightChoice = null,
                nightAcknowledged = false,
                lastSuspicion = priv.lastSuspicion, // keep
                mafiaCoordination = if (priv.team == Team.Mafia && isAlive(state, priv.role, killed)) {
                    null // reset; will be re-initialised on next Night
                } else null,
            )
        }

        // Win-check off the new alive set.
        val newAlive = aliveSet(state) - listOfNotNull(killed).toSet()
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

        val newState = state.copy(
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
            if (priv.team == Team.Mafia) priv.copy(pendingNightChoice = null) else priv
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
        val day = when (val p = state.phase) {
            is MafiaPhase.Discussion -> p.day
            is MafiaPhase.VoteAnnouncement -> p.day // revote case
            else -> return Reduction(state)
        }
        val alive = state.public.roster.filter { it.alive }.map { it.playerId }
        val active = alive - state.public.droppedPlayers
        // Candidates default to everyone alive. On a revote opened from VoteAnnouncement
        // with a tied outcome, the host bridge will have set state.public.activeVote
        // candidates already — but typically the reducer does it here for the first
        // vote of the day.
        val ballot = active
        val candidates = if (state.public.settings.allowSelfVote) active else active
        // Note: allowSelfVote affects whether `target == voter` is a valid CastVote,
        // not who appears on the candidate list. We keep candidates == active.
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
        val voting = state.phase as? MafiaPhase.Voting ?: return Reduction(state)
        val vote = state.public.activeVote ?: return Reduction(state)
        if (by !in vote.ballot) return Reduction(state)
        if (target !in vote.candidates) return Reduction(state)
        if (target == by && !state.public.settings.allowSelfVote) return Reduction(state)
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
            is VoteResolution.Outcome.Resolved -> applyVoteResolved(state, day, outcome)
            is VoteResolution.Outcome.Tied -> applyVoteTied(state, day, vote, outcome)
            is VoteResolution.Outcome.Skipped -> applyVoteSkipped(state, day, outcome)
        }
    }

    private fun applyVoteResolved(
        state: MafiaState,
        day: Int,
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
        val newAlive = aliveSet(state) - eliminated
        val winner = WinCheck.evaluate(newAlive, state.hostOnly.fullRoleMap)
        val nextPhase = if (winner != null) MafiaPhase.PostGame else MafiaPhase.VoteAnnouncement(day)
        val privatesCleared = state.privatePerPlayer.mapValues { (_, priv) ->
            priv.copy(voteAcknowledged = false)
        }
        return Reduction(
            state.copy(
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
                        revoteRound = 0,
                        tally = outcome.tally,
                        eliminatedPlayerId = eliminated,
                    ),
                ),
                privatePerPlayer = privatesCleared,
            ),
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
                        revoteRound = 0,
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
                state.copy(phase = MafiaPhase.PostGame),
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
            priv.copy(voteAcknowledged = false)
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
        return Reduction(
            state.copy(phase = MafiaPhase.PostGame),
            listOf(MafiaEvent.GameEnded, MafiaEvent.PhaseEntered(MafiaPhase.PostGame)),
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
        if (id !in state.players.map { it.id }) return Reduction(state)
        if (id in state.public.droppedPlayers) return Reduction(state)
        return Reduction(
            state.copy(public = state.public.copy(
                droppedPlayers = state.public.droppedPlayers + id,
            )),
        )
    }

    private fun readmit(state: MafiaState, id: PlayerId): Reduction<MafiaState, MafiaEvent> {
        if (id !in state.public.droppedPlayers) return Reduction(state)
        // Only readmit before the first Night begins.
        val canReadmit = state.phase == MafiaPhase.Setup || state.phase == MafiaPhase.RoleAssignment
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

    @Suppress("UNUSED_PARAMETER")
    private fun isAlive(state: MafiaState, role: Role, killedThisStep: PlayerId?): Boolean = true

    private fun aliveSet(state: MafiaState): Set<PlayerId> =
        state.public.roster.filter { it.alive }.map { it.playerId }.toSet()

    private fun activeRoster(state: MafiaState): List<PlayerId> =
        state.players.map { it.id }.filterNot { it in state.public.droppedPlayers }

    private fun aliveMafiaSubmissions(state: MafiaState): Map<PlayerId, PlayerId?> =
        state.privatePerPlayer
            .filter { (id, priv) -> priv.role == Role.Mafia && isAlive(state, id) }
            .mapValues { (_, priv) -> priv.pendingNightChoice }

    private fun aliveDoctorChoice(state: MafiaState): PlayerId? =
        state.privatePerPlayer.entries.firstOrNull { (id, priv) ->
            priv.role == Role.Doctor && isAlive(state, id)
        }?.value?.pendingNightChoice

    private fun aliveDetectiveChoice(state: MafiaState): PlayerId? =
        state.privatePerPlayer.entries.firstOrNull { (id, priv) ->
            priv.role == Role.Detective && isAlive(state, id)
        }?.value?.pendingNightChoice

    private fun detectiveByPlayer(state: MafiaState): PlayerId? =
        state.privatePerPlayer.entries.firstOrNull { (id, priv) ->
            priv.role == Role.Detective && isAlive(state, id)
        }?.key

    private fun previousNightDoctorProtect(state: MafiaState): PlayerId? =
        state.hostOnly.nightLog.lastOrNull()?.doctorProtect

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
            if (priv.team == Team.Mafia && isAlive(state, id)) {
                priv.copy(mafiaCoordination = snapshot)
            } else {
                priv.copy(mafiaCoordination = null)
            }
        }
        return state.copy(privatePerPlayer = updated)
    }
}

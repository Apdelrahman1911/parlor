package com.parlor.games.mafia.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.phase.MafiaPhase

/** Validates one redacted host snapshot before a peer installs it. */
internal object MafiaPeerSnapshotValidator {
    fun isValid(
        publicState: MafiaState,
        ownPrivate: MafiaPrivate?,
        selfPlayerId: PlayerId,
    ): Boolean = try {
        requireValid(publicState, ownPrivate, selfPlayerId)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod") // Security boundary: every independent projection invariant fails closed here.
    private fun requireValid(
        publicState: MafiaState,
        ownPrivate: MafiaPrivate?,
        selfPlayerId: PlayerId,
    ) {
        MafiaObservableStateValidator.requireValid(publicState)
        require(selfPlayerId in publicState.players.map { it.id }) {
            "Peer identity is absent from the Mafia roster"
        }
        require(publicState.privatePerPlayer.isEmpty()) { "Public Mafia snapshot contains private state" }
        require(
            publicState.hostOnly.fullRoleMap.isEmpty() &&
                publicState.hostOnly.randomSeed == 0L &&
                publicState.hostOnly.nightLog.isEmpty() &&
                publicState.hostOnly.voteLog.isEmpty(),
        ) { "Public Mafia snapshot contains host-only state" }

        val unassignedTerminal = publicState.phase == MafiaPhase.PostGame &&
            publicState.public.roster.all { it.revealedRole == null }
        val assigned = publicState.phase != MafiaPhase.Setup && !unassignedTerminal
        require((ownPrivate != null) == assigned) {
            "Mafia peer private slice does not match assignment state"
        }
        if (ownPrivate == null) return

        require(ownPrivate.team == ownPrivate.role.team) { "Mafia peer role and team disagree" }
        val rosterIds = publicState.players.map { it.id }.toSet()
        val activeAlive = publicState.public.roster
            .filter { it.alive && it.playerId !in publicState.public.droppedPlayers }
            .map { it.playerId }
            .toSet()
        require(selfPlayerId !in ownPrivate.knownTeammates) { "Mafia peer lists itself as a teammate" }
        require(rosterIds.containsAll(ownPrivate.knownTeammates)) { "Mafia peer has an unknown teammate" }
        if (ownPrivate.role == Role.Mafia) {
            require(ownPrivate.knownTeammates.size == publicState.public.settings.roleCounts.mafia - 1) {
                "Mafia peer teammate count differs from settings"
            }
        } else {
            require(ownPrivate.knownTeammates.isEmpty()) { "Town peer received Mafia teammates" }
        }

        require(ownPrivate.pendingNightChoice == null || ownPrivate.pendingNightChoice in rosterIds) {
            "Mafia peer night choice references an unknown player"
        }
        require(ownPrivate.pendingNightChoice == null || ownPrivate.nightChoiceSubmitted) {
            "Mafia peer night choice is not marked submitted"
        }
        require(ownPrivate.pendingDetectiveResult == null || ownPrivate.role == Role.Detective) {
            "Non-Detective peer received an inspection result"
        }
        ownPrivate.pendingDetectiveResult?.let { result ->
            require(result.target in rosterIds && result.day in 1..publicState.public.day) {
                "Mafia peer inspection result is invalid"
            }
        }
        require(!ownPrivate.detectiveResultAcknowledged || ownPrivate.role == Role.Detective) {
            "Non-Detective peer acknowledged an inspection result"
        }
        require(ownPrivate.previousDoctorProtect == null || ownPrivate.role == Role.Doctor) {
            "Non-Doctor peer received protection history"
        }
        require(ownPrivate.previousDoctorProtect == null || ownPrivate.previousDoctorProtect in rosterIds) {
            "Mafia peer protection history references an unknown player"
        }
        require(ownPrivate.lastSuspicion == null || ownPrivate.role == Role.Civilian) {
            "Non-Civilian peer received suspicion state"
        }
        require(
            ownPrivate.lastSuspicion == null ||
                (ownPrivate.lastSuspicion in rosterIds && ownPrivate.lastSuspicion != selfPlayerId),
        ) {
            "Mafia peer suspicion references an unknown player"
        }

        validateNightActionState(
            state = publicState,
            private = ownPrivate,
            selfPlayerId = selfPlayerId,
            activeAlive = activeAlive,
        )

        require(publicState.phase == MafiaPhase.RoleAssignment || !ownPrivate.roleAcknowledged) {
            "Mafia role acknowledgement survived its phase"
        }
        require(publicState.phase is MafiaPhase.NightAnnouncement || !ownPrivate.nightAcknowledged) {
            "Mafia night acknowledgement survived its phase"
        }
        require(publicState.phase is MafiaPhase.VoteAnnouncement || !ownPrivate.voteAcknowledged) {
            "Mafia vote acknowledgement survived its phase"
        }
        require(
            publicState.phase is MafiaPhase.Night ||
                (!ownPrivate.nightChoiceSubmitted && ownPrivate.pendingNightChoice == null),
        ) { "Mafia night action survived outside Night" }

        val coordination = ownPrivate.mafiaCoordination
        if (coordination != null) {
            require(ownPrivate.role == Role.Mafia) { "Town peer received Mafia coordination" }
            val expectedRound = when (val phase = publicState.phase) {
                MafiaPhase.RoleAssignment -> 1
                is MafiaPhase.Night -> phase.mafiaCoordinationRound
                else -> null
            }
            require(coordination.round == expectedRound) { "Mafia coordination round is stale" }
            require((ownPrivate.knownTeammates + selfPlayerId).containsAll(coordination.submissions.keys)) {
                "Mafia coordination contains a non-Mafia submitter"
            }
            require(activeAlive.containsAll(coordination.submissions.values)) {
                "Mafia coordination targets an ineligible player"
            }
            require(
                publicState.public.settings.mafiaCanTargetMafia ||
                    coordination.submissions.values.none(
                        (ownPrivate.knownTeammates + selfPlayerId)::contains,
                    ),
            ) { "Mafia coordination bypasses friendly-fire settings" }
            require(coordination.previousRoundTally?.all { (id, count) ->
                id in activeAlive && count > 0
            } != false) { "Mafia coordination previous tally is invalid" }
            require(
                if (coordination.round == 1) {
                    coordination.previousRoundTally == null
                } else {
                    coordination.previousRoundTally.hasTiedLead()
                },
            ) { "Mafia coordination history does not match its round" }
            if (publicState.phase is MafiaPhase.Night) {
                val ownSubmittedTarget = coordination.submissions[selfPlayerId]
                require(
                    if (ownPrivate.nightChoiceSubmitted && ownPrivate.pendingNightChoice != null) {
                        ownSubmittedTarget == ownPrivate.pendingNightChoice
                    } else {
                        ownSubmittedTarget == null
                    },
                ) { "Mafia peer choice disagrees with coordination" }
            }
        } else if (
            ownPrivate.role == Role.Mafia &&
            selfPlayerId !in publicState.public.droppedPlayers &&
            publicState.public.roster.single { it.playerId == selfPlayerId }.alive &&
            (publicState.phase == MafiaPhase.RoleAssignment || publicState.phase is MafiaPhase.Night)
        ) {
            require(false) { "Living Mafia peer is missing coordination state" }
        }

        if (publicState.phase == MafiaPhase.PostGame) {
            require(
                ownPrivate.mafiaCoordination == null &&
                    ownPrivate.pendingDetectiveResult == null &&
                    ownPrivate.lastSuspicion == null &&
                    ownPrivate.previousDoctorProtect == null &&
                    ownPrivate.pendingNightChoice == null &&
                    !ownPrivate.roleAcknowledged &&
                    !ownPrivate.nightAcknowledged &&
                    !ownPrivate.voteAcknowledged &&
                    !ownPrivate.detectiveResultAcknowledged &&
                    !ownPrivate.nightChoiceSubmitted,
            ) { "Terminal Mafia peer slice contains transient action state" }
            require(publicState.public.roster.single { it.playerId == selfPlayerId }.revealedRole == ownPrivate.role) {
                "Terminal Mafia peer role disagrees with the public reveal"
            }
        }
    }

    private fun validateNightActionState(
        state: MafiaState,
        private: MafiaPrivate,
        selfPlayerId: PlayerId,
        activeAlive: Set<PlayerId>,
    ) {
        val night = state.phase as? MafiaPhase.Night
        if (private.nightChoiceSubmitted) {
            require(night != null && selfPlayerId in activeAlive) {
                "Ineligible Mafia peer has a submitted night action"
            }
        }
        private.pendingNightChoice?.let { target ->
            require(night != null && selfPlayerId in activeAlive && target in activeAlive) {
                "Mafia peer night target is not currently eligible"
            }
            when (private.role) {
                Role.Mafia -> require(
                    target != selfPlayerId &&
                        (state.public.settings.mafiaCanTargetMafia || target !in private.knownTeammates),
                ) { "Mafia peer kill target bypasses settings" }
                Role.Doctor -> require(
                    (state.public.settings.doctorCanSelfHeal || target != selfPlayerId) &&
                        (
                            state.public.settings.doctorCanProtectSamePlayerConsecutively ||
                                target != private.previousDoctorProtect
                        ),
                ) { "Mafia peer protection target bypasses settings" }
                Role.Detective -> require(
                    state.public.settings.detectiveCanInspectSelf || target != selfPlayerId,
                ) { "Mafia peer inspection target bypasses settings" }
                Role.Civilian -> throw IllegalArgumentException(
                    "Civilian suspicion was stored as an authoritative night target",
                )
            }
        }
        private.pendingDetectiveResult?.let { result ->
            if (night != null) {
                require(
                    result.day == night.day &&
                        result.target == private.pendingNightChoice &&
                        private.nightChoiceSubmitted,
                ) { "Mafia peer inspection result does not match its current action" }
            } else {
                require(private.detectiveResultAcknowledged) {
                    "Unacknowledged inspection result survived night resolution"
                }
            }
        }
        if (
            night != null &&
            private.role == Role.Detective &&
            private.nightChoiceSubmitted &&
            private.pendingNightChoice == null
        ) {
            require(private.detectiveResultAcknowledged) {
                "Skipped Detective action was not marked acknowledged"
            }
        }
    }

    private fun Map<PlayerId, Int>?.hasTiedLead(): Boolean {
        val tally = this ?: return false
        val maximum = tally.values.maxOrNull() ?: return false
        return tally.values.count { it == maximum } >= 2
    }
}

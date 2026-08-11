package com.parlor.games.mafia.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.MafiaSessionRules
import com.parlor.games.mafia.domain.rules.WinCheck
import com.parlor.games.mafia.domain.settings.MafiaSettingsValidation
import com.parlor.games.mafia.domain.settings.TieBehavior

/**
 * Invariants that are visible in both a canonical host state and a redacted
 * peer projection. Keeping this boundary independent of host-only role data
 * prevents recovery and networking from slowly acquiring different ideas of
 * what a legal Mafia state looks like.
 */
internal object MafiaObservableStateValidator {
    fun isValid(state: MafiaState): Boolean = try {
        requireValid(state)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    fun requireValid(state: MafiaState) {
        val players = state.players
        require(MafiaSessionRules.isValidRoster(players)) { "Invalid Mafia roster" }
        require(state.public.settings.validate(players.size) is MafiaSettingsValidation.Valid) {
            "Invalid Mafia settings"
        }
        require(
            state.hostOnly.nightLog.size <= MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES &&
                state.hostOnly.voteLog.size <= MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES,
        ) { "Mafia host history exceeds its serialized retention bound" }

        val playerIds = players.map { it.id }
        val playerIdSet = playerIds.toSet()
        require(state.public.roster.size == players.size) { "Public Mafia roster has the wrong size" }
        require(state.public.roster.zip(players).all { (slot, player) ->
            slot.playerId == player.id &&
                slot.displayName == player.displayName &&
                slot.seat == player.seat
        }) { "Public and authoritative Mafia rosters differ" }
        require(playerIdSet.containsAll(state.public.disconnectedPlayers)) {
            "Disconnected Mafia player is outside the roster"
        }
        require(playerIdSet.containsAll(state.public.droppedPlayers)) {
            "Dropped Mafia player is outside the roster"
        }
        require(state.public.disconnectedPlayers.intersect(state.public.droppedPlayers).isEmpty()) {
            "Mafia player is both disconnected and dropped"
        }
        require(
            state.phase == MafiaPhase.PostGame || state.public.droppedPlayers.isEmpty(),
        ) { "Active Mafia state contains a permanently dropped player" }
        require(state.public.day >= 0) { "Mafia day is negative" }

        validateAnnouncements(state, playerIdSet)
        validateVote(state)
        validatePhase(state)
        validateRoleVisibility(state)
    }

    private fun validateAnnouncements(state: MafiaState, playerIds: Set<PlayerId>) {
        val public = state.public
        public.lastNight?.let { announcement ->
            require(announcement.day in 1..public.day) { "Night announcement has an invalid day" }
            require(announcement.killedPlayerId == null || announcement.killedPlayerId in playerIds) {
                "Night announcement references an unknown player"
            }
            require(!(announcement.wasSaved && announcement.killedPlayerId != null)) {
                "A saved Mafia target is also marked dead"
            }
            announcement.killedPlayerId?.let { killed ->
                require(public.roster.single { it.playerId == killed }.alive.not()) {
                    "Latest night victim is still alive"
                }
            }
        }
        public.lastVote?.let { announcement ->
            require(announcement.day in 1..public.day) { "Vote announcement has an invalid day" }
            require(announcement.tally.all { (id, count) -> id in playerIds && count > 0 }) {
                "Vote announcement contains an invalid tally"
            }
            require(announcement.tally.values.sumOf { it.toLong() } <= playerIds.size.toLong()) {
                "Vote announcement contains more votes than players"
            }
            require(announcement.eliminatedPlayerId == null || announcement.eliminatedPlayerId in playerIds) {
                "Vote announcement references an unknown elimination"
            }
            when (announcement.outcome) {
                VoteOutcome.Eliminated -> {
                    val maximum = announcement.tally.values.maxOrNull()
                    val leaders = announcement.tally.filterValues { it == maximum }.keys
                    require(
                        maximum != null &&
                            leaders.size == 1 &&
                            leaders.single() == announcement.eliminatedPlayerId,
                    ) { "Elimination outcome does not match its tally" }
                }
                VoteOutcome.SkippedDueToTie -> require(
                    announcement.eliminatedPlayerId == null &&
                        announcement.tally.hasTiedLead() &&
                        public.settings.voteTieBehavior == TieBehavior.SKIP_ELIMINATION,
                ) { "Skipped-tie outcome does not match its tally or settings" }
                VoteOutcome.MaxRevotesReached -> require(
                    announcement.eliminatedPlayerId == null &&
                        announcement.tally.hasTiedLead() &&
                        public.settings.voteTieBehavior != TieBehavior.SKIP_ELIMINATION,
                ) { "Max-revotes outcome does not match its tally or settings" }
                VoteOutcome.AllAbstained -> require(
                    announcement.eliminatedPlayerId == null && announcement.tally.isEmpty(),
                ) {
                    "All-abstained outcome contains a vote"
                }
            }
            announcement.eliminatedPlayerId?.let { eliminated ->
                require(public.roster.single { it.playerId == eliminated }.alive.not()) {
                    "Latest vote elimination is still alive"
                }
            }
        }
        require(public.winner == null || state.phase == MafiaPhase.PostGame) {
            "A non-terminal Mafia phase contains a winner"
        }
    }

    private fun validateVote(state: MafiaState) {
        val voting = state.phase as? MafiaPhase.Voting
        val vote = state.public.activeVote
        if (voting == null) {
            require(vote == null) { "Active Mafia vote exists outside Voting" }
            return
        }
        requireNotNull(vote) { "Voting phase has no active Mafia vote" }

        val eligible = state.public.roster
            .filter(PublicPlayerSlot::alive)
            .map(PublicPlayerSlot::playerId)
            .filterNot(state.public.droppedPlayers::contains)
        require(vote.day == voting.day && vote.revoteRound == voting.revoteRound) {
            "Mafia vote and phase disagree"
        }
        require(vote.revoteRound in 0..state.public.settings.maxRevotes) {
            "Mafia revote exceeds settings"
        }
        require(vote.ballot == eligible && vote.ballot.isNotEmpty()) {
            "Mafia vote ballot differs from the active roster"
        }
        require(hasReachableCandidates(vote, eligible, state.public.settings.voteTieBehavior)) {
            "Mafia vote candidates are not reducer-reachable"
        }
        require(vote.castSoFar.keys.all(vote.ballot::contains)) { "Unknown Mafia voter" }
        require(vote.castSoFar.values.all(vote.candidates::contains)) { "Invalid Mafia vote target" }
        require(vote.abstained.all(vote.ballot::contains)) { "Unknown Mafia abstention" }
        require(vote.castSoFar.keys.intersect(vote.abstained).isEmpty()) {
            "Mafia voter both cast and abstained"
        }
        require(
            state.public.settings.allowSelfVote ||
                vote.castSoFar.none { (voter, target) -> voter == target },
        ) { "Mafia self-vote bypasses settings" }
    }

    private fun hasReachableCandidates(
        vote: ActiveVote,
        eligible: List<PlayerId>,
        tieBehavior: TieBehavior,
    ): Boolean {
        if (vote.revoteRound == 0) return vote.candidates == eligible
        return when (tieBehavior) {
            TieBehavior.SKIP_ELIMINATION -> false
            TieBehavior.REVOTE_ALL -> vote.candidates == eligible
            TieBehavior.REVOTE_TIED_ONLY ->
                vote.candidates.size >= 2 &&
                    vote.candidates.distinct().size == vote.candidates.size &&
                    eligible.containsAll(vote.candidates) &&
                    vote.candidates == vote.candidates.sortedBy { it.raw }
        }
    }

    @Suppress("CyclomaticComplexMethod") // Exhaustive phase-to-shape state-machine validation.
    private fun validatePhase(state: MafiaState) {
        val public = state.public

        fun requireNoProgress() {
            require(
                public.day == 0 &&
                    public.lastNight == null &&
                    public.lastVote == null &&
                    public.activeVote == null &&
                    public.winner == null &&
                    public.roster.all { it.alive && it.revealedRole == null },
            ) { "Pre-game Mafia state contains gameplay progress" }
        }

        fun requirePriorDay(day: Int) {
            if (day == 1) {
                require(public.lastVote == null) { "First Mafia day contains a prior vote" }
            } else {
                require(public.lastVote?.day == day - 1) { "Mafia day is missing its prior vote" }
            }
        }

        when (val phase = state.phase) {
            MafiaPhase.Setup,
            MafiaPhase.RoleAssignment,
            -> requireNoProgress()

            is MafiaPhase.Night -> {
                require(phase.day >= 1 && phase.mafiaCoordinationRound in 1..2) {
                    "Invalid Mafia night phase"
                }
                require(public.day == phase.day && public.winner == null) {
                    "Mafia night and public day disagree"
                }
                if (phase.day == 1) {
                    require(public.lastNight == null) { "First Mafia night contains a prior night" }
                } else {
                    require(public.lastNight?.day == phase.day - 1) {
                        "Mafia night is missing its prior night announcement"
                    }
                }
                requirePriorDay(phase.day)
            }

            is MafiaPhase.NightAnnouncement -> {
                require(phase.day >= 1 && public.day == phase.day && public.winner == null) {
                    "Invalid Mafia night-announcement phase"
                }
                require(public.lastNight?.day == phase.day) {
                    "Night-announcement phase lacks the current announcement"
                }
                requirePriorDay(phase.day)
            }

            is MafiaPhase.Discussion -> {
                require(phase.day >= 1 && public.day == phase.day && public.winner == null) {
                    "Invalid Mafia discussion phase"
                }
                require(public.lastNight?.day == phase.day) {
                    "Discussion lacks the current night announcement"
                }
                requirePriorDay(phase.day)
            }

            is MafiaPhase.Voting -> {
                require(phase.day >= 1 && public.day == phase.day && public.winner == null) {
                    "Invalid Mafia voting phase"
                }
                require(public.lastNight?.day == phase.day) {
                    "Voting lacks the current night announcement"
                }
                requirePriorDay(phase.day)
            }

            is MafiaPhase.VoteAnnouncement -> {
                require(phase.day >= 1 && public.day == phase.day && public.winner == null) {
                    "Invalid Mafia vote-announcement phase"
                }
                require(public.lastNight?.day == phase.day && public.lastVote?.day == phase.day) {
                    "Vote-announcement phase lacks current-day outcomes"
                }
            }

            MafiaPhase.PostGame -> {
                require(public.activeVote == null) { "Post-game Mafia state contains an active vote" }
                require(public.disconnectedPlayers.isEmpty()) {
                    "Post-game Mafia state contains disconnected players"
                }
                if (public.roster.all { it.revealedRole != null }) {
                    val lastNightDay = public.lastNight?.day ?: 0
                    val lastVoteDay = public.lastVote?.day ?: 0
                    require(
                        if (public.day == 0) {
                            lastNightDay == 0 && lastVoteDay == 0
                        } else {
                            lastNightDay in (public.day - 1)..public.day &&
                                lastVoteDay in (public.day - 1)..public.day &&
                                lastVoteDay <= lastNightDay
                        },
                    ) { "Post-game Mafia day is not reachable from its latest resolutions" }
                }
            }
        }
    }

    private fun validateRoleVisibility(state: MafiaState) {
        val roles = state.public.roster.map(PublicPlayerSlot::revealedRole)
        if (state.phase == MafiaPhase.PostGame) {
            require(roles.all { it == null } || roles.all { it != null }) {
                "Post-game Mafia roles are only partially revealed"
            }
            if (roles.all { it == null }) {
                require(
                    state.public.day == 0 &&
                        state.public.lastNight == null &&
                        state.public.lastVote == null &&
                        state.public.winner == null &&
                        state.public.droppedPlayers.isEmpty() &&
                        state.public.roster.all(PublicPlayerSlot::alive),
                ) { "Unassigned Mafia terminal state contains gameplay progress" }
            } else {
                val configured = state.public.settings.roleCounts
                require(
                    roles.count { it == Role.Mafia } == configured.mafia &&
                        roles.count { it == Role.Detective } == configured.detective &&
                        roles.count { it == Role.Doctor } == configured.doctor &&
                        roles.count { it == Role.Civilian } == configured.civilians(roles.size),
                ) { "Terminal Mafia role reveal differs from settings" }
                val rolesByPlayer = state.public.roster.associate { slot ->
                    slot.playerId to requireNotNull(slot.revealedRole)
                }
                val activeAlive = state.public.roster
                    .filter { it.alive && it.playerId !in state.public.droppedPlayers }
                    .map { it.playerId }
                    .toSet()
                require(WinCheck.evaluate(activeAlive, rolesByPlayer) == state.public.winner) {
                    "Terminal Mafia winner differs from the public role reveal"
                }
            }
            return
        }

        state.public.roster.forEach { slot ->
            when {
                slot.alive -> require(slot.revealedRole == null) {
                    "Living Mafia player has a public role"
                }
                state.public.settings.revealRoleOnDeath -> require(slot.revealedRole != null) {
                    "Dead Mafia player's configured role reveal is missing"
                }
                else -> require(slot.revealedRole == null) {
                    "Dead Mafia role was exposed while reveal is disabled"
                }
            }
        }
    }

    private fun Map<PlayerId, Int>.hasTiedLead(): Boolean {
        val maximum = values.maxOrNull() ?: return false
        return values.count { it == maximum } >= 2
    }
}

package com.parlor.games.whodunit.domain.state

import com.parlor.core.ids.CaseId
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.rules.WhodunitRules

/**
 * Structural trust boundary for persisted authoritative Whodunit state.
 *
 * kotlinx.serialization proves only that fields have the expected Kotlin
 * types. It does not prove that identities, roles, votes, timers, or phases
 * describe a state the reducer could ever produce. Restoring an impossible
 * shape can leak the wrong dossier or strand every player, so the snapshot
 * codec runs this validator before accepting or emitting state.
 */
internal object WhodunitStateValidator {
    fun requireValid(state: WhodunitState) {
        val players = state.players
        require(WhodunitRules.isValidRoster(state.public.modeId, players)) {
            "Invalid mode or roster"
        }
        require(state.public.playersAtTable == players) {
            "Public and authoritative rosters differ"
        }

        val playerIds = players.map { it.id }
        val playerIdSet = playerIds.toSet()
        require(state.public.eliminatedPlayers.size == state.public.eliminatedPlayers.toSet().size) {
            "Eliminated players contain duplicates"
        }
        require(playerIdSet.containsAll(state.public.eliminatedPlayers)) {
            "Eliminated player is outside roster"
        }
        listOf(
            state.public.introAcknowledged,
            state.public.briefingReady,
            state.public.rolesViewed,
            state.public.disconnectedPlayers,
            state.public.droppedPlayers,
        ).forEach { ids ->
            require(playerIdSet.containsAll(ids)) { "Player state references an unknown player" }
        }

        validateAssignment(state, playerIdSet)
        validatePrivateRevealState(state)
        validateRoundsAndTimer(state)
        validateVote(state, playerIdSet)

        val terminal = state.phase == WhodunitPhase.Reveal || state.phase == WhodunitPhase.PostGame
        require(terminal || state.public.verdict == null) {
            "A non-terminal phase carries a verdict"
        }
        if (state.phase == WhodunitPhase.Reveal) {
            require(state.public.verdict != null) { "Reveal phase has no verdict" }
        }
    }

    /**
     * Validates state references that cannot be checked from serialized state
     * alone. Call this only after the expected, already-validated case payload
     * has been loaded; it deliberately does not choose or fetch content.
     */
    fun requireValidForCase(
        state: WhodunitState,
        expectedCaseId: CaseId,
        payload: WhodunitCase,
    ) {
        requireValid(state)
        require(state.public.caseId == expectedCaseId) { "State belongs to another case" }

        val characterIds = payload.characters.map { it.id }
        require(characterIds.size == characterIds.toSet().size) {
            "Case contains duplicate character ids"
        }
        val assignedCharacterIds = buildSet {
            state.hostOnly.seatToCharacter.values.forEach { add(it.raw) }
            state.privatePerPlayer.values.forEach { add(it.characterId.raw) }
            state.hostOnly.redHerringTargets.forEach { add(it.raw) }
            if (state.hostOnly.seatToCharacter.isNotEmpty()) {
                add(state.hostOnly.killerCharacterId.raw)
            }
        }
        require(characterIds.toSet().containsAll(assignedCharacterIds)) {
            "State references a character absent from the loaded case"
        }

        val clues = payload.allClues()
        require(clues.map { it.id }.toSet().size == clues.size) {
            "Case contains duplicate clue ids"
        }
        val cluesById = clues.associateBy { it.id }
        state.public.revealedClues.forEach { revealed ->
            val authored = cluesById[revealed.id.raw]
                ?: throw IllegalArgumentException("State references a clue absent from the loaded case")
            require(authored.text == revealed.text) {
                "Revealed clue text differs from the loaded case"
            }
            require(authored.appliesToModes?.let { state.public.modeId.raw in it } != false) {
                "Revealed clue does not apply to the active mode"
            }
        }
    }

    private fun validateAssignment(state: WhodunitState, playerIds: Set<com.parlor.core.ids.PlayerId>) {
        require(state.public.roleAssignmentGeneration >= 0L) {
            "Role-assignment generation is negative"
        }
        val noAssignment = state.privatePerPlayer.isEmpty() && state.hostOnly.seatToCharacter.isEmpty()
        if (noAssignment) {
            require(state.public.roleAssignmentGeneration == 0L) {
                "Unassigned state carries a role-assignment generation"
            }
            require(
                state.phase == WhodunitPhase.Setup ||
                    (state.phase == WhodunitPhase.PostGame && state.public.verdict == null),
            ) { "Active state has no role assignment" }
            require(state.hostOnly.killerId.raw == UNASSIGNED_ID) { "Unassigned state has a killer" }
            require(state.hostOnly.killerCharacterId.raw == UNASSIGNED_ID) {
                "Unassigned state has a killer character"
            }
            return
        }

        require(state.public.roleAssignmentGeneration > 0L) {
            "Assigned state has no role-assignment generation"
        }
        require(state.phase != WhodunitPhase.Setup) { "Setup already contains roles" }
        require(state.privatePerPlayer.keys == playerIds) { "Private role map is incomplete" }
        require(state.hostOnly.seatToCharacter.keys == playerIds) { "Seat map is incomplete" }
        require(state.hostOnly.seatToCharacter.values.toSet().size == playerIds.size) {
            "Character assignment is not one-to-one"
        }
        state.privatePerPlayer.forEach { (playerId, privateState) ->
            require(state.hostOnly.seatToCharacter[playerId] == privateState.characterId) {
                "Private dossier and host seat map disagree"
            }
            require(
                privateState.deflectionTargets.size ==
                    privateState.deflectionTargets.toSet().size &&
                    state.hostOnly.seatToCharacter.values.containsAll(
                        privateState.deflectionTargets,
                    ) &&
                    privateState.characterId !in privateState.deflectionTargets,
            ) { "Private dossier has an invalid deflection target" }
        }
        val killers = state.privatePerPlayer.filterValues { it.role == PlayerRole.Killer }.keys
        require(killers == setOf(state.hostOnly.killerId)) { "State must contain exactly one killer" }
        require(state.hostOnly.killerId in playerIds) { "Killer is outside roster" }
        require(
            state.hostOnly.seatToCharacter[state.hostOnly.killerId] ==
                state.hostOnly.killerCharacterId,
        ) { "Killer identity and character disagree" }
        require(
            state.hostOnly.redHerringTargets.size == state.hostOnly.redHerringTargets.toSet().size &&
                state.hostOnly.seatToCharacter.values.containsAll(state.hostOnly.redHerringTargets),
        ) { "Red-herring target is not a distinct assigned character" }
        state.privatePerPlayer.forEach { (playerId, privateState) ->
            if (playerId == state.hostOnly.killerId) {
                require(privateState.deflectionTargets == state.hostOnly.redHerringTargets) {
                    "Killer dossier and authoritative deflection targets disagree"
                }
            } else {
                require(privateState.deflectionTargets.isEmpty()) {
                    "Innocent dossier contains killer-only deflection targets"
                }
            }
        }
    }

    private fun validatePrivateRevealState(state: WhodunitState) {
        if (state.phase !is WhodunitPhase.CharacterReveal) {
            require(state.privatePerPlayer.values.none { privateState ->
                privateState.dossierUnlocked || privateState.privateReviewOpen
            }) { "Private dossier state is open outside character reveal" }
        }
        state.public.rolesViewed.forEach { playerId ->
            val privateState = state.privatePerPlayer[playerId]
                ?: throw IllegalArgumentException("Viewed role has no private state")
            require(!privateState.dossierUnlocked && !privateState.privateReviewOpen) {
                "Viewed role still exposes private dossier state"
            }
        }
    }

    private fun validateRoundsAndTimer(state: WhodunitState) {
        val maximumRounds = requireNotNull(
            WhodunitRules.maximumRoundCount(state.public.modeId, state.players.size),
        ) { "Unknown round policy" }
        require(state.public.currentRound in 0..maximumRounds) { "Current round is out of bounds" }
        when (val phase = state.phase) {
            WhodunitPhase.Setup,
            WhodunitPhase.PublicIntro,
            WhodunitPhase.RulesBriefing -> require(state.public.currentRound == 0) {
                "Pre-round phase has a current round"
            }
            is WhodunitPhase.CharacterReveal -> {
                require(state.public.currentRound == 0) { "Character reveal has a current round" }
                require(phase.playerIndex in state.players.indices) {
                    "Character-reveal index is outside roster"
                }
            }
            is WhodunitPhase.Round -> require(
                phase.index > 0 && phase.index == state.public.currentRound,
            ) { "Round phase and public round disagree" }
            WhodunitPhase.FinalVote,
            WhodunitPhase.TiedRevote -> require(state.public.currentRound > 0) {
                "Vote phase has no completed investigation round"
            }
            WhodunitPhase.Reveal,
            WhodunitPhase.PostGame -> Unit
        }

        val revealedIds = state.public.revealedClues.map { it.id }
        require(revealedIds.size == revealedIds.toSet().size) { "A clue was revealed twice" }
        require(
            state.public.revealedClues.map { it.roundIndex }.let { it.size == it.toSet().size },
        ) { "More than one clue exists for a round" }
        val revealedRounds = state.public.revealedClues.map { it.roundIndex }
        require(revealedRounds == revealedRounds.sorted()) { "Revealed clues are out of round order" }
        require(revealedRounds.withIndex().all { (index, round) -> round == index + 1 }) {
            "Revealed clue rounds are not contiguous"
        }
        require(state.public.revealedClues.all {
            it.roundIndex in 1..maximumRounds &&
                it.roundIndex <= state.public.currentRound &&
                it.text.isNotBlank()
        }) {
            "Invalid revealed clue"
        }
        require(state.hostOnly.drawnClueIds == revealedIds.toSet()) {
            "Drawn and revealed clue sets disagree"
        }

        state.public.timer?.let { timer ->
            require(state.phase is WhodunitPhase.Round) { "Timer exists outside a round" }
            require(
                timer.totalSeconds in
                    WhodunitRules.MIN_DISCUSSION_SECONDS..WhodunitRules.MAX_DISCUSSION_SECONDS,
            ) { "Invalid timer duration" }
            require(timer.remainingSeconds in 0..timer.totalSeconds) { "Invalid timer remainder" }
            require(timer.timerId == "discussion-${state.public.currentRound}") {
                "Timer belongs to another round"
            }
            require(state.public.revealedClues.any { it.roundIndex == state.public.currentRound }) {
                "Active timer has no revealed clue for its round"
            }
            if (state.public.paused) require(timer.paused) { "Session pause did not freeze timer" }
        }
    }

    private fun validateVote(state: WhodunitState, playerIds: Set<com.parlor.core.ids.PlayerId>) {
        when (val vote = state.public.voteState) {
            VoteState.Idle -> Unit
            is VoteState.Collecting -> {
                require(
                    state.phase is WhodunitPhase.Round ||
                        state.phase == WhodunitPhase.FinalVote ||
                        state.phase == WhodunitPhase.TiedRevote,
                ) { "Collecting vote exists outside a voting phase" }
                require(vote.isElimination == (state.public.modeId == WhodunitIds.EliminationModeId)) {
                    "Vote mode differs from session mode"
                }
                require(vote.ballotPlayerIds.isNotEmpty() &&
                    vote.ballotPlayerIds.size == vote.ballotPlayerIds.toSet().size
                ) { "Invalid ballot roster" }
                require(vote.candidatePlayerIds.isNotEmpty() &&
                    vote.candidatePlayerIds.size == vote.candidatePlayerIds.toSet().size
                ) { "Invalid candidate roster" }
                require(playerIds.containsAll(vote.ballotPlayerIds)) { "Unknown voter" }
                require(vote.ballotPlayerIds.containsAll(vote.candidatePlayerIds)) {
                    "Candidate cannot vote in this ballot"
                }
                require(vote.castSoFar.keys.intersect(vote.abstained).isEmpty()) {
                    "Voter both cast and abstained"
                }
                require(vote.ballotPlayerIds.containsAll(vote.castSoFar.keys + vote.abstained)) {
                    "Completed ballot references unknown voter"
                }
                require(vote.castSoFar.all { (voter, target) ->
                    voter != target &&
                        target in vote.candidatePlayerIds &&
                        target !in state.public.eliminatedPlayers &&
                        target !in state.public.droppedPlayers
                }) { "Invalid vote target" }
                require(vote.currentVoterIndex == vote.castSoFar.size + vote.abstained.size) {
                    "Ballot progress is inconsistent"
                }
                require(vote.currentVoterIndex in 0..vote.ballotPlayerIds.size) {
                    "Ballot cursor is outside the roster"
                }
                require(
                    vote.castSoFar.keys + vote.abstained ==
                        vote.ballotPlayerIds.take(vote.currentVoterIndex).toSet(),
                ) { "Ballots were not recorded in canonical voter order" }
                if (vote.isElimination) {
                    require(vote.ballotPlayerIds.none { it in state.public.eliminatedPlayers }) {
                        "Eliminated player has a ballot"
                    }
                }
            }
            is VoteState.Tied -> {
                require(state.phase == WhodunitPhase.TiedRevote) { "Tie exists outside revote" }
                require(vote.tiedPlayerIds.size >= 2 &&
                    vote.tiedPlayerIds.size == vote.tiedPlayerIds.toSet().size
                ) { "Invalid tied candidates" }
                require(playerIds.containsAll(vote.tiedPlayerIds)) { "Tie references unknown player" }
                require(vote.tiedPlayerIds.none {
                    it in state.public.eliminatedPlayers || it in state.public.droppedPlayers
                }) { "Ineligible player is tied" }
                require(vote.debateSecondsRemaining == 0) { "Tied revote must be untimed" }
            }
            is VoteState.Resolved -> {
                require(vote.accusedPlayerId in playerIds) { "Resolved vote references unknown player" }
                require(vote.wasKiller == (vote.accusedPlayerId == state.hostOnly.killerId)) {
                    "Resolved vote has an incorrect killer flag"
                }
            }
            is VoteState.NoResolution -> {
                require(vote.reason.isNotBlank() && vote.reason.length <= MAX_REASON_LENGTH) {
                    "Invalid no-resolution reason"
                }
                require(state.phase == WhodunitPhase.Reveal || state.phase == WhodunitPhase.PostGame) {
                    "No-resolution marker exists in active gameplay"
                }
            }
        }
    }

    private const val UNASSIGNED_ID = "unassigned"
    private const val MAX_REASON_LENGTH = 128
}

private fun WhodunitCase.allClues(): List<Clue> = buildList {
    addAll(cluePools.publicUniversal)
    cluePools.killerPointing.values.forEach(::addAll)
    cluePools.redHerring.values.forEach(::addAll)
    cluePools.contradiction.values.forEach(::addAll)
    cluePools.finalStrong.values.forEach(::addAll)
}

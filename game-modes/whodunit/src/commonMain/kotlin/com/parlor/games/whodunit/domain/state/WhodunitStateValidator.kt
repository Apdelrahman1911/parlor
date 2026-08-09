package com.parlor.games.whodunit.domain.state

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.rules.WhodunitCluePolicy
import com.parlor.games.whodunit.domain.rules.WhodunitRoundPolicy
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

        val maximumRounds = requireNotNull(
            WhodunitRules.maximumRoundCount(state.public.modeId, state.players.size),
        ) { "Unknown round policy" }
        validateAssignment(state, playerIdSet)
        validatePrivateRevealState(state)
        validateRoundsAndTimer(state, maximumRounds)
        validateVote(state, playerIdSet)
        validatePhaseShape(state, maximumRounds)
        validateTerminalOutcome(state)
    }

    /**
     * Validates state references that cannot be checked from serialized state
     * alone. Call this only after the expected, already-validated case payload
     * has been loaded; it deliberately does not choose or fetch content.
     */
    fun requireValidForCase(
        state: WhodunitState,
        case: ValidatedCase<WhodunitCase>,
    ) {
        requireValid(state)
        require(
            WhodunitRules.isSupportedByCase(
                case = case,
                caseId = state.public.caseId,
                modeId = state.public.modeId,
                playerCount = state.players.size,
            ),
        ) { "State is unsupported by the loaded case" }

        val payload = case.payload

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

        if (state.hostOnly.seatToCharacter.isNotEmpty()) {
            val authoredKiller = payload.characters.single {
                it.id == state.hostOnly.killerCharacterId.raw
            }
            val assignedCharacters = state.hostOnly.seatToCharacter.values.toSet()
            val expectedTargets = authoredKiller.guiltyBrief.deflectionTargets
                .map(::CharacterId)
                .filter(assignedCharacters::contains)
            require(state.hostOnly.redHerringTargets == expectedTargets) {
                "Deflection targets differ from the loaded case"
            }
        }

        var expectedDrawnClues = emptySet<ClueId>()
        state.public.revealedClues.forEach { revealed ->
            val expected = WhodunitCluePolicy.select(
                case = payload,
                killerCharacterId = state.hostOnly.killerCharacterId,
                modeId = state.public.modeId,
                playerCount = state.players.size,
                randomSeed = state.hostOnly.randomSeed,
                roundIndex = revealed.roundIndex,
                drawnClueIds = expectedDrawnClues,
            ) ?: throw IllegalArgumentException("Loaded case cannot reproduce clue history")
            require(revealed.id == ClueId(expected.id) && revealed.text == expected.text) {
                "Revealed clue differs from deterministic case history"
            }
            expectedDrawnClues = expectedDrawnClues + revealed.id
        }
        require(state.hostOnly.drawnClueIds == expectedDrawnClues) {
            "Drawn clues differ from deterministic case history"
        }

        state.public.timer?.let { timer ->
            val expectedSeconds = WhodunitRoundPolicy.discussionSeconds(
                case = payload,
                roundIndex = state.public.currentRound,
                playerCount = state.players.size,
            )
            require(timer.timerId == "discussion-${state.public.currentRound}") {
                "Timer belongs to another authored round"
            }
            require(timer.totalSeconds == expectedSeconds) {
                "Timer duration differs from the loaded case"
            }
            require(timer.remainingSeconds in 0..expectedSeconds) {
                "Timer remainder is outside the authored duration"
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
        state.privatePerPlayer.values.forEach { privateState ->
            require(!privateState.privateReviewOpen || privateState.dossierUnlocked) {
                "Private review is open while its dossier is locked"
            }
        }
        state.public.rolesViewed.forEach { playerId ->
            val privateState = state.privatePerPlayer[playerId]
                ?: throw IllegalArgumentException("Viewed role has no private state")
            require(!privateState.dossierUnlocked && !privateState.privateReviewOpen) {
                "Viewed role still exposes private dossier state"
            }
        }
    }

    private fun validateRoundsAndTimer(state: WhodunitState, maximumRounds: Int) {
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
        val tableIds = state.public.playersAtTable.map { it.id }
        val activeIds = tableIds.filterNot(state.public.droppedPlayers::contains)
        val survivorIds = activeIds.filterNot(state.public.eliminatedPlayers::contains)
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
                val expectedBallot = if (vote.isElimination) survivorIds else activeIds
                require(vote.ballotPlayerIds == expectedBallot) {
                    "Vote ballot differs from the canonical active roster"
                }
                val expectedCandidateOrder = vote.ballotPlayerIds.filter(
                    vote.candidatePlayerIds.toSet()::contains,
                )
                require(vote.candidatePlayerIds == expectedCandidateOrder) {
                    "Vote candidates are duplicated, unknown, or out of canonical order"
                }
                if (vote.isSecondRound) {
                    require(vote.candidatePlayerIds.size >= 2) {
                        "A revote requires at least two tied candidates"
                    }
                } else {
                    require(vote.candidatePlayerIds == vote.ballotPlayerIds) {
                        "A first ballot must allow every active voter as a candidate"
                    }
                }
                require(
                    vote.isSecondRound == (state.phase == WhodunitPhase.TiedRevote),
                ) { "Vote round marker disagrees with the phase" }
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
                val expectedBallot = if (
                    state.public.modeId == WhodunitIds.EliminationModeId
                ) {
                    survivorIds
                } else {
                    activeIds
                }
                require(
                    vote.tiedPlayerIds == expectedBallot.filter(vote.tiedPlayerIds.toSet()::contains),
                ) { "Tied candidates are outside canonical table order" }
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

    /**
     * Cross-field state machine boundary. Field-level validation is not enough:
     * a collection of individually valid values can still describe a phase no
     * reducer transition can produce, which would strand the restored UI.
     */
    private fun validatePhaseShape(state: WhodunitState, maximumRounds: Int) {
        val public = state.public
        val clueCount = public.revealedClues.size

        require(public.disconnectedPlayers.intersect(public.droppedPlayers).isEmpty()) {
            "A player is both disconnected and permanently dropped"
        }
        require(
            state.public.modeId == WhodunitIds.EliminationModeId ||
                public.eliminatedPlayers.isEmpty(),
        ) { "Classic mode contains eliminated players" }
        require(state.phase == WhodunitPhase.PublicIntro || public.introAcknowledged.isEmpty()) {
            "Intro readiness survived outside the intro phase"
        }
        require(state.phase == WhodunitPhase.RulesBriefing || public.briefingReady.isEmpty()) {
            "Briefing readiness survived outside the briefing phase"
        }
        require(state.phase is WhodunitPhase.CharacterReveal || public.rolesViewed.isEmpty()) {
            "Role-view readiness survived outside character reveal"
        }
        require(
            if (state.phase == WhodunitPhase.RulesBriefing) {
                public.briefingCardIndex in 0 until BRIEFING_CARD_COUNT
            } else {
                public.briefingCardIndex == 0
            },
        ) { "Briefing card index disagrees with the phase" }

        val phaseCanPause = when (state.phase) {
            WhodunitPhase.Setup,
            WhodunitPhase.Reveal,
            WhodunitPhase.PostGame -> false
            else -> true
        }
        require(phaseCanPause || !public.paused) { "Terminal or setup state is paused" }
        if (public.disconnectedPlayers.isNotEmpty() && phaseCanPause) {
            require(public.paused) { "Active disconnected state is not paused" }
        }
        if (state.phase == WhodunitPhase.PostGame) {
            require(public.disconnectedPlayers.isEmpty()) {
                "Post-game state still tracks disconnected players"
            }
        }

        fun requirePreRoundShape() {
            require(
                public.currentRound == 0 &&
                    clueCount == 0 &&
                    public.eliminatedPlayers.isEmpty() &&
                    public.timer == null &&
                    public.voteState == VoteState.Idle,
            ) { "Pre-round phase contains gameplay progress" }
        }

        when (val phase = state.phase) {
            WhodunitPhase.Setup,
            WhodunitPhase.PublicIntro,
            WhodunitPhase.RulesBriefing,
            is WhodunitPhase.CharacterReveal -> requirePreRoundShape()

            is WhodunitPhase.Round -> {
                require(clueCount == phase.index - 1 || clueCount == phase.index) {
                    "Round clue history is not reachable"
                }
                if (public.timer != null) {
                    require(clueCount == phase.index && public.voteState == VoteState.Idle) {
                        "Discussion timer exists outside the discussion substate"
                    }
                }
                when (state.public.modeId) {
                    WhodunitIds.ClassicVoteModeId -> require(public.voteState == VoteState.Idle) {
                        "Classic vote is collecting inside an investigation round"
                    }
                    WhodunitIds.EliminationModeId -> when (val vote = public.voteState) {
                        VoteState.Idle -> Unit
                        is VoteState.Collecting -> require(
                            clueCount == phase.index && public.timer == null && !vote.isSecondRound,
                        ) { "Elimination ballot is outside its round voting substate" }
                        is VoteState.Resolved -> require(
                            clueCount == phase.index &&
                                public.timer == null &&
                                !vote.wasKiller &&
                                vote.accusedPlayerId in public.eliminatedPlayers,
                        ) { "Elimination result cannot be acknowledged from this round state" }
                        is VoteState.Tied,
                        is VoteState.NoResolution -> throw IllegalArgumentException(
                            "Invalid elimination round vote state",
                        )
                    }
                }
            }

            WhodunitPhase.FinalVote -> {
                val vote = public.voteState as? VoteState.Collecting
                    ?: throw IllegalArgumentException("Final vote is not collecting ballots")
                require(
                    state.public.modeId == WhodunitIds.ClassicVoteModeId &&
                        public.currentRound == maximumRounds &&
                        clueCount == maximumRounds &&
                        public.timer == null &&
                        !vote.isSecondRound,
                ) { "Final-vote state is not reachable"
                }
            }

            WhodunitPhase.TiedRevote -> {
                require(public.timer == null && clueCount == public.currentRound) {
                    "Tied revote does not follow a completed round"
                }
                require(
                    public.voteState is VoteState.Tied ||
                        public.voteState is VoteState.Collecting,
                ) { "Tied-revote phase has no tie or second ballot" }
                if (state.public.modeId == WhodunitIds.ClassicVoteModeId) {
                    require(public.currentRound == maximumRounds) {
                        "Classic revote occurs before the final round"
                    }
                }
            }

            WhodunitPhase.Reveal,
            WhodunitPhase.PostGame -> {
                require(public.timer == null) { "Terminal state retains a timer" }
                require(
                    clueCount == public.currentRound ||
                        (public.currentRound > 0 && clueCount == public.currentRound - 1),
                ) { "Terminal clue history is not reachable" }
            }
        }
    }

    private fun validateTerminalOutcome(state: WhodunitState) {
        val public = state.public
        val terminal = state.phase == WhodunitPhase.Reveal || state.phase == WhodunitPhase.PostGame
        require(terminal || public.verdict == null) { "A non-terminal phase carries a verdict" }
        if (state.phase == WhodunitPhase.Reveal) {
            require(public.verdict != null) { "Reveal phase has no verdict" }
        }
        if (!terminal) return

        val hasAssignment = state.privatePerPlayer.isNotEmpty()
        if (!hasAssignment) {
            require(
                state.phase == WhodunitPhase.PostGame &&
                    public.verdict == null &&
                    public.voteState == VoteState.Idle,
            ) { "Unassigned terminal state carries a game result" }
            return
        }

        val verdict = public.verdict
        if (verdict == null) {
            require(
                state.phase == WhodunitPhase.PostGame &&
                    public.voteState == VoteState.NoResolution(EARLY_END_REASON),
            ) { "Result-less post-game state was not an explicit early end" }
            return
        }
        val verdictKillerCharacterId = when (verdict) {
            is Verdict.PlayersWin -> verdict.killerCharacterId
            is Verdict.KillerWins -> verdict.killerCharacterId
        }
        require(verdictKillerCharacterId == state.hostOnly.killerCharacterId.raw) {
            "Verdict names a different killer character"
        }
        when (verdict) {
            is Verdict.PlayersWin -> {
                val resolved = public.voteState as? VoteState.Resolved
                    ?: throw IllegalArgumentException("Players-win verdict has no resolved vote")
                require(
                    resolved.wasKiller && resolved.accusedPlayerId == state.hostOnly.killerId,
                ) { "Players-win verdict did not identify the killer" }
            }
            is Verdict.KillerWins -> when (verdict.cause) {
                KillerWinCause.InnocentAccused -> {
                    val resolved = public.voteState as? VoteState.Resolved
                        ?: throw IllegalArgumentException("Innocent-accused verdict has no vote")
                    require(!resolved.wasKiller) { "Innocent-accused verdict resolved the killer" }
                }
                KillerWinCause.TieUnresolved,
                KillerWinCause.SurvivedToFinalTwo -> require(
                    public.voteState == VoteState.Resolved(state.hostOnly.killerId, true),
                ) { "Killer survival verdict has an inconsistent result" }
                KillerWinCause.GameEndedEarly -> require(
                    public.voteState == VoteState.NoResolution(EARLY_END_REASON),
                ) { "Early-end verdict has an inconsistent result" }
            }
        }
    }

    private const val UNASSIGNED_ID = "unassigned"
    private const val MAX_REASON_LENGTH = 128
    private const val BRIEFING_CARD_COUNT = 4
    private const val EARLY_END_REASON = "game-ended-early"
}

private fun WhodunitCase.allClues(): List<Clue> = buildList {
    addAll(cluePools.publicUniversal)
    cluePools.killerPointing.values.forEach(::addAll)
    cluePools.redHerring.values.forEach(::addAll)
    cluePools.contradiction.values.forEach(::addAll)
    cluePools.finalStrong.values.forEach(::addAll)
}

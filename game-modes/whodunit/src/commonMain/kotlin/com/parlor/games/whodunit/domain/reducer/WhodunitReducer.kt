package com.parlor.games.whodunit.domain.reducer

import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState

/**
 * Pure reducer for Whodunit's lifecycle, gameplay, voting, reveal, replay, and
 * safety actions. Time, randomness, and content arrive via [WhodunitReducerContext].
 */
object WhodunitReducer : GameReducer<WhodunitState, WhodunitAction, WhodunitEvent>() {

    override fun reduce(
        state: WhodunitState,
        action: WhodunitAction,
        ctx: ReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val wctx = ctx as? WhodunitReducerContext
            ?: error("WhodunitReducer requires WhodunitReducerContext")
        return when (action) {
            // Lifecycle / reveal (Phase 4)
            is WhodunitAction.AssignRoles -> assignRoles(state, action.seed, wctx)
            WhodunitAction.AdvanceFromIntro -> advance(state, WhodunitPhase.RulesBriefing)
            is WhodunitAction.AdvanceBriefingCard -> advanceBriefingCard(state, action.index)
            is WhodunitAction.StartCharacterReveal -> startCharacterReveal(state, action.playerId)
            is WhodunitAction.CompleteCharacterReveal -> completeCharacterReveal(state, action.playerId)
            is WhodunitAction.OpenPrivateReview -> openPrivateReview(state, action.playerId)
            is WhodunitAction.CloseHide -> closeHide(state, action.playerId)

            // Rounds (Phase 5)
            WhodunitAction.RevealNextClue -> revealNextClue(state, wctx)
            is WhodunitAction.SubmitStructuredAction -> Reduction(state)
            is WhodunitAction.StartDiscussionTimer -> startDiscussionTimer(state, action.seconds)
            WhodunitAction.PauseDiscussionTimer -> pauseDiscussionTimer(state)
            WhodunitAction.ResumeDiscussionTimer -> resumeDiscussionTimer(state)
            is WhodunitAction.TimerTicked -> timerTicked(state, action.remainingSeconds)
            WhodunitAction.TimerExpired -> timerExpired(state)
            WhodunitAction.AdvanceFromDiscussion -> advanceFromDiscussion(state)

            // Voting (Phase 5)
            WhodunitAction.OpenVote -> openVote(state)
            is WhodunitAction.CastVote -> castVote(state, action.voter, action.target)
            is WhodunitAction.AbstainVote -> abstainVote(state, action.voter, refused = false)
            is WhodunitAction.RefuseToVote -> abstainVote(state, action.voter, refused = true)
            WhodunitAction.CloseVote -> closeVote(state)
            WhodunitAction.AcknowledgeRevealCard -> Reduction(state)
            WhodunitAction.AcknowledgeReveal -> advance(state, WhodunitPhase.PostGame)
            WhodunitAction.BeginReplay -> beginReplay(state, wctx)

            // Safety (Phase 6)
            WhodunitAction.Pause -> pauseSession(state)
            WhodunitAction.Resume -> resumeSession(state)
            is WhodunitAction.EndGameEarly -> endGameEarly(state, action.withReveal)
            WhodunitAction.RequestReroll -> reroll(state, wctx)
        }
    }

    // ============================================================ Setup / Reveal (Phase 4) ==

    private fun assignRoles(
        state: WhodunitState,
        seed: Long,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val random = RandomSource.seeded(seed)
        val players = state.players
        val characters = ctx.case.characters
        require(players.size <= characters.size)

        val picked = random.shuffled(characters).take(players.size)
        val seatToCharacter: Map<PlayerId, CharacterId> = players
            .zip(picked)
            .associate { (p, c) -> p.id to CharacterId(c.id) }

        val killerPlayer = random.pick(players)
        val killerCharacterId = seatToCharacter.getValue(killerPlayer.id)
        val killerChar = characters.first { it.id == killerCharacterId.raw }
        val deflection = killerChar.guiltyBrief.deflectionTargets
            .map { CharacterId(it) }
            .filter { target -> seatToCharacter.values.any { it == target } }

        val privates = players.associate { p ->
            val role = if (p.id == killerPlayer.id) PlayerRole.Killer else PlayerRole.Innocent
            p.id to WhodunitPrivate(role = role, characterId = seatToCharacter.getValue(p.id))
        }

        val newState = state.copy(
            privatePerPlayer = privates,
            hostOnly = WhodunitHostOnly(
                killerId = killerPlayer.id,
                killerCharacterId = killerCharacterId,
                randomSeed = seed,
                seatToCharacter = seatToCharacter,
                redHerringTargets = deflection,
            ),
            phase = WhodunitPhase.PublicIntro,
        )
        return Reduction(
            newState,
            listOf(WhodunitEvent.RolesAssigned, WhodunitEvent.PhaseEntered(newState.phase)),
        )
    }

    private fun advance(state: WhodunitState, next: WhodunitPhase): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase == next) return Reduction(state)
        return Reduction(state.copy(phase = next), listOf(WhodunitEvent.PhaseEntered(next)))
    }

    private fun advanceBriefingCard(state: WhodunitState, index: Int): Reduction<WhodunitState, WhodunitEvent> {
        val newIndex = index.coerceAtLeast(0)
        return if (newIndex >= BRIEFING_CARD_COUNT) {
            val newState = state.copy(
                phase = WhodunitPhase.CharacterReveal(playerIndex = 0),
                public = state.public.copy(briefingCardIndex = 0),
            )
            Reduction(newState, listOf(WhodunitEvent.PhaseEntered(newState.phase)))
        } else {
            Reduction(state.copy(public = state.public.copy(briefingCardIndex = newIndex)))
        }
    }

    private fun startCharacterReveal(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val priv = state.privatePerPlayer[playerId] ?: return Reduction(state)
        val updated = state.privatePerPlayer + (playerId to priv.copy(dossierUnlocked = true))
        return Reduction(
            state.copy(privatePerPlayer = updated),
            listOf(WhodunitEvent.PrivateRevealRequested(playerId)),
        )
    }

    private fun completeCharacterReveal(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val phase = state.phase as? WhodunitPhase.CharacterReveal ?: return Reduction(state)
        val current = state.players.getOrNull(phase.playerIndex) ?: return Reduction(state)
        if (current.id != playerId) return Reduction(state)

        val nextIndex = phase.playerIndex + 1
        val priv = state.privatePerPlayer[playerId]
        val updated = if (priv != null) state.privatePerPlayer + (playerId to priv.copy(dossierUnlocked = false)) else state.privatePerPlayer

        return if (nextIndex >= state.players.size) {
            val newState = state.copy(
                privatePerPlayer = updated,
                phase = WhodunitPhase.Round(index = 1),
                public = state.public.copy(currentRound = 1),
            )
            Reduction(newState, listOf(WhodunitEvent.PhaseEntered(newState.phase)))
        } else {
            val nextPhase = WhodunitPhase.CharacterReveal(playerIndex = nextIndex)
            Reduction(state.copy(privatePerPlayer = updated, phase = nextPhase), listOf(WhodunitEvent.PhaseEntered(nextPhase)))
        }
    }

    private fun openPrivateReview(state: WhodunitState, playerId: PlayerId): Reduction<WhodunitState, WhodunitEvent> {
        if (state.public.voteState is VoteState.Collecting) return Reduction(state)
        val priv = state.privatePerPlayer[playerId] ?: return Reduction(state)
        val updated = state.privatePerPlayer + (playerId to priv.copy(privateReviewOpen = true))
        return Reduction(state.copy(privatePerPlayer = updated), listOf(WhodunitEvent.PrivateRevealRequested(playerId)))
    }

    private fun closeHide(state: WhodunitState, playerId: PlayerId): Reduction<WhodunitState, WhodunitEvent> {
        val priv = state.privatePerPlayer[playerId] ?: return Reduction(state)
        val updated = state.privatePerPlayer + (playerId to priv.copy(privateReviewOpen = false, dossierUnlocked = false))
        return Reduction(state.copy(privatePerPlayer = updated))
    }

    // ======================================================================= Rounds (P5) ==

    private fun revealNextClue(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val round = state.phase as? WhodunitPhase.Round ?: return Reduction(state)
        val clue = pickNextClue(state, ctx, round.index) ?: return Reduction(state)
        val revealed = RevealedClue(id = ClueId(clue.id), text = clue.text, roundIndex = round.index)
        val newPublic = state.public.copy(revealedClues = state.public.revealedClues + revealed)
        val newHostOnly = state.hostOnly.copy(drawnClueIds = state.hostOnly.drawnClueIds + ClueId(clue.id))
        return Reduction(
            state.copy(public = newPublic, hostOnly = newHostOnly),
            listOf(WhodunitEvent.ClueRevealed(ClueId(clue.id), clue.text, round.index)),
        )
    }

    private fun pickNextClue(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
        roundIndex: Int,
    ): Clue? {
        val pools = ctx.case.cluePools
        val killerCharId = state.hostOnly.killerCharacterId.raw
        val drawn = state.hostOnly.drawnClueIds
        val random = RandomSource.seeded(state.hostOnly.randomSeed xor roundIndex.toLong().shl(8))

        val lastRound = isLastRound(state.players.size, roundIndex)

        val candidatePool: List<Clue> = when {
            lastRound -> pools.finalStrong[killerCharId].orEmpty()
            roundIndex == 1 -> pools.publicUniversal +
                pools.killerPointing[killerCharId].orEmpty()
            else -> pools.killerPointing[killerCharId].orEmpty() +
                pools.contradiction[killerCharId].orEmpty() +
                pools.redHerring[killerCharId].orEmpty()
        }

        val available = candidatePool.filterNot { ClueId(it.id) in drawn }
        return available.takeIf { it.isNotEmpty() }?.let { random.pick(it) }
    }

    private fun isLastRound(playerCount: Int, roundIndex: Int): Boolean =
        if (playerCount <= 4) roundIndex >= 3 else roundIndex >= 4

    private fun startDiscussionTimer(state: WhodunitState, seconds: Int): Reduction<WhodunitState, WhodunitEvent> {
        val timer = PublicTimerState(
            timerId = "discussion-${state.public.currentRound}",
            totalSeconds = seconds,
            remainingSeconds = seconds,
            paused = false,
        )
        return Reduction(state.copy(public = state.public.copy(timer = timer)), listOf(WhodunitEvent.TimerStarted(seconds)))
    }

    private fun pauseDiscussionTimer(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val t = state.public.timer ?: return Reduction(state)
        return Reduction(state.copy(public = state.public.copy(timer = t.copy(paused = true))))
    }

    private fun resumeDiscussionTimer(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val t = state.public.timer ?: return Reduction(state)
        return Reduction(state.copy(public = state.public.copy(timer = t.copy(paused = false))))
    }

    private fun timerTicked(state: WhodunitState, remaining: Int): Reduction<WhodunitState, WhodunitEvent> {
        // Session pause and per-timer pause both freeze the timer — a tick
        // arriving while either flag is set is a no-op (the ticker coroutine
        // races with the pause action and we don't want stale ticks to slip
        // through after the user has paused).
        if (state.public.paused) return Reduction(state)
        val t = state.public.timer ?: return Reduction(state)
        if (t.paused) return Reduction(state)
        val clamped = remaining.coerceAtLeast(0)
        val newT = t.copy(remainingSeconds = clamped)
        val events = mutableListOf<WhodunitEvent>()
        if (clamped in 1..10 && t.remainingSeconds > 10) {
            events += WhodunitEvent.TimerWarning(clamped)
        }
        return Reduction(state.copy(public = state.public.copy(timer = newT)), events)
    }

    private fun timerExpired(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> =
        Reduction(state.copy(public = state.public.copy(timer = null)), listOf(WhodunitEvent.TimerExhausted))

    private fun advanceFromDiscussion(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val round = state.phase as? WhodunitPhase.Round ?: return Reduction(state)
        val isElimination = state.public.modeId == WhodunitIds.EliminationModeId
        val playerCount = state.public.playersAtTable.size
        val lastRound = isLastRound(playerCount, round.index)

        return if (isElimination || lastRound) {
            val target = if (lastRound && !isElimination) WhodunitPhase.FinalVote else state.phase
            openVote(state.copy(phase = target))
        } else {
            val next = WhodunitPhase.Round(round.index + 1)
            val newState = state.copy(
                phase = next,
                public = state.public.copy(currentRound = round.index + 1, timer = null),
            )
            Reduction(newState, listOf(WhodunitEvent.PhaseEntered(next)))
        }
    }

    // ====================================================================== Voting (P5) ==

    private fun openVote(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val isElimination = state.public.modeId == WhodunitIds.EliminationModeId
        // If we're opening from a Tied state, this is the revote — preserve
        // that marker forward so handleTie can apply the second-tie rule.
        val isSecondRound = state.public.voteState is VoteState.Tied
        val survivors = state.public.playersAtTable.map { it.id } - state.public.eliminatedPlayers.toSet()
        val ballot = if (isElimination) survivors else state.public.playersAtTable.map { it.id }
        val vote = VoteState.Collecting(
            isElimination = isElimination,
            ballotPlayerIds = ballot,
            castSoFar = emptyMap(),
            abstained = emptySet(),
            currentVoterIndex = 0,
            isSecondRound = isSecondRound,
        )
        return Reduction(
            state.copy(public = state.public.copy(voteState = vote)),
            listOf(WhodunitEvent.VoteOpened),
        )
    }

    private fun castVote(
        state: WhodunitState,
        voter: PlayerId,
        target: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val vote = state.public.voteState as? VoteState.Collecting ?: return Reduction(state)
        if (voter !in vote.ballotPlayerIds) return Reduction(state)
        val advanced = vote.copy(
            castSoFar = vote.castSoFar + (voter to target),
            abstained = vote.abstained - voter,
            currentVoterIndex = vote.currentVoterIndex + 1,
        )
        return Reduction(
            state.copy(public = state.public.copy(voteState = advanced)),
            listOf(WhodunitEvent.VoteCast(voter, target)),
        )
    }

    /**
     * Abstain/refuse share tally semantics: the voter contributes no count and
     * the ballot pointer advances. Refusal additionally emits [WhodunitEvent.VoteRefused]
     * so UI / telemetry can distinguish "no opinion" from "protest."
     */
    private fun abstainVote(
        state: WhodunitState,
        voter: PlayerId,
        refused: Boolean,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val vote = state.public.voteState as? VoteState.Collecting ?: return Reduction(state)
        if (voter !in vote.ballotPlayerIds) return Reduction(state)
        val advanced = vote.copy(
            abstained = vote.abstained + voter,
            currentVoterIndex = vote.currentVoterIndex + 1,
        )
        val events: List<WhodunitEvent> = if (refused) listOf(WhodunitEvent.VoteRefused(voter)) else emptyList()
        return Reduction(state.copy(public = state.public.copy(voteState = advanced)), events)
    }

    private fun closeVote(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val vote = state.public.voteState as? VoteState.Collecting ?: return Reduction(state)
        val tally = vote.castSoFar.values.groupingBy { it }.eachCount()

        if (tally.isEmpty()) {
            return Reduction(
                state.copy(public = state.public.copy(voteState = VoteState.NoResolution("all-abstained"))),
            )
        }

        val maxCount = tally.values.max()
        val topTargets = tally.filterValues { it == maxCount }.keys.toList()

        return if (topTargets.size > 1) {
            handleTie(state, topTargets, vote.isElimination, vote.isSecondRound)
        } else {
            resolveVote(state, topTargets.first(), vote.isElimination, tally)
        }
    }

    /**
     * Resolve a tie outcome.
     *
     * The [isSecondRound] flag arrives from the current [VoteState.Collecting]
     * — set true by [openVote] when it ran from a [VoteState.Tied] state.
     * (Reading it from `voteState` here would not work: at close-time the
     * voteState is `Collecting`, not `Tied`, so the previous reducer's
     * `priorTied?.secondRound == true` was always false — the killer-wins /
     * advance-round paths could never trigger.)
     *
     * Outcomes per design doc §12 / §13:
     *  - First tie (any mode): debate window, transition to [WhodunitPhase.TiedRevote].
     *  - Second tie, Classic: killer wins via [KillerWinCause.TieUnresolved].
     *  - Second tie, Elimination: no one is eliminated, game proceeds to the
     *    next round with `voteState = Idle`.
     */
    private fun handleTie(
        state: WhodunitState,
        tied: List<PlayerId>,
        isElimination: Boolean,
        isSecondRound: Boolean,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (isSecondRound) {
            return if (isElimination) {
                val nextRoundIndex = state.public.currentRound + 1
                val nextPhase = WhodunitPhase.Round(nextRoundIndex)
                Reduction(
                    state.copy(
                        phase = nextPhase,
                        public = state.public.copy(
                            currentRound = nextRoundIndex,
                            voteState = VoteState.Idle,
                            timer = null,
                        ),
                    ),
                    listOf(WhodunitEvent.PhaseEntered(nextPhase)),
                )
            } else {
                killerWins(state, KillerWinCause.TieUnresolved)
            }
        }
        val tiedState = VoteState.Tied(tiedPlayerIds = tied, debateSecondsRemaining = TIE_DEBATE_SECONDS)
        return Reduction(
            state.copy(public = state.public.copy(voteState = tiedState), phase = WhodunitPhase.TiedRevote),
            listOf(WhodunitEvent.VoteTied(tied), WhodunitEvent.PhaseEntered(WhodunitPhase.TiedRevote)),
        )
    }

    private fun resolveVote(
        state: WhodunitState,
        accused: PlayerId,
        isElimination: Boolean,
        tally: Map<PlayerId, Int>,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val wasKiller = accused == state.hostOnly.killerId
        val resolved = VoteState.Resolved(accusedPlayerId = accused, wasKiller = wasKiller)

        return if (isElimination) {
            val eliminated = state.public.eliminatedPlayers + accused
            val newPublic = state.public.copy(voteState = resolved, eliminatedPlayers = eliminated)

            when {
                wasKiller -> Reduction(
                    state.copy(public = newPublic, phase = WhodunitPhase.Reveal),
                    listOf(
                        WhodunitEvent.VoteTallied(tally),
                        WhodunitEvent.PlayerEliminated(accused, true),
                        WhodunitEvent.WinnerDecided(Verdict.PlayersWin(state.hostOnly.killerCharacterId.raw)),
                        WhodunitEvent.RevealNarrativePlaying,
                    ),
                )
                (state.public.playersAtTable.map { it.id } - eliminated.toSet()).size <= 2 ->
                    Reduction(
                        state.copy(public = newPublic, phase = WhodunitPhase.Reveal),
                        listOf(
                            WhodunitEvent.VoteTallied(tally),
                            WhodunitEvent.PlayerEliminated(accused, false),
                            WhodunitEvent.WinnerDecided(
                                Verdict.KillerWins(
                                    state.hostOnly.killerCharacterId.raw,
                                    KillerWinCause.SurvivedToFinalTwo,
                                ),
                            ),
                            WhodunitEvent.RevealNarrativePlaying,
                        ),
                    )
                else -> {
                    val nextRound = WhodunitPhase.Round(state.public.currentRound + 1)
                    Reduction(
                        state.copy(
                            public = newPublic.copy(
                                currentRound = state.public.currentRound + 1,
                                voteState = VoteState.Idle,
                                timer = null,
                            ),
                            phase = nextRound,
                        ),
                        listOf(
                            WhodunitEvent.VoteTallied(tally),
                            WhodunitEvent.PlayerEliminated(accused, false),
                            WhodunitEvent.PhaseEntered(nextRound),
                        ),
                    )
                }
            }
        } else {
            val verdict = if (wasKiller) {
                Verdict.PlayersWin(state.hostOnly.killerCharacterId.raw)
            } else {
                Verdict.KillerWins(state.hostOnly.killerCharacterId.raw, KillerWinCause.InnocentAccused)
            }
            Reduction(
                state.copy(public = state.public.copy(voteState = resolved), phase = WhodunitPhase.Reveal),
                listOf(
                    WhodunitEvent.VoteTallied(tally),
                    WhodunitEvent.WinnerDecided(verdict),
                    WhodunitEvent.RevealNarrativePlaying,
                ),
            )
        }
    }

    private fun killerWins(state: WhodunitState, cause: KillerWinCause): Reduction<WhodunitState, WhodunitEvent> {
        val newPublic = state.public.copy(voteState = VoteState.Resolved(state.hostOnly.killerId, true))
        return Reduction(
            state.copy(public = newPublic, phase = WhodunitPhase.Reveal),
            listOf(
                WhodunitEvent.WinnerDecided(Verdict.KillerWins(state.hostOnly.killerCharacterId.raw, cause)),
                WhodunitEvent.RevealNarrativePlaying,
            ),
        )
    }

    // ====================================================================== Replay (P5) ==

    private fun beginReplay(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val newSeed = state.hostOnly.randomSeed * 31 + 17
        val fresh = state.copy(
            public = state.public.copy(
                eliminatedPlayers = emptyList(),
                currentRound = 0,
                revealedClues = emptyList(),
                voteState = VoteState.Idle,
                briefingCardIndex = 0,
                timer = null,
                paused = false,
            ),
            privatePerPlayer = emptyMap(),
            phase = WhodunitPhase.Setup,
        )
        return assignRoles(fresh, newSeed, ctx)
    }

    // ====================================================================== Safety (P6) ==

    /**
     * Session-wide pause. Flips [WhodunitPublic.paused] and freezes any active
     * discussion timer. A snapshot persisted now resumes paused — the resume
     * UX renders the pause overlay until the player explicitly continues.
     */
    private fun pauseSession(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (state.public.paused) return Reduction(state)
        val frozenTimer = state.public.timer?.copy(paused = true)
        return Reduction(
            state.copy(public = state.public.copy(paused = true, timer = frozenTimer)),
            listOf(WhodunitEvent.PauseEngaged),
        )
    }

    private fun resumeSession(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (!state.public.paused) return Reduction(state)
        val unfrozenTimer = state.public.timer?.copy(paused = false)
        return Reduction(
            state.copy(public = state.public.copy(paused = false, timer = unfrozenTimer)),
            listOf(WhodunitEvent.PauseLifted),
        )
    }

    private fun endGameEarly(state: WhodunitState, withReveal: Boolean): Reduction<WhodunitState, WhodunitEvent> {
        return if (withReveal) {
            Reduction(
                state.copy(phase = WhodunitPhase.Reveal),
                listOf(
                    WhodunitEvent.GameEndedEarly(true),
                    WhodunitEvent.WinnerDecided(
                        Verdict.KillerWins(state.hostOnly.killerCharacterId.raw, KillerWinCause.SurvivedToFinalTwo),
                    ),
                    WhodunitEvent.RevealNarrativePlaying,
                ),
            )
        } else {
            Reduction(
                state.copy(phase = WhodunitPhase.PostGame),
                listOf(WhodunitEvent.GameEndedEarly(false), WhodunitEvent.PhaseEntered(WhodunitPhase.PostGame)),
            )
        }
    }

    private fun reroll(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val newSeed = state.hostOnly.randomSeed * 1103515245 + 12345
        val reset = state.copy(
            privatePerPlayer = emptyMap(),
            phase = WhodunitPhase.CharacterReveal(playerIndex = 0),
            public = state.public.copy(
                revealedClues = emptyList(),
                voteState = VoteState.Idle,
                currentRound = 0,
                timer = null,
            ),
        )
        val priorPhaseId = state.phase.id
        val reduction = assignRoles(reset, newSeed, ctx)
        return Reduction(
            reduction.newState.copy(phase = WhodunitPhase.CharacterReveal(0)),
            reduction.events + WhodunitEvent.RerolledAt(priorPhaseId),
        )
    }

    private const val BRIEFING_CARD_COUNT = 4
    private const val TIE_DEBATE_SECONDS = 60
}

package com.parlor.games.whodunit.domain.reducer

import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.rules.WhodunitCluePolicy
import com.parlor.games.whodunit.domain.rules.WhodunitRoundPolicy
import com.parlor.games.whodunit.domain.rules.WhodunitRules
import com.parlor.games.whodunit.domain.state.PartyReadiness
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
        if (state.public.paused && !action.isAllowedWhilePaused()) {
            return Reduction(state)
        }
        return when (action) {
            // Lifecycle / reveal (Phase 4)
            is WhodunitAction.AssignRoles -> assignRoles(state, action.seed, wctx)
            WhodunitAction.AdvanceFromIntro -> advanceFromIntro(state)
            is WhodunitAction.AdvanceBriefingCard -> advanceBriefingCard(state, action.index)
            is WhodunitAction.StartCharacterReveal -> startCharacterReveal(
                state,
                action.playerId,
                action.roleAssignmentGeneration,
            )
            is WhodunitAction.CompleteCharacterReveal -> completeCharacterReveal(
                state,
                action.playerId,
                action.roleAssignmentGeneration,
            )
            // Party Play readiness (Wave 9H)
            is WhodunitAction.AcknowledgeIntro -> acknowledgeIntro(state, action.playerId)
            is WhodunitAction.AcknowledgeBriefing -> acknowledgeBriefing(state, action.playerId)
            WhodunitAction.AdvanceFromCharacterReveal -> advanceFromCharacterReveal(state)

            // Party Play connection rules (Wave 9H)
            is WhodunitAction.MarkPlayerDisconnected -> markPlayerDisconnected(state, action.playerId)
            is WhodunitAction.MarkPlayerReconnected -> markPlayerReconnected(state, action.playerId)
            is WhodunitAction.ContinueWithoutPlayer -> continueWithoutPlayer(state, action.playerId)

            // Rounds (Phase 5)
            WhodunitAction.RevealNextClue -> revealNextClue(state, wctx)
            is WhodunitAction.StartDiscussionTimer -> startDiscussionTimer(
                state,
                action.seconds,
                wctx,
            )
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
            WhodunitAction.AcknowledgeRevealCard -> acknowledgeRevealCard(state)
            WhodunitAction.AcknowledgeReveal -> acknowledgeReveal(state)
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
        if (state.phase != WhodunitPhase.Setup) return Reduction(state)
        val players = state.players
        // UI validation is advisory. The canonical reducer rejects an unknown
        // mode, duplicate identity/seat, out-of-range count, or a case that
        // cannot supply one distinct character per player.
        if (!WhodunitRules.isValidRoster(state.public.modeId, players)) return Reduction(state)
        if (
            !WhodunitRules.isSupportedByCase(
                case = ctx.case,
                caseId = state.public.caseId,
                modeId = state.public.modeId,
                playerCount = players.size,
            )
        ) {
            return Reduction(state)
        }
        if (state.public.playersAtTable != players ||
            state.privatePerPlayer.isNotEmpty() ||
            state.hostOnly.seatToCharacter.isNotEmpty()
        ) {
            return Reduction(state)
        }
        val assignment = createRoleAssignment(players, ctx.payload.characters, seed)
            ?: return Reduction(state)
        val newState = applyRoleAssignment(state, assignment, seed, ctx.payload.characters)
            ?: return Reduction(state)
        val introState = newState.copy(phase = WhodunitPhase.PublicIntro)
        return Reduction(
            introState,
            listOf(WhodunitEvent.RolesAssigned, WhodunitEvent.PhaseEntered(introState.phase)),
        )
    }

    private data class RoleAssignment(
        val killerId: PlayerId,
        val seatToCharacter: Map<PlayerId, CharacterId>,
    )

    private fun createRoleAssignment(
        players: List<Player>,
        characters: List<Character>,
        seed: Long,
    ): RoleAssignment? {
        if (players.isEmpty() || players.size > characters.size) return null
        if (characters.map { it.id }.toSet().size != characters.size) return null
        val random = RandomSource.seeded(seed)
        val picked = random.shuffled(characters).take(players.size)
        val seatToCharacter = players.zip(picked).associate { (player, character) ->
            player.id to CharacterId(character.id)
        }
        return RoleAssignment(
            killerId = random.pick(players).id,
            seatToCharacter = seatToCharacter,
        )
    }

    private fun applyRoleAssignment(
        state: WhodunitState,
        assignment: RoleAssignment,
        seed: Long,
        characters: List<Character>,
    ): WhodunitState? {
        if (state.public.roleAssignmentGeneration == Long.MAX_VALUE) return null
        val nextGeneration = state.public.roleAssignmentGeneration + 1L
        val killerCharacterId = assignment.seatToCharacter.getValue(assignment.killerId)
        val killerCharacter = characters.first { it.id == killerCharacterId.raw }
        val selectedCharacters = assignment.seatToCharacter.values.toSet()
        val deflection = killerCharacter.guiltyBrief.deflectionTargets
            .map(::CharacterId)
            .filter(selectedCharacters::contains)
        val privateState = state.players.associate { player ->
            val role = if (player.id == assignment.killerId) PlayerRole.Killer else PlayerRole.Innocent
            player.id to WhodunitPrivate(
                role = role,
                characterId = assignment.seatToCharacter.getValue(player.id),
                deflectionTargets = if (role == PlayerRole.Killer) deflection else emptyList(),
            )
        }
        return state.copy(
            public = state.public.copy(roleAssignmentGeneration = nextGeneration),
            privatePerPlayer = privateState,
            hostOnly = WhodunitHostOnly(
                killerId = assignment.killerId,
                killerCharacterId = killerCharacterId,
                randomSeed = seed,
                seatToCharacter = assignment.seatToCharacter,
                redHerringTargets = deflection,
            ),
        )
    }

    private fun advance(state: WhodunitState, next: WhodunitPhase): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase == next) return Reduction(state)
        return Reduction(state.copy(phase = next), listOf(WhodunitEvent.PhaseEntered(next)))
    }

    /**
     * Active roster used by the readiness invariant. Subtracts
     * `public.droppedPlayers`; disconnected (transient) players still
     * count because the host hasn't yet chosen to skip them.
     */
    private fun activeRoster(state: WhodunitState) = PartyReadiness.activeRoster(
        players = state.players,
        droppedPlayers = state.public.droppedPlayers,
    )

    /**
     * Gate: AdvanceFromIntro is rejected unless every active-roster player
     * has acknowledged the case intro. Defense-in-depth: the host UI also
     * disables the Next button on the same invariant, but the reducer is
     * the canonical enforcer.
     */
    private fun advanceFromIntro(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase != WhodunitPhase.PublicIntro) return Reduction(state)
        val active = activeRoster(state)
        if (!PartyReadiness.isComplete(state.public.introAcknowledged, active)) {
            return Reduction(state)
        }
        val advanced = advance(state, WhodunitPhase.RulesBriefing)
        return advanced.copy(
            newState = advanced.newState.copy(
                public = advanced.newState.public.copy(introAcknowledged = emptySet()),
            ),
        )
    }

    private fun advanceBriefingCard(state: WhodunitState, index: Int): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase != WhodunitPhase.RulesBriefing) return Reduction(state)
        if (index != state.public.briefingCardIndex + 1) return Reduction(state)
        return if (index == BRIEFING_CARD_COUNT) {
            // Final advance: gated by briefing readiness.
            val active = activeRoster(state)
            if (!PartyReadiness.isComplete(state.public.briefingReady, active)) {
                return Reduction(state)
            }
            val newState = state.copy(
                phase = WhodunitPhase.CharacterReveal(playerIndex = 0),
                public = state.public.copy(
                    briefingCardIndex = 0,
                    briefingReady = emptySet(),
                ),
            )
            Reduction(newState, listOf(WhodunitEvent.PhaseEntered(newState.phase)))
        } else {
            Reduction(state.copy(public = state.public.copy(briefingCardIndex = index)))
        }
    }

    // -------------------------------------------------- Party readiness (9H-1) --

    private fun acknowledgeIntro(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase != WhodunitPhase.PublicIntro) return Reduction(state)
        if (playerId !in state.players.map { it.id }) return Reduction(state)
        // Terminal dropped seats cannot acknowledge a prior-phase command.
        if (playerId in state.public.droppedPlayers) return Reduction(state)
        val updated = state.public.introAcknowledged + playerId
        if (updated == state.public.introAcknowledged) return Reduction(state)
        return Reduction(state.copy(public = state.public.copy(introAcknowledged = updated)))
    }

    private fun acknowledgeBriefing(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase != WhodunitPhase.RulesBriefing) return Reduction(state)
        if (playerId !in state.players.map { it.id }) return Reduction(state)
        if (playerId in state.public.droppedPlayers) return Reduction(state)
        val updated = state.public.briefingReady + playerId
        if (updated == state.public.briefingReady) return Reduction(state)
        return Reduction(state.copy(public = state.public.copy(briefingReady = updated)))
    }

    // -------------------------------------------------- Connection rules (9H-2) --

    private fun markPlayerDisconnected(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        // PostGame is terminal. Tracking a new disconnect there only creates a
        // permanent overlay because no gameplay recovery remains to perform.
        if (state.phase == WhodunitPhase.PostGame) return Reduction(state)
        if (playerId !in state.players.map { it.id }) return Reduction(state)
        if (playerId in state.public.disconnectedPlayers) return Reduction(state)
        val activeGame = state.phase != WhodunitPhase.Setup &&
            state.phase != WhodunitPhase.Reveal &&
            state.phase != WhodunitPhase.PostGame
        val shouldEngagePause = activeGame && !state.public.paused
        return Reduction(
            state.copy(
                public = state.public.copy(
                    disconnectedPlayers = state.public.disconnectedPlayers + playerId,
                    paused = state.public.paused || activeGame,
                    timer = if (activeGame) state.public.timer?.copy(paused = true) else state.public.timer,
                ),
            ),
            if (shouldEngagePause) listOf(WhodunitEvent.PauseEngaged) else emptyList(),
        )
    }

    private fun markPlayerReconnected(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (playerId !in state.public.disconnectedPlayers) return Reduction(state)
        // Legacy dropped-player snapshots require explicit readmission; a
        // transport reconnect must not silently mutate that persisted state.
        if (playerId in state.public.droppedPlayers) return Reduction(state)
        return Reduction(
            state.copy(
                public = state.public.copy(
                    disconnectedPlayers = state.public.disconnectedPlayers - playerId,
                ),
            ),
        )
    }

    private fun continueWithoutPlayer(
        state: WhodunitState,
        playerId: PlayerId,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (playerId !in state.public.disconnectedPlayers) return Reduction(state)
        // Setup has no assigned killer or private roles to reveal. A peer lost
        // before assignment therefore cancels cleanly instead of persisting an
        // "unassigned" killer verdict and a permanent disconnect overlay.
        if (state.phase == WhodunitPhase.Setup) {
            return Reduction(
                state.copy(
                    phase = WhodunitPhase.PostGame,
                    public = state.public.copy(
                        disconnectedPlayers = emptySet(),
                        paused = false,
                        timer = null,
                    ),
                ),
                listOf(
                    WhodunitEvent.GameEndedEarly(false),
                    WhodunitEvent.PhaseEntered(WhodunitPhase.PostGame),
                ),
            )
        }
        // A missing dossier makes the case invalid. The compatibility action
        // name is retained for the wire/snapshot API, but its only legal
        // meaning is "the rejoin grace period expired: reveal and end."
        val missingSeat = state.copy(
            public = state.public.copy(
                droppedPlayers = state.public.droppedPlayers + playerId,
            ),
        )
        // The verdict already exists in Reveal. Expiry there completes the
        // result ceremony instead of delegating to EndGameEarly, which quite
        // correctly rejects an already-ended game and previously left the
        // disconnect overlay stuck forever.
        if (state.phase == WhodunitPhase.Reveal) {
            return Reduction(
                missingSeat.copy(
                    phase = WhodunitPhase.PostGame,
                    public = missingSeat.public.copy(
                        disconnectedPlayers = missingSeat.public.disconnectedPlayers - playerId,
                        paused = false,
                        timer = null,
                    ),
                ),
                listOf(WhodunitEvent.PhaseEntered(WhodunitPhase.PostGame)),
            )
        }
        return endGameEarly(missingSeat, withReveal = true)
    }

    private fun startCharacterReveal(
        state: WhodunitState,
        playerId: PlayerId,
        roleAssignmentGeneration: Long,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase !is WhodunitPhase.CharacterReveal) return Reduction(state)
        if (roleAssignmentGeneration != state.public.roleAssignmentGeneration) return Reduction(state)
        if (playerId in state.public.droppedPlayers) return Reduction(state)
        if (playerId in state.public.rolesViewed) return Reduction(state)
        val priv = state.privatePerPlayer[playerId] ?: return Reduction(state)
        if (priv.dossierUnlocked) return Reduction(state)
        val updated = state.privatePerPlayer + (playerId to priv.copy(dossierUnlocked = true))
        return Reduction(
            state.copy(privatePerPlayer = updated),
            listOf(WhodunitEvent.PrivateRevealRequested(playerId)),
        )
    }

    /**
     * Wave 9H: CompleteCharacterReveal is repurposed as the per-player
     * "I'm done viewing my role" signal — it locks the dossier and adds
     * the player to `rolesViewed`. It NO LONGER auto-advances the phase.
     * The host explicitly fires `AdvanceFromCharacterReveal` when all
     * active-roster players have confirmed.
     *
     * `phase.playerIndex` remains at 0 because the simultaneous-reveal model
     * does not sequence players. The field remains serialized as part of the
     * current state schema and validators require this canonical value.
     */
    private fun completeCharacterReveal(
        state: WhodunitState,
        playerId: PlayerId,
        roleAssignmentGeneration: Long,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase !is WhodunitPhase.CharacterReveal) return Reduction(state)
        if (roleAssignmentGeneration != state.public.roleAssignmentGeneration) return Reduction(state)
        if (playerId !in state.players.map { it.id }) return Reduction(state)
        if (playerId in state.public.droppedPlayers) return Reduction(state)
        val priv = state.privatePerPlayer[playerId]
        if (priv == null || !priv.dossierUnlocked) return Reduction(state)
        val updatedPriv = state.privatePerPlayer + (
            playerId to priv.copy(
                dossierUnlocked = false,
                privateReviewOpen = false,
            )
        )
        val updatedRolesViewed = state.public.rolesViewed + playerId
        return Reduction(
            state.copy(
                privatePerPlayer = updatedPriv,
                public = state.public.copy(rolesViewed = updatedRolesViewed),
            ),
        )
    }

    /**
     * Host signals "everyone has viewed their role, move to Round 1".
     * Gated by `PartyReadiness.isComplete(rolesViewed, activeRoster)`.
     * Clears `rolesViewed` on success.
     */
    private fun advanceFromCharacterReveal(
        state: WhodunitState,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase !is WhodunitPhase.CharacterReveal) return Reduction(state)
        val active = activeRoster(state)
        if (!PartyReadiness.isComplete(state.public.rolesViewed, active)) {
            return Reduction(state)
        }
        val newState = state.copy(
            phase = WhodunitPhase.Round(index = 1),
            public = state.public.copy(
                currentRound = 1,
                rolesViewed = emptySet(),
            ),
            privatePerPlayer = state.privatePerPlayer.mapValues { (_, privateState) ->
                privateState.copy(dossierUnlocked = false, privateReviewOpen = false)
            },
        )
        return Reduction(newState, listOf(WhodunitEvent.PhaseEntered(newState.phase)))
    }

    // ======================================================================= Rounds (P5) ==

    private fun revealNextClue(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val round = state.phase as? WhodunitPhase.Round ?: return Reduction(state)
        if (state.public.voteState != VoteState.Idle || state.public.timer != null) {
            return Reduction(state)
        }
        if (state.public.revealedClues.any { it.roundIndex == round.index }) {
            return Reduction(state)
        }
        val clue = WhodunitCluePolicy.select(
            case = ctx.payload,
            killerCharacterId = state.hostOnly.killerCharacterId,
            modeId = state.public.modeId,
            playerCount = state.players.size,
            randomSeed = state.hostOnly.randomSeed,
            roundIndex = round.index,
            drawnClueIds = state.hostOnly.drawnClueIds,
        ) ?: return Reduction(state)
        val revealed = RevealedClue(id = ClueId(clue.id), text = clue.text, roundIndex = round.index)
        val newPublic = state.public.copy(revealedClues = state.public.revealedClues + revealed)
        val newHostOnly = state.hostOnly.copy(drawnClueIds = state.hostOnly.drawnClueIds + ClueId(clue.id))
        return Reduction(
            state.copy(public = newPublic, hostOnly = newHostOnly),
            listOf(WhodunitEvent.ClueRevealed(ClueId(clue.id), clue.text, round.index)),
        )
    }

    private fun isLastRound(modeId: com.parlor.core.ids.ModeId, playerCount: Int, roundIndex: Int): Boolean =
        WhodunitRules.maximumRoundCount(modeId, playerCount)?.let { roundIndex >= it } ?: false

    private fun startDiscussionTimer(
        state: WhodunitState,
        seconds: Int,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val round = state.phase as? WhodunitPhase.Round ?: return Reduction(state)
        if (state.public.voteState != VoteState.Idle || state.public.timer != null) {
            return Reduction(state)
        }
        if (state.public.revealedClues.none { it.roundIndex == round.index }) {
            return Reduction(state)
        }
        val authoredSeconds = WhodunitRoundPolicy.discussionSeconds(
            case = ctx.payload,
            roundIndex = round.index,
            playerCount = state.players.size,
        )
        if (seconds != authoredSeconds) {
            return Reduction(state)
        }
        val timer = PublicTimerState(
            timerId = "discussion-${state.public.currentRound}",
            totalSeconds = seconds,
            remainingSeconds = seconds,
            paused = false,
        )
        return Reduction(state.copy(public = state.public.copy(timer = timer)), listOf(WhodunitEvent.TimerStarted(seconds)))
    }

    private fun pauseDiscussionTimer(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase !is WhodunitPhase.Round) return Reduction(state)
        val t = state.public.timer ?: return Reduction(state)
        if (t.paused) return Reduction(state)
        return Reduction(state.copy(public = state.public.copy(timer = t.copy(paused = true))))
    }

    private fun resumeDiscussionTimer(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase !is WhodunitPhase.Round) return Reduction(state)
        val t = state.public.timer ?: return Reduction(state)
        if (!t.paused) return Reduction(state)
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
        // A delayed/duplicated ticker action must never move time backwards
        // (increase the remaining duration) or emit a second warning.
        if (clamped >= t.remainingSeconds) return Reduction(state)
        val newT = t.copy(remainingSeconds = clamped)
        val events = mutableListOf<WhodunitEvent>()
        if (clamped in 1..TIMER_WARNING_SECONDS && t.remainingSeconds > TIMER_WARNING_SECONDS) {
            events += WhodunitEvent.TimerWarning(clamped)
        }
        return Reduction(state.copy(public = state.public.copy(timer = newT)), events)
    }

    private fun timerExpired(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val timer = state.public.timer ?: return Reduction(state)
        if (state.phase !is WhodunitPhase.Round || timer.paused) return Reduction(state)
        // TimerExpired is the ticker's terminal edge, not a host shortcut for
        // skipping an arbitrary amount of discussion. The ticker submits it
        // when one second remains; explicit host progression uses
        // AdvanceFromDiscussion. Keeping the distinction in the reducer makes
        // an early/stale expiry action harmless even outside the UI path.
        if (timer.remainingSeconds > 1) return Reduction(state)

        // Expiry is a real state-machine transition, not merely a timer clear.
        // Clearing in-place made the router redisplay the clue CTA, which could
        // restart the same discussion forever.
        val progressed = advanceFromDiscussion(state)
        if (progressed.newState == state) return Reduction(state)
        return progressed.copy(events = listOf(WhodunitEvent.TimerExhausted) + progressed.events)
    }

    private fun advanceFromDiscussion(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val round = state.phase as? WhodunitPhase.Round ?: return Reduction(state)
        if (state.public.timer == null || state.public.voteState != VoteState.Idle) {
            return Reduction(state)
        }
        if (state.public.revealedClues.none { it.roundIndex == round.index }) {
            return Reduction(state)
        }
        val isElimination = state.public.modeId == WhodunitIds.EliminationModeId
        val playerCount = state.public.playersAtTable.size
        val lastRound = isLastRound(state.public.modeId, playerCount, round.index)

        return if (isElimination || lastRound) {
            val target = if (lastRound && !isElimination) WhodunitPhase.FinalVote else state.phase
            openVote(state.copy(phase = target, public = state.public.copy(timer = null)))
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
        val tied = state.public.voteState as? VoteState.Tied
        val isSecondRound = tied != null
        val validEntry = when {
            tied != null -> state.phase == WhodunitPhase.TiedRevote
            isElimination -> state.phase is WhodunitPhase.Round &&
                state.public.voteState == VoteState.Idle &&
                state.public.timer == null
            else -> state.phase == WhodunitPhase.FinalVote &&
                state.public.voteState == VoteState.Idle
        }
        if (!validEntry) return Reduction(state)

        val tableIds = state.public.playersAtTable.map { it.id }
        // Active roster excludes dropped players (Wave 9H-2 — host has
        // explicitly chosen to continue without them).
        val active = tableIds - state.public.droppedPlayers
        val survivors = active - state.public.eliminatedPlayers.toSet()
        val ballot = if (isElimination) survivors else active
        val candidates = tied
            ?.tiedPlayerIds
            ?.filter { it in ballot }
            ?: ballot
        if (ballot.isEmpty() || candidates.isEmpty()) return Reduction(state)
        val vote = VoteState.Collecting(
            isElimination = isElimination,
            ballotPlayerIds = ballot,
            candidatePlayerIds = candidates,
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
        if (voter in state.public.droppedPlayers) return Reduction(state)
        // A terminal dropped seat cannot be used by a delayed/replayed vote.
        if (target in state.public.droppedPlayers) return Reduction(state)
        val vote = state.public.voteState as? VoteState.Collecting ?: return Reduction(state)
        if (voter !in vote.ballotPlayerIds) return Reduction(state)
        if (target !in vote.candidatePlayerIds) return Reduction(state)
        if (target in state.public.eliminatedPlayers) return Reduction(state)
        if (voter == target) return Reduction(state)
        if (vote.ballotPlayerIds.getOrNull(vote.currentVoterIndex) != voter) {
            return Reduction(state)
        }
        // First submission wins. Duplicate, delayed, or malicious recasts are
        // idempotent no-ops and cannot alter a secret ballot already cast.
        if (voter in vote.castSoFar || voter in vote.abstained) return Reduction(state)
        val advanced = vote.copy(
            castSoFar = vote.castSoFar + (voter to target),
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
     * so UI observers can distinguish "no opinion" from "protest."
     */
    private fun abstainVote(
        state: WhodunitState,
        voter: PlayerId,
        refused: Boolean,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (voter in state.public.droppedPlayers) return Reduction(state)
        val vote = state.public.voteState as? VoteState.Collecting ?: return Reduction(state)
        if (voter !in vote.ballotPlayerIds) return Reduction(state)
        if (vote.ballotPlayerIds.getOrNull(vote.currentVoterIndex) != voter) {
            return Reduction(state)
        }
        if (voter in vote.castSoFar || voter in vote.abstained) return Reduction(state)
        val advanced = vote.copy(
            abstained = vote.abstained + voter,
            currentVoterIndex = vote.currentVoterIndex + 1,
        )
        val events: List<WhodunitEvent> = if (refused) listOf(WhodunitEvent.VoteRefused(voter)) else emptyList()
        return Reduction(state.copy(public = state.public.copy(voteState = advanced)), events)
    }

    private fun closeVote(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val vote = state.public.voteState as? VoteState.Collecting ?: return Reduction(state)
        val completed = vote.castSoFar.keys + vote.abstained
        if (!completed.containsAll(vote.ballotPlayerIds)) return Reduction(state)
        val tally = vote.castSoFar.values.groupingBy { it }.eachCount()

        if (tally.isEmpty()) {
            // Every voter abstained or refused — the table failed to accuse
            // anyone. Resolve it the same way an unresolved tie does (design
            // doc §13: the table not deciding favours the killer / yields no
            // elimination) instead of parking on a `NoResolution` state that
            // no screen consumes — that left the game on a permanent blank
            // screen with no host escape.
            return if (vote.isElimination) {
                continueAfterUnresolvedEliminationVote(state)
            } else {
                killerWins(state, KillerWinCause.TieUnresolved)
            }
        }

        val maxCount = tally.values.max()
        val topTargets = vote.candidatePlayerIds.filter { tally[it] == maxCount }

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
     *  - First tie (any mode): host-paced defense, transition to [WhodunitPhase.TiedRevote].
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
                continueAfterUnresolvedEliminationVote(state)
            } else {
                killerWins(state, KillerWinCause.TieUnresolved)
            }
        }
        val tiedState = VoteState.Tied(
            tiedPlayerIds = tied,
            // Tied revotes are host-paced. This compatibility field is kept
            // at zero so old serialized shapes still decode without implying
            // a countdown that no authoritative job advances.
            debateSecondsRemaining = 0,
        )
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
                wasKiller -> {
                    val verdict = Verdict.PlayersWin(state.hostOnly.killerCharacterId.raw)
                    Reduction(
                        state.copy(public = newPublic.copy(verdict = verdict), phase = WhodunitPhase.Reveal),
                        listOf(
                            WhodunitEvent.VoteTallied(tally),
                            WhodunitEvent.PlayerEliminated(accused, true),
                            WhodunitEvent.WinnerDecided(verdict),
                            WhodunitEvent.RevealNarrativePlaying,
                        ),
                    )
                }
                state.public.playersAtTable
                    .map { it.id }
                    .filterNot { it in eliminated }
                    .filterNot { it in state.public.droppedPlayers }
                    .size <= 2 -> {
                    val verdict = Verdict.KillerWins(
                        state.hostOnly.killerCharacterId.raw,
                        KillerWinCause.SurvivedToFinalTwo,
                    )
                    Reduction(
                        state.copy(public = newPublic.copy(verdict = verdict), phase = WhodunitPhase.Reveal),
                        listOf(
                            WhodunitEvent.VoteTallied(tally),
                            WhodunitEvent.PlayerEliminated(accused, false),
                            WhodunitEvent.WinnerDecided(verdict),
                            WhodunitEvent.RevealNarrativePlaying,
                        ),
                    )
                }
                else -> {
                    // Innocent eliminated, game continues. Hold the room on
                    // the Resolved announcement screen so the table sees the
                    // verdict for the eliminated player ("[Name] was innocent.
                    // The killer is still among you." — design doc §13). The
                    // host's AcknowledgeRevealCard advances to the next round.
                    //
                    // Normalise the phase back to the current Round. A *revote*
                    // (opened from a tie) reaches this branch with
                    // phase == TiedRevote; acknowledgeRevealCard only fires when
                    // phase is a Round, so without this the room is stranded on
                    // a blank TiedRevote screen with no way forward. On the
                    // first-round path the phase is already Round(currentRound),
                    // so this is a no-op there.
                    Reduction(
                        state.copy(
                            phase = WhodunitPhase.Round(state.public.currentRound),
                            public = newPublic.copy(timer = null),
                        ),
                        listOf(
                            WhodunitEvent.VoteTallied(tally),
                            WhodunitEvent.PlayerEliminated(accused, false),
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
                state.copy(
                    public = state.public.copy(voteState = resolved, verdict = verdict),
                    phase = WhodunitPhase.Reveal,
                ),
                listOf(
                    WhodunitEvent.VoteTallied(tally),
                    WhodunitEvent.WinnerDecided(verdict),
                    WhodunitEvent.RevealNarrativePlaying,
                ),
            )
        }
    }

    /**
     * Consume the elimination-mode "innocent eliminated, game continues"
     * announcement and advance to the next round. The reducer holds the room
     * on `voteState = Resolved(wasKiller = false)` after such a vote so the
     * table sees who was wrongly eliminated; AcknowledgeRevealCard is the
     * host's tap-through that clears the announcement.
     *
     * Idempotent and shape-gated: only fires when we're in a Round phase with
     * a Resolved-not-killer voteState. Otherwise a no-op so the action is safe
     * to land late (e.g. a retried network submit).
     */
    private fun acknowledgeRevealCard(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        val phase = state.phase as? WhodunitPhase.Round ?: return Reduction(state)
        val resolved = state.public.voteState as? VoteState.Resolved ?: return Reduction(state)
        if (resolved.wasKiller) return Reduction(state)
        val maximumRounds = WhodunitRules.maximumRoundCount(
            state.public.modeId,
            state.public.playersAtTable.size,
        ) ?: return Reduction(state)
        // An unresolved earlier ballot can leave more than two survivors at
        // the final authored evidence round. The investigation is still over:
        // advancing again would eventually exhaust every unique clue and trap
        // the room on an unrevealable round.
        if (phase.index >= maximumRounds) {
            return killerWins(state, KillerWinCause.TieUnresolved)
        }
        val nextRoundIndex = phase.index + 1
        val nextPhase = WhodunitPhase.Round(nextRoundIndex)
        return Reduction(
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
    }

    private fun continueAfterUnresolvedEliminationVote(
        state: WhodunitState,
    ): Reduction<WhodunitState, WhodunitEvent> {
        val survivors = state.public.playersAtTable.map { it.id }
            .filterNot { it in state.public.eliminatedPlayers }
            .filterNot { it in state.public.droppedPlayers }
        if (survivors.size <= 2) {
            return killerWins(state, KillerWinCause.SurvivedToFinalTwo)
        }
        val maximumRounds = WhodunitRules.maximumRoundCount(
            state.public.modeId,
            state.public.playersAtTable.size,
        ) ?: return Reduction(state)
        if (state.public.currentRound >= maximumRounds) {
            return killerWins(state, KillerWinCause.TieUnresolved)
        }
        val nextRoundIndex = state.public.currentRound + 1
        val nextPhase = WhodunitPhase.Round(nextRoundIndex)
        return Reduction(
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
    }

    private fun killerWins(state: WhodunitState, cause: KillerWinCause): Reduction<WhodunitState, WhodunitEvent> {
        val verdict = Verdict.KillerWins(state.hostOnly.killerCharacterId.raw, cause)
        val newPublic = state.public.copy(
            voteState = VoteState.Resolved(state.hostOnly.killerId, true),
            verdict = verdict,
        )
        return Reduction(
            state.copy(public = newPublic, phase = WhodunitPhase.Reveal),
            listOf(
                WhodunitEvent.WinnerDecided(verdict),
                WhodunitEvent.RevealNarrativePlaying,
            ),
        )
    }

    private fun acknowledgeReveal(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (
            state.phase != WhodunitPhase.Reveal ||
            state.public.verdict == null ||
            state.public.disconnectedPlayers.isNotEmpty()
        ) {
            return Reduction(state)
        }
        return advance(state, WhodunitPhase.PostGame)
    }

    // ====================================================================== Replay (P5) ==

    private fun beginReplay(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase != WhodunitPhase.PostGame) return Reduction(state)
        if (
            state.public.disconnectedPlayers.isNotEmpty() ||
            state.public.droppedPlayers.isNotEmpty()
        ) {
            return Reduction(state)
        }
        val newSeed = state.hostOnly.randomSeed * REMATCH_SEED_MULTIPLIER + REMATCH_SEED_INCREMENT
        val fresh = state.copy(
            public = state.public.copy(
                eliminatedPlayers = emptyList(),
                currentRound = 0,
                revealedClues = emptyList(),
                voteState = VoteState.Idle,
                briefingCardIndex = 0,
                timer = null,
                paused = false,
                verdict = null,
                introAcknowledged = emptySet(),
                briefingReady = emptySet(),
                rolesViewed = emptySet(),
                disconnectedPlayers = emptySet(),
                droppedPlayers = emptySet(),
            ),
            privatePerPlayer = emptyMap(),
            hostOnly = WhodunitHostOnly(
                killerId = PlayerId(UNASSIGNED_ID),
                killerCharacterId = CharacterId(UNASSIGNED_ID),
                randomSeed = newSeed,
                seatToCharacter = emptyMap(),
                redHerringTargets = emptyList(),
                drawnClueIds = emptySet(),
            ),
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
        if (state.phase !is WhodunitPhase.Round) return Reduction(state)
        if (state.public.voteState is VoteState.Collecting) return Reduction(state)
        if (state.privatePerPlayer.values.any { it.privateReviewOpen || it.dossierUnlocked }) {
            return Reduction(state)
        }
        val frozenTimer = state.public.timer?.copy(paused = true)
        return Reduction(
            state.copy(public = state.public.copy(paused = true, timer = frozenTimer)),
            listOf(WhodunitEvent.PauseEngaged),
        )
    }

    private fun resumeSession(state: WhodunitState): Reduction<WhodunitState, WhodunitEvent> {
        if (!state.public.paused) return Reduction(state)
        if (state.public.disconnectedPlayers.isNotEmpty()) return Reduction(state)
        val unfrozenTimer = state.public.timer?.copy(paused = false)
        return Reduction(
            state.copy(public = state.public.copy(paused = false, timer = unfrozenTimer)),
            listOf(WhodunitEvent.PauseLifted),
        )
    }

    private fun endGameEarly(state: WhodunitState, withReveal: Boolean): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase == WhodunitPhase.Setup ||
            state.phase == WhodunitPhase.Reveal ||
            state.phase == WhodunitPhase.PostGame
        ) {
            return Reduction(state)
        }
        return if (withReveal) {
            val verdict = Verdict.KillerWins(
                state.hostOnly.killerCharacterId.raw,
                KillerWinCause.GameEndedEarly,
            )
            Reduction(
                state.copy(
                    public = state.public.copy(
                        verdict = verdict,
                        voteState = VoteState.NoResolution(EARLY_END_REASON),
                        briefingCardIndex = 0,
                        introAcknowledged = emptySet(),
                        briefingReady = emptySet(),
                        rolesViewed = emptySet(),
                        paused = false,
                        timer = null,
                        disconnectedPlayers = emptySet(),
                    ),
                    privatePerPlayer = state.privatePerPlayer.closePrivateExposure(),
                    phase = WhodunitPhase.Reveal,
                ),
                listOf(
                    WhodunitEvent.GameEndedEarly(true),
                    WhodunitEvent.WinnerDecided(verdict),
                    WhodunitEvent.RevealNarrativePlaying,
                ),
            )
        } else {
            Reduction(
                state.copy(
                    public = state.public.copy(
                        voteState = VoteState.NoResolution(EARLY_END_REASON),
                        briefingCardIndex = 0,
                        introAcknowledged = emptySet(),
                        briefingReady = emptySet(),
                        rolesViewed = emptySet(),
                        paused = false,
                        timer = null,
                        disconnectedPlayers = emptySet(),
                    ),
                    privatePerPlayer = state.privatePerPlayer.closePrivateExposure(),
                    phase = WhodunitPhase.PostGame,
                ),
                listOf(WhodunitEvent.GameEndedEarly(false), WhodunitEvent.PhaseEntered(WhodunitPhase.PostGame)),
            )
        }
    }

    private fun reroll(
        state: WhodunitState,
        ctx: WhodunitReducerContext,
    ): Reduction<WhodunitState, WhodunitEvent> {
        if (state.phase !is WhodunitPhase.CharacterReveal) return Reduction(state)
        val reroll = createPrivateSafeReroll(state, ctx.payload.characters) ?: return Reduction(state)
        val (newSeed, assignment) = reroll
        val reset = state.copy(
            privatePerPlayer = emptyMap(),
            phase = WhodunitPhase.CharacterReveal(0),
            public = state.public.copy(
                revealedClues = emptyList(),
                voteState = VoteState.Idle,
                currentRound = 0,
                timer = null,
                paused = false,
                // Reroll produces a fresh killer/seat map, so prior per-player
                // readiness and outcome state are stale: without clearing these,
                // advanceFromCharacterReveal's PartyReadiness gate sees old
                // rolesViewed ids and never re-prompts players to view their NEW
                // role (see PROBLEMS_PARLOR.md → wd-01). Mirror beginReplay's reset.
                rolesViewed = emptySet(),
                introAcknowledged = emptySet(),
                briefingReady = emptySet(),
                eliminatedPlayers = emptyList(),
                verdict = null,
            ),
        )
        val priorPhaseId = state.phase.id
        val target = WhodunitPhase.CharacterReveal(0)
        val assigned = applyRoleAssignment(reset, assignment, newSeed, ctx.payload.characters)
            ?: return Reduction(state)
        return Reduction(
            assigned,
            listOf(
                WhodunitEvent.RolesAssigned,
                WhodunitEvent.PhaseEntered(target),
                WhodunitEvent.RerolledAt(priorPhaseId),
            ),
        )
    }

    /**
     * A privacy reroll must actually invalidate every dossier already seen.
     * Rejection-sample deterministic seeds until both the killer and every
     * seat assignment change. The rotation fallback is deterministic and
     * guarantees that contract even if the seeded generator repeatedly lands
     * on an unsuitable assignment.
     */
    private fun createPrivateSafeReroll(
        state: WhodunitState,
        characters: List<Character>,
    ): Pair<Long, RoleAssignment>? {
        var candidateSeed = nextRerollSeed(state.hostOnly.randomSeed)
        repeat(MAX_REROLL_ATTEMPTS) {
            val candidate = createRoleAssignment(state.players, characters, candidateSeed)
                ?: return null
            val everyDossierChanged = state.players.all { player ->
                candidate.seatToCharacter[player.id] != state.hostOnly.seatToCharacter[player.id]
            }
            if (candidate.killerId != state.hostOnly.killerId && everyDossierChanged) {
                return candidateSeed to candidate
            }
            candidateSeed = nextRerollSeed(candidateSeed)
        }

        val priorCharacters = state.players.map { player ->
            state.hostOnly.seatToCharacter[player.id] ?: return null
        }
        val validCharacterIds = characters.map { CharacterId(it.id) }.toSet()
        if (priorCharacters.size < 2 ||
            priorCharacters.toSet().size != priorCharacters.size ||
            !validCharacterIds.containsAll(priorCharacters)
        ) {
            return null
        }
        val rotatedCharacters = priorCharacters.drop(1) + priorCharacters.first()
        val rotatedSeats = state.players.map { it.id }.zip(rotatedCharacters).toMap()
        val priorKillerIndex = state.players.indexOfFirst { it.id == state.hostOnly.killerId }
        if (priorKillerIndex < 0) return null
        val nextKiller = state.players[(priorKillerIndex + 1) % state.players.size].id
        return candidateSeed to RoleAssignment(nextKiller, rotatedSeats)
    }

    private fun nextRerollSeed(seed: Long): Long = seed * REROLL_SEED_MULTIPLIER + REROLL_SEED_INCREMENT

    private const val BRIEFING_CARD_COUNT = 4
    private const val MAX_REROLL_ATTEMPTS = 64
    private const val TIMER_WARNING_SECONDS = 10
    private const val REMATCH_SEED_MULTIPLIER = 31L
    private const val REMATCH_SEED_INCREMENT = 17L
    private const val REROLL_SEED_MULTIPLIER = 1_103_515_245L
    private const val REROLL_SEED_INCREMENT = 12_345L
    private const val EARLY_END_REASON = "game-ended-early"
    private const val UNASSIGNED_ID = "unassigned"

    private fun Map<PlayerId, WhodunitPrivate>.closePrivateExposure(): Map<PlayerId, WhodunitPrivate> =
        mapValues { (_, privateState) ->
            privateState.copy(dossierUnlocked = false, privateReviewOpen = false)
        }

    private fun WhodunitAction.isAllowedWhilePaused(): Boolean = when (this) {
        WhodunitAction.Pause,
        WhodunitAction.Resume,
        is WhodunitAction.EndGameEarly,
        is WhodunitAction.MarkPlayerDisconnected,
        is WhodunitAction.MarkPlayerReconnected,
        is WhodunitAction.ContinueWithoutPlayer -> true
        else -> false
    }
}

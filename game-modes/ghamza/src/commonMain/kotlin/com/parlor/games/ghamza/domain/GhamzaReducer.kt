package com.parlor.games.ghamza.domain

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.GhamzaIds

/** The physical wink is self-reported. One authoritative attempt token prevents double consumption. */
class GhamzaReducer : GameReducer<GhamzaState, GhamzaAction, GhamzaChanged> {
    fun initial(config: SessionConfig): GhamzaState {
        require(config.modeId == GhamzaIds.Standard && GhamzaRoster.isValidPlayerId(config.sessionId.raw))
        return initial(config.players, requireNotNull(GhamzaSettings.fromCaseId(config.caseId.raw)), config.randomSeed)
    }

    fun initial(players: List<Player>, settings: GhamzaSettings, seed: Long, token: Long = 1L): GhamzaState {
        require(GhamzaRoster.isValidRoster(players) && token in 1..MAX_TOKEN)
        return deal(players.toList(), settings, seed, token, 1, players.associate { it.id to 0 })
    }

    override fun reduce(state: GhamzaState, action: GhamzaAction, ctx: ReducerContext): Reduction<GhamzaState, GhamzaChanged> {
        val next = apply(state, action)
        return Reduction(next, if (next == state) emptyList() else listOf(GhamzaChanged(next.public.token)))
    }

    fun apply(state: GhamzaState, action: GhamzaAction): GhamzaState {
        if (state.hostOnly.seed == null || state.phase == GhamzaPhase.Aborted || state.hostOnly.history.size >= MAX_HISTORY) return state
        val next = when (action) {
            is GhamzaAction.Disconnected -> disconnect(state, action.playerId, true)
            is GhamzaAction.Reconnected -> disconnect(state, action.playerId, false)
            GhamzaAction.Abort -> aborted(state)
            else -> if (state.public.disconnected.isEmpty()) play(state, action) else state
        }
        if (next == state) return state
        if (action is GhamzaAction.Rematch) return next
        // Reserve the final replay entry for an explicit terminal transition.
        // Extreme lifecycle churn must not strand a room at a full history bound.
        val exhausted = state.hostOnly.history.size == MAX_HISTORY - 1
        val accepted = if (exhausted) aborted(state) else next
        val recorded = if (exhausted) GhamzaAction.Abort else action
        return accepted.copy(hostOnly = accepted.hostOnly.copy(history = state.hostOnly.history + recorded))
    }

    private fun aborted(state: GhamzaState): GhamzaState = state.copy(
        phase = GhamzaPhase.Aborted,
        public = state.public.copy(result = null, finalGuesser = null, disconnected = emptySet()),
    )

    private fun play(state: GhamzaState, action: GhamzaAction): GhamzaState = when (action) {
        is GhamzaAction.Ready -> ready(state, action)
        is GhamzaAction.Winked -> winked(state, action)
        is GhamzaAction.Guess -> guess(state, action)
        is GhamzaAction.NextRound -> if (
            state.phase == GhamzaPhase.RoundResult && state.public.token == action.token && action.token < MAX_TOKEN
        ) {
            deal(state.players, state.public.settings, checkNotNull(state.hostOnly.seed), action.token + 1,
                state.public.round + 1, state.public.scores).let {
                it.copy(hostOnly = it.hostOnly.copy(firstToken = state.hostOnly.firstToken))
            }
        } else state
        is GhamzaAction.Rematch -> if (
            state.phase == GhamzaPhase.MatchResult && state.public.token == action.token && action.token < MAX_TOKEN
        ) {
            initial(state.players, state.public.settings,
                RandomSource.seeded(checkNotNull(state.hostOnly.seed)).nextLong(), action.token + 1)
        } else state
        else -> state
    }

    private fun ready(state: GhamzaState, action: GhamzaAction.Ready): GhamzaState {
        if (state.phase != GhamzaPhase.Reveal || action.token != state.public.token) return state
        if (action.by !in state.privatePerPlayer || action.by in state.public.ready) return state
        val ready = state.public.ready + action.by
        return state.copy(
            public = state.public.copy(ready = ready),
            phase = if (ready.size == state.players.size) GhamzaPhase.Social else GhamzaPhase.Reveal,
        )
    }

    private fun winked(state: GhamzaState, action: GhamzaAction.Winked): GhamzaState {
        if (state.phase != GhamzaPhase.Social || action.token != state.public.token || action.by == state.hostOnly.winker) return state
        val used = state.public.reports[action.by] ?: return state
        if (used >= state.public.settings.attempts || action.attempt != used + 1) return state
        val reports = state.public.reports + (action.by to action.attempt)
        val survivors = reports.filter { (id, count) -> id != state.hostOnly.winker && count < state.public.settings.attempts }.keys
        val report = WinkReport(action.by, action.attempt, reports.values.sum())
        return state.copy(
            public = state.public.copy(
                reports = reports, recentReports = (state.public.recentReports + report).takeLast(RECENT_REPORT_LIMIT),
                finalGuesser = survivors.singleOrNull(),
            ),
            phase = if (survivors.size == 1) GhamzaPhase.FinalGuess else GhamzaPhase.Social,
        )
    }

    private fun guess(state: GhamzaState, action: GhamzaAction.Guess): GhamzaState {
        if (state.phase != GhamzaPhase.FinalGuess || action.token != state.public.token) return state
        if (action.by != state.public.finalGuesser) return state
        if (action.target == action.by || state.players.none { it.id == action.target }) return state
        val winker = checkNotNull(state.hostOnly.winker)
        val winner = if (action.target == winker) action.by else winker
        return state.copy(
            phase = if (state.public.round == state.public.settings.rounds) GhamzaPhase.MatchResult else GhamzaPhase.RoundResult,
            public = state.public.copy(
                result = GhamzaResult(winker, action.by, action.target, winner),
                scores = state.public.scores + (winner to state.public.scores.getValue(winner) + 1),
            ),
        )
    }

    private fun disconnect(state: GhamzaState, id: PlayerId, offline: Boolean): GhamzaState {
        if (state.players.none { it.id == id }) return state
        val disconnected = if (offline) state.public.disconnected + id else state.public.disconnected - id
        return if (disconnected == state.public.disconnected) state else state.copy(public = state.public.copy(disconnected = disconnected))
    }

    private fun deal(
        players: List<Player>, settings: GhamzaSettings, seed: Long, token: Long, round: Int, scores: Map<PlayerId, Int>,
    ): GhamzaState {
        val winker = RandomSource.seeded(seed xor token).pick(players).id
        return GhamzaState(
            public = GhamzaPublic(settings, round, token, emptySet(), players.associate { it.id to 0 }, emptyList(), null, scores),
            privatePerPlayer = players.associate { it.id to GhamzaPrivate(if (it.id == winker) GhamzaRole.Winker else GhamzaRole.Guest) },
            hostOnly = GhamzaHostOnly(seed, token, winker), phase = GhamzaPhase.Reveal, players = players,
        )
    }

    companion object {
        const val MAX_TOKEN = 1_000_000_000L
        const val MAX_HISTORY = 4096
        const val RECENT_REPORT_LIMIT = 8
    }
}

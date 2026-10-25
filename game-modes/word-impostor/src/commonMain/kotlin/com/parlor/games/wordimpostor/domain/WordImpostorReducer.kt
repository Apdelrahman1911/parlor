package com.parlor.games.wordimpostor.domain

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.WordImpostorIds

/** Private votes commit once; all impostors then guess independently. No optimistic peer state. */
class WordImpostorReducer : GameReducer<WordImpostorState, WordImpostorAction, WordImpostorChanged> {
    fun initial(config: SessionConfig): WordImpostorState {
        require(config.modeId == WordImpostorIds.Standard && WordImpostorRoster.isValidPlayerId(config.sessionId.raw))
        return initial(config.players, requireNotNull(WordImpostorSettings.fromCaseId(config.caseId.raw)), config.randomSeed)
    }

    fun initial(players: List<Player>, settings: WordImpostorSettings, seed: Long, token: Long = 1L): WordImpostorState {
        require(WordImpostorRoster.isValidRoster(players) && settings.supports(players.size) && token in 1..MAX_TOKEN)
        return deal(players.toList(), settings, seed, token, 1, players.associate { it.id to 0 })
    }

    override fun reduce(
        state: WordImpostorState, action: WordImpostorAction, ctx: ReducerContext,
    ): Reduction<WordImpostorState, WordImpostorChanged> {
        val next = apply(state, action)
        return Reduction(next, if (next == state) emptyList() else listOf(WordImpostorChanged(next.public.token, next.phase)))
    }

    fun apply(state: WordImpostorState, action: WordImpostorAction): WordImpostorState {
        if (state.hostOnly.seed == null || state.phase == WordImpostorPhase.Aborted) return state
        if (state.hostOnly.history.size >= MAX_HISTORY) return state
        val next = when (action) {
            is WordImpostorAction.Disconnected -> disconnect(state, action.playerId, true)
            is WordImpostorAction.Reconnected -> disconnect(state, action.playerId, false)
            WordImpostorAction.Abort -> aborted(state)
            else -> if (state.public.disconnected.isEmpty()) play(state, action) else state
        }
        if (next == state) return state
        if (action is WordImpostorAction.Rematch) return next
        // Reserve the final replay entry for an explicit terminal transition.
        // Extreme lifecycle churn must not strand a room at a full history bound.
        val exhausted = state.hostOnly.history.size == MAX_HISTORY - 1
        val accepted = if (exhausted) aborted(state) else next
        val recorded = if (exhausted) WordImpostorAction.Abort else action
        val published = refreshPrivate(accepted)
        return published.copy(hostOnly = published.hostOnly.copy(history = state.hostOnly.history + recorded))
    }

    private fun aborted(state: WordImpostorState): WordImpostorState = state.copy(
        phase = WordImpostorPhase.Aborted,
        public = state.public.copy(result = null, disconnected = emptySet()),
    )

    private fun play(state: WordImpostorState, action: WordImpostorAction): WordImpostorState = when (action) {
        is WordImpostorAction.Ready -> ready(state, action)
        is WordImpostorAction.Answered -> answered(state, action)
        is WordImpostorAction.OpenVoting -> if (
            state.phase == WordImpostorPhase.Discussion && action.token == state.public.token
        ) state.copy(phase = WordImpostorPhase.Voting) else state
        is WordImpostorAction.Vote -> vote(state, action)
        is WordImpostorAction.Guess -> guess(state, action)
        is WordImpostorAction.NextRound -> nextRound(state, action.token)
        is WordImpostorAction.Rematch -> if (
            state.phase == WordImpostorPhase.MatchResult && action.token == state.public.token && action.token < MAX_TOKEN
        ) {
            initial(state.players, state.public.settings,
                RandomSource.seeded(checkNotNull(state.hostOnly.seed)).nextLong(), action.token + 1)
        } else state
        else -> state
    }

    private fun ready(state: WordImpostorState, action: WordImpostorAction.Ready): WordImpostorState {
        if (state.phase != WordImpostorPhase.Reveal || action.token != state.public.token) return state
        if (action.by !in state.privatePerPlayer || action.by in state.public.ready) return state
        val ready = state.public.ready + action.by
        return state.copy(public = state.public.copy(ready = ready),
            phase = if (ready.size == state.players.size) WordImpostorPhase.Questions else WordImpostorPhase.Reveal)
    }

    private fun answered(state: WordImpostorState, action: WordImpostorAction.Answered): WordImpostorState {
        if (state.phase != WordImpostorPhase.Questions || action.token != state.public.token) return state
        if (action.question != state.public.questionIndex || action.by != state.public.interactions[action.question].asker) return state
        val index = state.public.questionIndex + 1
        return state.copy(public = state.public.copy(questionIndex = index),
            phase = if (index == state.players.size) WordImpostorPhase.Discussion else WordImpostorPhase.Questions)
    }

    private fun vote(state: WordImpostorState, action: WordImpostorAction.Vote): WordImpostorState {
        if (state.phase != WordImpostorPhase.Voting || action.token != state.public.token) return state
        if (action.by !in state.privatePerPlayer || action.target !in state.privatePerPlayer || action.target == action.by) return state
        if (action.by in state.hostOnly.votes) return state
        val votes = state.hostOnly.votes + (action.by to action.target)
        val complete = votes.size == state.players.size
        val counts = if (complete) state.players.associate { player -> player.id to votes.values.count { it == player.id } } else null
        return state.copy(
            public = state.public.copy(voted = votes.keys, voteCounts = counts),
            hostOnly = state.hostOnly.copy(votes = votes),
            phase = if (complete) WordImpostorPhase.Guessing else WordImpostorPhase.Voting,
        )
    }

    private fun guess(state: WordImpostorState, action: WordImpostorAction.Guess): WordImpostorState {
        if (state.phase != WordImpostorPhase.Guessing || action.token != state.public.token) return state
        if (action.by !in state.hostOnly.impostors || action.by in state.hostOnly.guesses) return state
        if (action.wordId !in state.hostOnly.choices[action.by].orEmpty()) return state
        val guesses = state.hostOnly.guesses + (action.by to action.wordId)
        val next = state.copy(public = state.public.copy(guessCount = guesses.size), hostOnly = state.hostOnly.copy(guesses = guesses))
        return if (guesses.size == state.public.settings.impostors) finish(next) else next
    }

    private fun finish(state: WordImpostorState): WordImpostorState {
        val identified = identifiedByVotes(checkNotNull(state.public.voteCounts), state.public.settings.impostors)
        val correctVoters = state.hostOnly.votes.filter { (voter, target) ->
            voter !in state.hostOnly.impostors && target in state.hostOnly.impostors
        }.keys
        val wordId = checkNotNull(state.hostOnly.wordId)
        val awarded = state.players.associate { player ->
            val point = if (player.id in state.hostOnly.impostors) state.hostOnly.guesses[player.id] == wordId
                else player.id in correctVoters
            player.id to if (point) 1 else 0
        }
        return state.copy(
            phase = if (state.public.round == state.public.settings.rounds) WordImpostorPhase.MatchResult
                else WordImpostorPhase.RoundResult,
            public = state.public.copy(
                result = WordRoundResult(wordId, state.hostOnly.impostors, identified, correctVoters, state.hostOnly.guesses, awarded),
                scores = state.public.scores.mapValues { (id, score) -> score + awarded.getValue(id) },
            ),
        )
    }

    private fun nextRound(state: WordImpostorState, token: Long): WordImpostorState {
        if (state.phase != WordImpostorPhase.RoundResult || token != state.public.token || token >= MAX_TOKEN) return state
        return deal(state.players, state.public.settings, checkNotNull(state.hostOnly.seed), token + 1,
            state.public.round + 1, state.public.scores).let {
            it.copy(hostOnly = it.hostOnly.copy(firstToken = state.hostOnly.firstToken))
        }
    }

    private fun disconnect(state: WordImpostorState, id: PlayerId, offline: Boolean): WordImpostorState {
        if (state.players.none { it.id == id }) return state
        val disconnected = if (offline) state.public.disconnected + id else state.public.disconnected - id
        return if (disconnected == state.public.disconnected) state else state.copy(public = state.public.copy(disconnected = disconnected))
    }

    private fun deal(
        players: List<Player>, settings: WordImpostorSettings, seed: Long, token: Long, round: Int, scores: Map<PlayerId, Int>,
    ): WordImpostorState {
        val random = RandomSource.seeded(seed xor token)
        val impostors = random.shuffled(players).take(settings.impostors).map { it.id }.toSet()
        val word = random.pick(WordTopicBank.words(settings.topic))
        val cycle = random.shuffled(players)
        val interactions = cycle.mapIndexed { index, player -> QuestionPair(player.id, cycle[(index + 1) % cycle.size].id) }
        val questions = random.shuffled(WordTopicBank.questions(settings.topic)).take(players.size)
        val choices = impostors.associateWith {
            val distractors = WordTopicBank.words(settings.topic).filter { it.group == word.group && it.id != word.id }
            random.shuffled(listOf(word.id) + random.shuffled(distractors).take(CHOICE_COUNT - 1).map { it.id })
        }
        return refreshPrivate(WordImpostorState(
            public = WordImpostorPublic(settings, round, token, emptySet(), interactions, 0, emptySet(), null, 0, scores),
            privatePerPlayer = emptyMap(),
            hostOnly = WordImpostorHostOnly(seed, token, word.id, impostors, questions, choices),
            phase = WordImpostorPhase.Reveal, players = players,
        ))
    }

    private fun refreshPrivate(state: WordImpostorState): WordImpostorState = state.copy(
        privatePerPlayer = state.players.associate { player ->
            val impostor = player.id in state.hostOnly.impostors
            val ownQuestion = state.phase == WordImpostorPhase.Questions &&
                state.public.interactions.getOrNull(state.public.questionIndex)?.asker == player.id
            player.id to if (state.phase == WordImpostorPhase.Aborted) WordImpostorPrivate() else WordImpostorPrivate(
                role = if (impostor) WordRole.Impostor else WordRole.Ordinary,
                wordId = state.hostOnly.wordId.takeUnless { impostor },
                teammates = if (impostor) state.hostOnly.impostors - player.id else emptySet(),
                questionId = if (ownQuestion) state.hostOnly.questions[state.public.questionIndex] else null,
                vote = state.hostOnly.votes[player.id],
                choices = if (state.phase == WordImpostorPhase.Guessing && impostor) state.hostOnly.choices.getValue(player.id)
                    else emptyList(),
                guess = state.hostOnly.guesses[player.id],
            )
        },
    )

    companion object {
        const val MAX_TOKEN = 1_000_000_000L
        const val MAX_HISTORY = 4096
        const val CHOICE_COUNT = 5

        /** Informational poll result only. Ties never remove a correct voter's personal point. */
        fun identifiedByVotes(counts: Map<PlayerId, Int>, count: Int): Set<PlayerId> {
            if (count <= 0 || count >= counts.size) return emptySet()
            val ranked = counts.entries.sortedByDescending { it.value }
            if (ranked[count - 1].value == ranked[count].value) return emptySet()
            return ranked.take(count).map { it.key }.toSet()
        }
    }
}

package com.parlor.games.wordimpostor.domain

import com.parlor.core.ids.PlayerId

object WordImpostorValidation {
    fun publicState(state: WordImpostorState): Boolean {
        if (!WordImpostorRoster.isValidRoster(state.players)) return false
        val p = state.public
        val ids = state.players.map { it.id }.toSet()
        if (!validBounds(p, ids) || !validCycle(p.interactions, ids)) return false
        if (state.phase == WordImpostorPhase.Aborted) return validAbortedProgress(state)
        if (p.result == null && p.scores.values.any { it >= p.round }) return false
        if (state.phase != WordImpostorPhase.Reveal && p.ready != ids) return false
        if (state.phase in BEFORE_VOTES && (p.voteCounts != null || p.guessCount != 0 || p.result != null)) return false
        return when (state.phase) {
            WordImpostorPhase.Reveal -> p.questionIndex == 0 && p.ready.size < ids.size && p.voted.isEmpty()
            WordImpostorPhase.Questions -> p.questionIndex < ids.size && p.voted.isEmpty()
            WordImpostorPhase.Discussion -> p.questionIndex == ids.size && p.voted.isEmpty()
            WordImpostorPhase.Voting -> p.questionIndex == ids.size && p.voted.size < ids.size
            WordImpostorPhase.Guessing -> validVotes(state) && p.guessCount < p.settings.impostors && p.result == null
            WordImpostorPhase.RoundResult -> p.round < p.settings.rounds && validResult(state)
            WordImpostorPhase.MatchResult -> p.round == p.settings.rounds && validResult(state)
            WordImpostorPhase.Aborted -> false
        }
    }

    private fun validBounds(p: WordImpostorPublic, ids: Set<PlayerId>): Boolean {
        if (!p.settings.supports(ids.size) || p.round !in 1..p.settings.rounds || p.token !in 1..WordImpostorReducer.MAX_TOKEN) return false
        if (p.scores.keys != ids || p.scores.values.any { it !in 0..p.round }) return false
        if (!ids.containsAll(p.ready) || !ids.containsAll(p.voted) || !ids.containsAll(p.disconnected)) return false
        return p.questionIndex in 0..ids.size && p.guessCount in 0..p.settings.impostors
    }

    private fun validAbortedProgress(state: WordImpostorState): Boolean {
        val p = state.public
        val count = state.players.size
        if (p.result != null || p.disconnected.isNotEmpty()) return false
        if (p.ready.size < count && (p.questionIndex != 0 || p.voted.isNotEmpty())) return false
        if (p.questionIndex < count && p.voted.isNotEmpty()) return false
        return if (p.voteCounts != null) validVotes(state) else p.voted.size < count && p.guessCount == 0
    }

    fun playerState(state: WordImpostorState, id: PlayerId): Boolean {
        if (!publicState(state) || state.hostOnly != WordImpostorHostOnly() || state.privatePerPlayer.keys != setOf(id)) return false
        val ids = state.players.map { it.id }.toSet()
        if (id !in ids) return false
        val own = state.privatePerPlayer.getValue(id)
        if (state.phase == WordImpostorPhase.Aborted) return own == WordImpostorPrivate()
        val wordIds = WordTopicBank.words(state.public.settings.topic).map { it.id }.toSet()
        if (!validRole(state, own, id, wordIds) || !validOwnProgress(state, own, id)) return false
        if (!validChoices(state, own, wordIds)) return false
        val result = state.public.result ?: return true
        val impostor = id in result.impostors
        return (own.role == WordRole.Impostor) == impostor &&
            (if (impostor) own.teammates == result.impostors - id && own.guess == result.guesses[id] else own.wordId == result.wordId)
    }

    private fun validRole(
        state: WordImpostorState, own: WordImpostorPrivate, id: PlayerId, wordIds: Set<String>,
    ): Boolean = when (own.role) {
        null -> false
        WordRole.Ordinary -> own.wordId in wordIds && own.teammates.isEmpty() && own.choices.isEmpty() && own.guess == null
        WordRole.Impostor -> own.wordId == null && own.teammates.size == state.public.settings.impostors - 1 &&
            id !in own.teammates && state.players.map { it.id }.containsAll(own.teammates)
    }

    private fun validOwnProgress(state: WordImpostorState, own: WordImpostorPrivate, id: PlayerId): Boolean {
        val ids = state.players.map { it.id }.toSet()
        val asking = state.phase == WordImpostorPhase.Questions && state.public.interactions[state.public.questionIndex].asker == id
        if (asking != (own.questionId != null)) return false
        if (own.questionId != null && own.questionId !in WordTopicBank.questions(state.public.settings.topic)) return false
        if ((id in state.public.voted) != (own.vote != null) || own.vote == id || (own.vote != null && own.vote !in ids)) return false
        val counts = state.public.voteCounts
        return counts == null || own.vote == null || counts.getValue(own.vote) > 0
    }

    private fun validChoices(state: WordImpostorState, own: WordImpostorPrivate, wordIds: Set<String>): Boolean {
        val guessing = state.phase == WordImpostorPhase.Guessing && own.role == WordRole.Impostor
        if (guessing) {
            if (own.choices.size != WordImpostorReducer.CHOICE_COUNT || own.choices.toSet().size != own.choices.size) return false
            if (!wordIds.containsAll(own.choices) || (own.guess != null && own.guess !in own.choices)) return false
            val groups = WordTopicBank.words(state.public.settings.topic).filter { it.id in own.choices }.map { it.group }
            if (groups.distinct().size != 1) return false
        } else if (own.choices.isNotEmpty()) return false
        if (own.guess != null && own.guess !in wordIds) return false
        if (state.phase in BEFORE_VOTES && own.guess != null) return false
        return true
    }

    private fun validCycle(pairs: List<QuestionPair>, ids: Set<PlayerId>): Boolean {
        if (pairs.size != ids.size || pairs.map { it.asker }.toSet() != ids || pairs.map { it.answerer }.toSet() != ids) return false
        if (pairs.any { it.asker == it.answerer }) return false
        return pairs.indices.all { pairs[it].answerer == pairs[(it + 1) % pairs.size].asker }
    }

    private fun validVotes(state: WordImpostorState): Boolean {
        val p = state.public
        val ids = state.players.map { it.id }.toSet()
        val counts = p.voteCounts ?: return false
        return p.questionIndex == ids.size && p.voted == ids && counts.keys == ids &&
            counts.values.all { it in 0 until ids.size } && counts.values.sum() == ids.size
    }

    private fun validResult(state: WordImpostorState): Boolean {
        if (!validVotes(state)) return false
        val p = state.public
        val r = p.result ?: return false
        val wordIds = WordTopicBank.words(p.settings.topic).map { it.id }.toSet()
        val ids = state.players.map { it.id }.toSet()
        if (p.guessCount != p.settings.impostors || r.impostors.size != p.settings.impostors) return false
        if (!ids.containsAll(r.impostors)) return false
        if (r.wordId !in wordIds || r.guesses.keys != r.impostors || !wordIds.containsAll(r.guesses.values)) return false
        if (r.identified != WordImpostorReducer.identifiedByVotes(checkNotNull(p.voteCounts), p.settings.impostors)) return false
        if (r.ordinaryTeamScored != (r.identified == r.impostors) || r.awarded.keys != ids) return false
        return r.awarded.all { (id, points) ->
            val earned = if (id in r.impostors) r.guesses[id] == r.wordId else r.ordinaryTeamScored
            points == (if (earned) 1 else 0) && p.scores.getValue(id) >= points
        }
    }

    private val BEFORE_VOTES = setOf(
        WordImpostorPhase.Reveal, WordImpostorPhase.Questions, WordImpostorPhase.Discussion, WordImpostorPhase.Voting,
    )
}

object WordImpostorActionValidation {
    fun valid(action: WordImpostorAction): Boolean = when (action) {
        is WordImpostorAction.Ready -> actor(action.by, action.token)
        is WordImpostorAction.Answered -> actor(action.by, action.token) && action.question in 0..11
        is WordImpostorAction.Vote -> actor(action.by, action.token) && WordImpostorRoster.isValidPlayerId(action.target.raw)
        is WordImpostorAction.Guess -> actor(action.by, action.token) && WordTopic.entries.any { topic ->
            WordTopicBank.words(topic).any { it.id == action.wordId }
        }
        is WordImpostorAction.OpenVoting -> action.token in 1..WordImpostorReducer.MAX_TOKEN
        is WordImpostorAction.NextRound -> action.token in 1..WordImpostorReducer.MAX_TOKEN
        is WordImpostorAction.Rematch -> action.token in 1..WordImpostorReducer.MAX_TOKEN
        else -> false
    }
    private fun actor(by: PlayerId, token: Long): Boolean =
        WordImpostorRoster.isValidPlayerId(by.raw) && token in 1..WordImpostorReducer.MAX_TOKEN
}

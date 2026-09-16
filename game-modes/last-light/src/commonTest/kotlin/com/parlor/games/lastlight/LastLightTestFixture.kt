package com.parlor.games.lastlight

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightState
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.time.Instant

internal class LastLightTestFixture {
    val definition = LastLightDefinition()
    private val context = DefaultReducerContext(FakeClock(Instant.fromEpochSeconds(0)), RandomSource.seeded(991L))

    fun config(count: Int = 4, seed: Long = 71L): SessionConfig = SessionConfig(
        sessionId = SessionId("last-light-test-$count-$seed"),
        caseId = LastLightIds.CaseId,
        modeId = LastLightIds.StandardModeId,
        players = List(count) { Player(PlayerId("player-$it"), "Player ${it + 1}", it) },
        randomSeed = seed,
    )

    fun initial(count: Int = 4, seed: Long = 71L): LastLightState = definition.createInitialState(config(count, seed))

    fun reduce(state: LastLightState, action: LastLightAction): LastLightState =
        definition.reducer().reduce(state, action, context).newState

    fun accepted(state: LastLightState, action: LastLightAction): LastLightState = reduce(state, action).also {
        assertNotSame(state, it, "Expected an accepted ${action::class.simpleName}")
    }

    /** Uses only the current player's permitted view, like a production player. */
    fun legalAction(state: LastLightState): LastLightAction {
        if (state.phase == GamePhase.ROUND_ENDED) return LastLightAction.NextRound
        val actor = PlayerId(assertNotNull(state.public.turnPlayerId))
        val view = LastLightProjectionPolicy.viewFor(state, actor)
        return if (view.availableActions.canChallenge && (view.forcedChallenge || view.acceptedPlaySequence % 3L == 0L)) {
            LastLightAction.Challenge(actor)
        } else {
            LastLightAction.PlayCards(actor, view.yourHand.take(view.availableActions.maxPlayableCards).map { it.id })
        }
    }
}

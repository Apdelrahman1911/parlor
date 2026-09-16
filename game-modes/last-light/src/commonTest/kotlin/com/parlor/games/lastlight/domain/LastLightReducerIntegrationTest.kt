package com.parlor.games.lastlight.domain

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.LastLightTestFixture
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.authority.LastLightActionAuthority
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.rules.LastLightSessionRules
import com.parlor.games.lastlight.domain.state.LastLightObservableStateValidator
import com.parlor.networking.room.RoomInputPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LastLightReducerIntegrationTest {
    private val fixture = LastLightTestFixture()

    @Test
    fun allSupportedRostersDealFivePrivateCardsAndMatchParlorNameBoundaries() {
        for (count in 2..6) {
            val state = fixture.initial(count)
            assertEquals(count, state.public.roster.size)
            assertTrue(state.privatePerPlayer.values.all { it.hand.size == 5 })
            assertTrue(LastLightObservableStateValidator.isValid(state))
        }
        val labels = listOf("A".repeat(32), "عبد الرحمن", "Ada 🌙", "اللاعبة الثانية")
        val config = fixture.config().let { initial ->
            initial.copy(players = initial.players.mapIndexed { index, player -> player.copy(displayName = labels[index]) })
        }
        assertTrue(RoomInputPolicy.areValidDistinctDisplayNames(labels))
        assertTrue(LastLightSessionRules.isValidConfig(config))
        assertEquals(labels, fixture.definition.createInitialState(config).public.roster.map { it.displayName })
        val invalid = listOf("A".repeat(33), " Ada", "Ada ", "\u202EAda", "Ada\n", "\uD800", "Ada\uDC00")
        for (name in invalid) {
            assertFalse(LastLightSessionRules.isValidDisplayName(name), name)
            val changed = config.copy(players = config.players.mapIndexed { index, player ->
                if (index == 0) player.copy(displayName = name) else player
            })
            assertFailsWith<IllegalArgumentException> { fixture.definition.createInitialState(changed) }
        }
    }

    @Test
    fun unknownModesContentAndNoncanonicalSeatsAreRejectedBeforeDealing() {
        val config = fixture.config()
        val variants = listOf(
            config.copy(modeId = ModeId("custom")),
            config.copy(caseId = CaseId("some-other-game")),
            config.copy(players = config.players.reversed()),
            config.copy(players = config.players.map { it.copy(seat = 0) }),
            config.copy(players = config.players.map { it.copy(id = PlayerId("same")) }),
            config.copy(players = config.players.map { it.copy(displayName = "Same") }),
            fixture.config(count = 1),
            fixture.config(count = 7),
        )
        variants.forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { fixture.definition.createInitialState(invalid) }
        }
    }

    @Test
    fun illegalActionsAreAtomicAndAcceptedCountersOnlyTrackTheirOwnTransitions() {
        var state = fixture.initial()
        val actor = PlayerId(assertNotNull(state.public.turnPlayerId))
        val another = state.players.first { it.id != actor }.id
        val hand = state.privatePerPlayer.getValue(actor).hand
        val invalid = listOf(
            LastLightAction.NextRound,
            LastLightAction.Challenge(actor),
            LastLightAction.Challenge(PlayerId("stale-seat")),
            LastLightAction.PlayCards(another, listOf(hand.first().id)),
            LastLightAction.PlayCards(actor, emptyList()),
            LastLightAction.PlayCards(actor, hand.take(4).map { it.id }),
            LastLightAction.PlayCards(actor, listOf(hand.first().id, hand.first().id)),
            LastLightAction.PlayCards(actor, listOf(state.privatePerPlayer.getValue(another).hand.first().id)),
            LastLightAction.ContinueWithoutPlayer(actor),
            LastLightAction.MarkPlayerDisconnected(PlayerId("stale-seat")),
        )
        invalid.forEach { assertSame(state, fixture.reduce(state, it)) }
        val played = fixture.accepted(state, LastLightAction.PlayCards(actor, listOf(hand.first().id)))
        assertEquals(1L, played.public.acceptedPlaySequence)
        assertEquals(0L, played.public.outcomeSequence)
        val nextActor = PlayerId(assertNotNull(played.public.turnPlayerId))
        state = fixture.accepted(played, LastLightAction.Challenge(nextActor))
        assertEquals(1L, state.public.acceptedPlaySequence)
        assertEquals(1L, state.public.outcomeSequence)
        assertSame(state, fixture.reduce(state, LastLightAction.Challenge(nextActor)))
        assertSame(state, fixture.reduce(state, LastLightAction.PlayCards(actor, listOf(hand.first().id))))
    }

    @Test
    fun fixedSeedsRunToFiniteWinnersThroughOnlyRecipientViews() {
        for (count in 2..6) for (seed in 0L..3L) {
            var state = fixture.initial(count, seed)
            val original = fixture.initial(count, seed)
            assertEquals(state, original)
            var transitions = 0
            while (state.phase != GamePhase.FINISHED) {
                state = fixture.accepted(state, fixture.legalAction(state))
                assertTrue(LastLightObservableStateValidator.isValid(state))
                assertTrue(++transitions <= LastLightRules.MAX_HISTORY_ENTRIES)
            }
            assertEquals(1, state.public.roster.count { !it.eliminated })
            assertEquals(state.public.roster.single { !it.eliminated }.id, state.public.winnerId)
            assertTrue(state.public.outcomeSequence <= 6 * count - 1)
            assertSame(state, fixture.reduce(state, LastLightAction.NextRound))
            assertSame(state, fixture.reduce(state, LastLightAction.EndGame))
        }
    }

    @Test
    fun aMissingEmptyHandClaimantPausesWithoutBeingSkippedAndReturnsWithTheSameHand() {
        var state = fixture.initial(count = 2)
        while (!state.public.forcedChallenge) {
            val actor = PlayerId(assertNotNull(state.public.turnPlayerId))
            val hand = state.privatePerPlayer.getValue(actor).hand
            state = fixture.accepted(state, LastLightAction.PlayCards(actor, hand.take(3).map { it.id }))
        }
        val missing = PlayerId(assertNotNull(state.public.latestClaim).playerId)
        assertTrue(state.privatePerPlayer.getValue(missing).hand.isEmpty())
        val pause = fixture.accepted(state, LastLightAction.MarkPlayerDisconnected(missing))
        val challenger = PlayerId(assertNotNull(pause.public.turnPlayerId))
        assertFalse(LastLightProjectionPolicy.viewFor(pause, challenger).availableActions.canChallenge)
        assertSame(pause, fixture.reduce(pause, LastLightAction.Challenge(challenger)))
        assertSame(pause, fixture.reduce(pause, LastLightAction.NextRound))
        assertSame(pause, fixture.reduce(pause, LastLightAction.MarkPlayerDisconnected(missing)))
        assertEquals(state, fixture.accepted(pause, LastLightAction.MarkPlayerReconnected(missing)))
    }

    @Test
    fun explicitDepartureTerminatesAndNeverAwardsAWinnerOrRedistributesHiddenCards() {
        val initial = fixture.initial()
        val first = initial.players[0].id
        val second = initial.players[1].id
        var paused = fixture.accepted(initial, LastLightAction.MarkPlayerDisconnected(first))
        paused = fixture.accepted(paused, LastLightAction.MarkPlayerDisconnected(second))
        val ended = fixture.accepted(paused, LastLightAction.ContinueWithoutPlayer(first))
        assertEquals(GamePhase.FINISHED, ended.phase)
        assertTrue(ended.public.endedEarly)
        assertNull(ended.public.winnerId)
        assertEquals(initial.privatePerPlayer, ended.privatePerPlayer)
        assertEquals(initial.hostOnly.undealtCards, ended.hostOnly.undealtCards)
        assertEquals(setOf(first), ended.public.droppedPlayers)
        assertTrue(ended.public.disconnectedPlayers.isEmpty())
        assertSame(ended, fixture.reduce(ended, LastLightAction.MarkPlayerReconnected(second)))
        assertSame(ended, fixture.reduce(ended, LastLightAction.ContinueWithoutPlayer(second)))
    }

    @Test
    fun actorAuthorityRejectsImpersonationAndStaleSeatsButAnEliminatedHostCanAdvance() {
        var state = fixture.initial(count = 6)
        while (state.public.roster.none { it.eliminated }) state = fixture.accepted(state, fixture.legalAction(state))
        assertEquals(GamePhase.ROUND_ENDED, state.phase)
        val host = PlayerId(state.public.roster.first { it.eliminated }.id)
        val peer = state.players.first { it.id != host }.id
        assertTrue(LastLightActionAuthority.isAllowed(LastLightAction.NextRound, host, host, state))
        assertFalse(LastLightActionAuthority.isAllowed(LastLightAction.NextRound, peer, host, state))
        assertFalse(LastLightActionAuthority.isAllowed(LastLightAction.Challenge(peer), host, host, state))
        assertFalse(LastLightActionAuthority.isAllowed(LastLightAction.Challenge(host), host, host, state))
        val stale = PlayerId("stale-seat")
        assertFalse(LastLightActionAuthority.isAllowed(LastLightAction.Challenge(stale), stale, host, state))
        assertSame(state, fixture.reduce(state, LastLightAction.MarkPlayerDisconnected(host)))
        state = fixture.accepted(state, LastLightAction.NextRound)
        assertTrue(state.privatePerPlayer.getValue(host).hand.isEmpty())
        assertSame(state, fixture.reduce(state, LastLightAction.Challenge(host)))
    }

    @Test
    fun projectionsCannotBecomeAReducerAuthority() {
        val state = fixture.initial()
        val actor = PlayerId(assertNotNull(state.public.turnPlayerId))
        val player = LastLightProjectionPolicy.toPlayer(state, actor).state
        assertSame(player, fixture.reduce(player, LastLightAction.EndGame))
        assertSame(player, fixture.reduce(player, LastLightAction.PlayCards(actor, player.privatePerPlayer.getValue(actor).hand.take(1).map { it.id })))
    }
}

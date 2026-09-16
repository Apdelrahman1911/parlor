package com.parlor.games.lastlight.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.LastLightTestFixture
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightHostOnly
import com.parlor.games.lastlight.domain.state.LastLightPrivate
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LastLightProjectionTest {
    private val fixture = LastLightTestFixture()

    @Test
    fun everyRecipientGetsOnlyTheirOwnHandAndNoHostAuthorityKeysOrCards() {
        val state = fixture.initial()
        val publicBytes = LastLightProjectionCodec.encodePublic(state)
        val publicText = publicBytes.decodeToString()
        val public = LastLightProjectionCodec.decodePublic(publicBytes)
        assertTrue(public.privatePerPlayer.isEmpty())
        assertEquals(LastLightHostOnly.Redacted, public.hostOnly)
        for (key in listOf("randomSeed", "burnoutSteps", "pendingCards", "discardedCards", "undealtCards", "history", "privatePerPlayer")) {
            assertFalse(publicText.contains("\"$key\""), key)
        }
        val allPrivateCards = state.privatePerPlayer.values.flatMap { it.hand }
        for (card in allPrivateCards + state.hostOnly.undealtCards) assertFalse(publicText.contains("\"${card.id}\""))
        for (recipient in state.players) {
            val projected = LastLightProjectionPolicy.toPlayer(state, recipient.id).state
            assertEquals(setOf(recipient.id), projected.privatePerPlayer.keys)
            assertEquals(LastLightHostOnly.Redacted, projected.hostOnly)
            val own = projected.privatePerPlayer.getValue(recipient.id)
            val bytes = LastLightProjectionCodec.encodePrivate(own)
            val private = LastLightProjectionCodec.decodePrivate(bytes)
            assertTrue(LastLightPeerSnapshotValidator.isValid(public, private, recipient.id))
            val forbiddenCards = state.privatePerPlayer.filterKeys { it != recipient.id }.values.flatMap { it.hand }
            for (card in forbiddenCards + state.hostOnly.undealtCards) {
                assertFalse(bytes.decodeToString().contains("\"${card.id}\""))
            }
            val view = LastLightProjectionPolicy.viewFor(projected, recipient.id)
            assertEquals(own.hand, view.yourHand)
        }
        val unknown = LastLightProjectionPolicy.viewFor(state, PlayerId("missing"))
        assertNull(unknown.viewerId)
        assertTrue(unknown.yourHand.isEmpty())
        assertFalse(unknown.availableActions.canPlay)
        assertFalse(unknown.availableActions.canChallenge)
    }

    @Test
    fun allLegalPhasesAndEliminatedRecipientsPassStrictProjectionValidation() {
        var state = fixture.initial(count = 6)
        while (true) {
            val public = LastLightProjectionCodec.decodePublic(LastLightProjectionCodec.encodePublic(state))
            for (player in state.players) {
                val own = LastLightProjectionCodec.decodePrivate(
                    LastLightProjectionCodec.encodePrivate(LastLightProjectionPolicy.toPlayer(state, player.id).state.privatePerPlayer.getValue(player.id)),
                )
                assertTrue(LastLightPeerSnapshotValidator.isValid(public, own, player.id), "${state.phase}/${state.public.roundNumber}")
            }
            if (state.phase == GamePhase.FINISHED) break
            state = fixture.accepted(state, fixture.legalAction(state))
        }
    }

    @Test
    fun publicPeerValidationRejectsPrivateBucketsImpossibleTurnsAndFalsePlayProgress() {
        val initial = fixture.initial(count = 3)
        val public = LastLightProjectionPolicy.toPublic(initial).state
        val self = initial.players[0].id
        val own = initial.privatePerPlayer.getValue(self)
        assertFalse(LastLightPeerSnapshotValidator.isValid(initial, own, self))
        assertFalse(LastLightPeerSnapshotValidator.isValid(public.copy(privatePerPlayer = mapOf(self to own)), own, self))
        assertFalse(LastLightPeerSnapshotValidator.isValid(public.copy(hostOnly = LastLightHostOnly(randomSeed = 99)), own, self))
        assertFalse(LastLightPeerSnapshotValidator.isValid(public, null, self))
        assertFalse(LastLightPeerSnapshotValidator.isValid(public, own, PlayerId("unknown")))
        assertFalse(LastLightPeerSnapshotValidator.isValidPublic(public.copy(public = public.public.copy(acceptedPlaySequence = 1))))
        val played = fixture.accepted(initial, fixture.legalAction(initial))
        val wrongTurn = played.public.roster.first {
            it.id != played.public.turnPlayerId && it.id != played.public.latestClaim?.playerId
        }.id
        val wrong = LastLightProjectionPolicy.toPublic(played).state.let {
            it.copy(public = it.public.copy(turnPlayerId = wrongTurn))
        }
        assertFalse(LastLightPeerSnapshotValidator.isValidPublic(wrong))
    }

    @Test
    fun privatePeerValidationRejectsWrongSeatSlotsUndealtCardsDuplicatesAndStaleCards() {
        val state = fixture.initial(count = 2)
        val public = LastLightProjectionPolicy.toPublic(state).state
        val self = state.players[0].id
        val own = state.privatePerPlayer.getValue(self)
        val other = state.privatePerPlayer.getValue(state.players[1].id)
        val wrongHands = listOf(
            other,
            own.copy(hand = own.hand.dropLast(1) + Card("r1-c29", CardRank.CROWN)),
            own.copy(hand = own.hand.dropLast(1) + own.hand.first()),
            own.copy(hand = own.hand.map { it.copy(id = it.id.replace("r1-", "r2-")) }),
            own.copy(hand = own.hand.reversed()),
        )
        wrongHands.forEach { assertFalse(LastLightPeerSnapshotValidator.isValid(public, it, self)) }

        val played = fixture.accepted(state, fixture.legalAction(state))
        val currentSelf = PlayerId(assertNotNull(played.public.turnPlayerId))
        val playedPublic = LastLightProjectionPolicy.toPublic(played).state
        val currentHand = played.privatePerPlayer.getValue(currentSelf)
        val actualSlots = currentHand.hand.map { it.id.substringAfter("-c").toInt() }
        val otherSeatSlot = (0 until 10).first { it % 2 != actualSlots.first() % 2 }
        val mixed = currentHand.copy(hand = (currentHand.hand.dropLast(1) + Card("r1-c$otherSeatSlot", CardRank.CROWN))
            .sortedBy { it.id.substringAfter("-c").toInt() })
        assertFalse(LastLightPeerSnapshotValidator.isValid(playedPublic, mixed, currentSelf))
    }

    @Test
    fun revealedWildsAndOwnWildsMustFitTheSameThirtyCardDeck() {
        val initial = (0L..100L).asSequence().map { fixture.initial(count = 4, seed = it) }.first { state ->
            state.privatePerPlayer.getValue(PlayerId(requireNotNull(state.public.turnPlayerId))).hand.any { it.rank == CardRank.WILD }
        }
        val claimant = PlayerId(assertNotNull(initial.public.turnPlayerId))
        val wild = initial.privatePerPlayer.getValue(claimant).hand.first { it.rank == CardRank.WILD }
        val played = fixture.accepted(initial, LastLightAction.PlayCards(claimant, listOf(wild.id)))
        val challenger = PlayerId(assertNotNull(played.public.turnPlayerId))
        val ended = fixture.accepted(played, LastLightAction.Challenge(challenger))
        val self = ended.players.first { it.id != claimant && it.id != challenger }.id
        val public = LastLightProjectionPolicy.toPublic(ended).state
        val own = ended.privatePerPlayer.getValue(self)
        assertTrue(LastLightPeerSnapshotValidator.isValid(public, own, self))
        val impossible = own.copy(hand = own.hand.mapIndexed { index, card ->
            card.copy(rank = if (index < 3) CardRank.WILD else CardRank.CROWN)
        })
        assertFalse(LastLightPeerSnapshotValidator.isValid(public, impossible, self))
        val overlapping = own.copy(hand = own.hand.dropLast(1) + wild)
        assertFalse(LastLightPeerSnapshotValidator.isValid(public, overlapping, self))
    }

    @Test
    fun publicChallengeProofMustComeFromOneDealtHandAndFitItsRemainingCardCount() {
        val initial = fixture.initial()
        val played = fixture.accepted(initial, fixture.legalAction(initial))
        val challenger = PlayerId(assertNotNull(played.public.turnPlayerId))
        val ended = fixture.accepted(played, LastLightAction.Challenge(challenger))
        val public = LastLightProjectionPolicy.toPublic(ended).state
        val outcome = assertNotNull(public.public.roundOutcome)
        val mixedOwnerProof = outcome.copy(revealedCards = outcome.revealedCards.mapIndexed { index, card ->
            if (index == 0) card.copy(id = "r1-c1") else card
        })
        assertFalse(LastLightPeerSnapshotValidator.isValidPublic(public.copy(public = public.public.copy(roundOutcome = mixedOwnerProof))))

        val actor = PlayerId(assertNotNull(initial.public.turnPlayerId))
        val card = initial.privatePerPlayer.getValue(actor).hand.first()
        val onePlayed = fixture.accepted(initial, LastLightAction.PlayCards(actor, listOf(card.id)))
        val oneEnded = fixture.accepted(onePlayed, LastLightAction.Challenge(PlayerId(assertNotNull(onePlayed.public.turnPlayerId))))
        val onePublic = LastLightProjectionPolicy.toPublic(oneEnded).state
        val oneOutcome = assertNotNull(onePublic.public.roundOutcome)
        val tooManyClaimed = oneOutcome.copy(revealedCards = oneOutcome.revealedCards + card.copy(id = "r1-c4"))
        assertFalse(LastLightPeerSnapshotValidator.isValidPublic(onePublic.copy(public = onePublic.public.copy(roundOutcome = tooManyClaimed))))
    }

    @Test
    fun publicAndPrivateWireFormatsRejectUnknownFieldsVersionsAndNoncanonicalPayloads() {
        val initial = fixture.initial()
        val public = LastLightProjectionCodec.encodePublic(initial).decodeToString()
        val private = LastLightProjectionCodec.encodePrivate(initial.privatePerPlayer.getValue(initial.players[0].id)).decodeToString()
        for ((text, decode) in listOf(
            public to { bytes: ByteArray -> LastLightProjectionCodec.decodePublic(bytes) },
            private to { bytes: ByteArray -> LastLightProjectionCodec.decodePrivate(bytes) },
        )) {
            assertFails { decode(text.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2").encodeToByteArray()) }
            assertFails { decode(text.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":1,\"hostOnly\":{}").encodeToByteArray()) }
            assertFails { decode(text.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1").encodeToByteArray()) }
            assertFails { decode(" $text".encodeToByteArray()) }
            assertFails { decode(ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES + 1)) }
            assertFails { decode(byteArrayOf(0xc3.toByte())) }
        }
    }

    @Test
    fun callerMutationsCannotRewriteProjectedOrCanonicalCollections() {
        val config = fixture.config()
        val callerRoster = config.players.toMutableList()
        val state = fixture.definition.createInitialState(config.copy(players = callerRoster))
        callerRoster.clear()
        val baseline = fixture.definition.snapshotCodec().encode(state)
        val self = state.players[0].id
        val projected = LastLightProjectionPolicy.toPlayer(state, self).state
        val view = LastLightProjectionPolicy.viewFor(projected, self)
        attemptClear(projected.players)
        attemptClear(projected.public.roster)
        attemptClear(projected.privatePerPlayer.getValue(self).hand)
        attemptClear(view.yourHand)
        attemptClear(view.players)
        (projected.privatePerPlayer as? MutableMap<PlayerId, LastLightPrivate>)?.let { runCatching { it.clear() } }
        assertTrue(baseline.contentEquals(fixture.definition.snapshotCodec().encode(state)))
        assertEquals(state.privatePerPlayer.getValue(self).hand, LastLightProjectionPolicy.viewFor(projected, self).yourHand)
    }

    private fun <T> attemptClear(values: List<T>) {
        (values as? MutableList<T>)?.let { runCatching { it.clear() } }
    }
}

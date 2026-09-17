package com.parlor.games.lastlight.snapshot

import com.parlor.core.ids.PlayerId
import com.parlor.games.lastlight.LastLightTestFixture
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.state.LastLightHistoryEntry
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LastLightSnapshotCodecTest {
    private val fixture = LastLightTestFixture()
    private val codec = LastLightSnapshotCodec(Json { ignoreUnknownKeys = true; isLenient = true })
    private val unsafeJson = Json { encodeDefaults = true; explicitNulls = true }

    @Test
    fun roundTripsPreserveEveryPhaseAndTheFutureAcrossProcessRecovery() {
        for (count in listOf(2, 4, 6)) {
            var uninterrupted = fixture.initial(count)
            var restored = codec.decode(codec.encode(uninterrupted))
            var transitions = 0
            while (uninterrupted.phase != GamePhase.FINISHED) {
                val action = fixture.legalAction(uninterrupted)
                uninterrupted = fixture.accepted(uninterrupted, action)
                restored = fixture.accepted(restored, action)
                assertEquals(uninterrupted, restored)
                if (restored.phase != GamePhase.PLAYING || restored.public.latestClaim == null) {
                    restored = codec.decode(codec.encode(restored))
                    assertEquals(uninterrupted, restored)
                }
                assertTrue(++transitions <= LastLightRules.MAX_HISTORY_ENTRIES)
            }
            assertTrue(codec.encode(restored).size < MAX_SNAPSHOT_PAYLOAD_BYTES)
        }
    }

    @Test
    fun rejectsCorruptedPrivateStateSeedFuseHistoryAndStructurallyPlausibleTurnChanges() {
        val initial = fixture.initial()
        val first = initial.players[0].id
        val second = initial.players[1].id
        val swappedHands = initial.privatePerPlayer.toMutableMap().apply {
            this[first] = initial.privatePerPlayer.getValue(second)
            this[second] = initial.privatePerPlayer.getValue(first)
        }
        val publicChanges = listOf(
            initial.copy(public = initial.public.copy(tableRank = CardRank.CROWN)),
            initial.copy(public = initial.public.copy(turnPlayerId = initial.players[0].id.raw)),
            initial.copy(public = initial.public.copy(acceptedPlaySequence = 1)),
            initial.copy(public = initial.public.copy(disconnectedPlayers = setOf(PlayerId("unknown")))),
            initial.copy(public = initial.public.copy(droppedPlayers = setOf(first))),
        )
        val privateChanges = listOf(
            initial.copy(privatePerPlayer = swappedHands),
            initial.copy(privatePerPlayer = initial.privatePerPlayer - first),
            initial.copy(hostOnly = initial.hostOnly.copy(randomSeed = 72L)),
            initial.copy(hostOnly = initial.hostOnly.copy(openerPlayerId = initial.players[0].id.raw)),
            initial.copy(hostOnly = initial.hostOnly.copy(burnoutSteps = initial.hostOnly.burnoutSteps + (first to 1))),
            initial.copy(hostOnly = initial.hostOnly.copy(discardedCards = listOf(initial.privatePerPlayer.getValue(first).hand.first()))),
            initial.copy(hostOnly = initial.hostOnly.copy(history = listOf(LastLightHistoryEntry(LastLightHistoryEntry.NEXT_ROUND)))),
        )
        (publicChanges + privateChanges).forEach(::assertRejected)
    }

    @Test
    fun acceptedHistoryCannotBeOmittedDuplicatedReorderedOrExtendedAfterTerminal() {
        var state = fixture.initial()
        repeat(4) { state = fixture.accepted(state, fixture.legalAction(state)) }
        assertTrue(state.hostOnly.history.size >= 4)
        assertRejected(state.copy(hostOnly = state.hostOnly.copy(history = state.hostOnly.history.dropLast(1))))
        assertRejected(state.copy(hostOnly = state.hostOnly.copy(history = state.hostOnly.history + state.hostOnly.history.last())))
        assertRejected(state.copy(hostOnly = state.hostOnly.copy(history = state.hostOnly.history.reversed())))
        val ended = fixture.accepted(state, LastLightAction.EndGame)
        assertEquals(ended, codec.decode(codec.encode(ended)))
        assertRejected(ended.copy(hostOnly = ended.hostOnly.copy(history = ended.hostOnly.history + LastLightHistoryEntry(-2))))
    }

    @Test
    fun restoredPendingClaimsRemainChallengeableAndOldRoundCardsCannotReturn() {
        val initial = fixture.initial()
        val play = fixture.legalAction(initial) as LastLightAction.PlayCards
        val played = fixture.accepted(initial, play)
        val restored = codec.decode(codec.encode(played))
        val challenger = PlayerId(assertNotNull(restored.public.turnPlayerId))
        val ended = fixture.accepted(restored, LastLightAction.Challenge(challenger))
        assertEquals(played.hostOnly.pendingCards, assertNotNull(ended.public.roundOutcome).revealedCards)
        val next = fixture.accepted(ended, LastLightAction.NextRound)
        val actor = PlayerId(assertNotNull(next.public.turnPlayerId))
        assertSame(next, fixture.reduce(next, LastLightAction.PlayCards(actor, play.cardIds)))
    }

    @Test
    fun authorityRecoveryRefusesRedactedProjectionsAndMalformedOrUnboundedPayloads() {
        val state = fixture.initial()
        assertRejected(LastLightProjectionPolicy.toPublic(state).state)
        assertRejected(LastLightProjectionPolicy.toPlayer(state, state.players[0].id).state)
        val payload = codec.encode(state).decodeToString()
        val malformed = listOf(
            payload.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2"),
            payload.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":1,\"future\":true"),
            payload.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            " $payload",
            "",
        )
        malformed.forEach { assertFails { codec.decode(it.encodeToByteArray()) } }
        assertFails { codec.decode(byteArrayOf(0xc3.toByte())) }
        assertFailsWith<IllegalArgumentException> { codec.decode(ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES + 1)) }
        val excessiveHistory = state.copy(hostOnly = state.hostOnly.copy(
            history = List(LastLightRules.MAX_HISTORY_ENTRIES + 1) { LastLightHistoryEntry(0, listOf(1)) },
        ))
        assertRejected(excessiveHistory)
    }

    @Test
    fun connectionStateCanNeverRewritePrivateHandsOrSurviveATerminalTransition() {
        val initial = fixture.initial()
        val missing = initial.players[0].id
        val paused = fixture.accepted(initial, LastLightAction.MarkPlayerDisconnected(missing))
        assertEquals(paused, codec.decode(codec.encode(paused)))
        val ended = fixture.accepted(paused, LastLightAction.ContinueWithoutPlayer(missing))
        assertEquals(ended, codec.decode(codec.encode(ended)))
        assertRejected(ended.copy(public = ended.public.copy(disconnectedPlayers = setOf(initial.players[1].id))))
        assertRejected(ended.copy(public = ended.public.copy(endedEarly = false)))
    }

    private fun assertRejected(state: LastLightState) {
        assertFailsWith<IllegalArgumentException> { codec.encode(state) }
        val payload = "{\"schemaVersion\":1,\"state\":" + unsafeJson.encodeToString(LastLightState.serializer(), state) + "}"
        assertFails { codec.decode(payload.encodeToByteArray()) }
    }
}
